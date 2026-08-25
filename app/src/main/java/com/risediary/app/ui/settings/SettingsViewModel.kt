package com.risediary.app.ui.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UserPreferences
import com.risediary.app.reminder.ReminderNotifier
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.reminder.ReminderType
import com.risediary.app.security.BiometricAuthResult
import com.risediary.app.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val reminderNotifier: ReminderNotifier,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private var biometricRequestInProgress = false
    private var pendingOnboardingWrite: Job? = null
    private var biometricResetJob: Job? = null

    // --- State holders ---
    private val sharing = SharingStarted.WhileSubscribed(5_000)
    val username = prefs.username.stateIn(viewModelScope, sharing, "机长")
    val mlPerSpurt = prefs.mlPerSpurt.stateIn(viewModelScope, sharing, 2.0f)
    val defaultVolumeMode =
        prefs.defaultVolumeMode.stateIn(
            viewModelScope,
            sharing,
            DefaultVolumeMode.MILLILITERS
        )

    val dailyReminderEnabled = prefs.dailyReminderEnabled.stateIn(viewModelScope, sharing, false)
    val dailyReminderTime = prefs.dailyReminderTime.stateIn(viewModelScope, sharing, "22:00")

    val inactiveReminderEnabled = prefs.inactiveReminderEnabled.stateIn(viewModelScope, sharing, false)
    val inactiveReminderDays = prefs.inactiveReminderDays.stateIn(viewModelScope, sharing, 7)
    val inactiveReminderTime =
        prefs.inactiveReminderTime.stateIn(viewModelScope, sharing, "22:00")

    val monthlyLengthReminderEnabled = prefs.monthlyLengthReminderEnabled.stateIn(viewModelScope, sharing, false)
    val monthlyLengthReminderDay = prefs.monthlyLengthReminderDay.stateIn(viewModelScope, sharing, 1)
    val monthlyLengthReminderTime =
        prefs.monthlyLengthReminderTime.stateIn(viewModelScope, sharing, "22:00")

    val appLockEnabled = prefs.appLockEnabled.stateIn(viewModelScope, sharing, false)
    val biometricUnlockEnabled =
        prefs.biometricUnlockEnabled.stateIn(viewModelScope, sharing, false)
    val backgroundAutoLockEnabled =
        prefs.backgroundAutoLockEnabled.stateIn(viewModelScope, sharing, false)
    val backgroundLockMode =
        prefs.backgroundLockMode.stateIn(viewModelScope, sharing, BackgroundLockMode.ALWAYS)
    val biometricAvailable: Boolean
        get() = biometricAuthenticator.isAvailable()

    val themeMode = prefs.themeMode.stateIn(viewModelScope, sharing, "system")

    // --- Setters ---
    fun setUsername(value: String) = viewModelScope.launch { prefs.setUsername(value) }
    fun setMlPerSpurt(value: Float) = viewModelScope.launch { prefs.setMlPerSpurt(value) }
    fun setDefaultVolumeMode(mode: DefaultVolumeMode) =
        viewModelScope.launch { prefs.setDefaultVolumeMode(mode) }

    fun applyRecommendedReminders(enabled: Boolean, time: String = "22:00") =
        queueOnboardingWrite {
            prefs.setRecommendedReminders(enabled, time)
            try {
                reminderScheduler.syncAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 设置已保存，后续应用启动或时间变化时会重新同步。
            }
        }

    fun applyOnboardingLockDefaults() = queueOnboardingWrite {
        prefs.setOnboardingLockDefaults()
    }

    suspend fun awaitOnboardingWrites() {
        pendingOnboardingWrite?.join()
    }

    fun setReminderEnabled(type: ReminderType, enabled: Boolean) = viewModelScope.launch {
        when (type) {
            ReminderType.DAILY -> prefs.setDailyReminder(enabled)
            ReminderType.INACTIVE -> prefs.setInactiveReminder(enabled)
            ReminderType.MONTHLY_LENGTH -> prefs.setMonthlyLengthReminder(enabled)
        }
        try {
            reminderScheduler.onReminderEnabledChanged(type, enabled)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 偏好设置已经保存；调度失败时由后续启动/时间变化检查修复。
        }
    }

    fun setReminderTime(type: ReminderType, time: String) = viewModelScope.launch {
        prefs.setReminderTime(type, time)
    }

    fun setInactiveReminderDays(days: Int) = viewModelScope.launch {
        prefs.setInactiveReminderDays(days)
    }

    fun setMonthlyLengthReminderDay(day: Int) = viewModelScope.launch {
        prefs.setMonthlyLengthReminderDay(day)
    }

    fun sendTestNotification(): Boolean = reminderNotifier.postTest()

    fun exactAlarmsAllowed(): Boolean = reminderScheduler.exactAlarmsAllowed()

    fun refreshReminderSchedules() = viewModelScope.launch {
        try {
            reminderScheduler.syncAll()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 保留现有设置，等待下一次系统或应用触发重新核对。
        }
    }

    fun scheduleBackgroundReminderTest(): Boolean = reminderScheduler.scheduleBackgroundTest()

    fun cancelBackgroundReminderTest() = reminderScheduler.cancelBackgroundTest()

    fun requestBiometricUnlock(enabled: Boolean, activity: FragmentActivity?) {
        if (!enabled) {
            viewModelScope.launch { prefs.setBiometricUnlockEnabled(false) }
            return
        }
        if (
            activity == null ||
            !biometricAuthenticator.isAvailable() ||
            biometricRequestInProgress
        ) return
        biometricRequestInProgress = true
        biometricResetJob?.cancel()
        biometricResetJob = viewModelScope.launch {
            delay(15_000)
            biometricRequestInProgress = false
        }
        biometricAuthenticator.authenticate(
            activity = activity,
            title = context.getString(R.string.settings_biometric_enable_title),
            subtitle = context.getString(R.string.settings_biometric_enable_subtitle),
            negativeButtonText = context.getString(R.string.action_cancel),
            onResult = { result ->
                biometricResetJob?.cancel()
                biometricRequestInProgress = false
                if (result == BiometricAuthResult.Success) {
                    viewModelScope.launch { prefs.setBiometricUnlockEnabled(true) }
                }
            }
        )
    }

    fun setBackgroundAutoLockEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setBackgroundAutoLockEnabled(enabled) }

    fun setBackgroundLockMode(mode: BackgroundLockMode) =
        viewModelScope.launch { prefs.setBackgroundLockMode(mode) }

    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }

    private fun queueOnboardingWrite(block: suspend () -> Unit): Job {
        val previous = pendingOnboardingWrite
        return viewModelScope.launch {
            previous?.join()
            block()
        }.also { pendingOnboardingWrite = it }
    }
}
