package com.risediary.app.ui.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.BackgroundLockMode
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
    private var pendingOnboardingFailure: Throwable? = null
    private class OnboardingWrite(val operation: suspend () -> Unit, var completed: Boolean = false)
    private val onboardingWrites = mutableListOf<OnboardingWrite>()
    private var biometricResetJob: Job? = null

    // --- State holders ---
    private val sharing = SharingStarted.WhileSubscribed(5_000)
    val username = prefs.username.stateIn(viewModelScope, sharing, "机长")
    private val mutablePredictionSettings = MutableStateFlow(PredictionSettingsUiState())
    val predictionSettings = mutablePredictionSettings.asStateFlow()
    private var predictionSettingsJob: Job? = null

    private val mutableLiveUpdatesSettings = MutableStateFlow(LiveUpdatesSettingsUiState())
    val liveUpdatesSettings = mutableLiveUpdatesSettings.asStateFlow()
    private var liveUpdatesSettingsJob: Job? = null

    init { retryPredictionSettings(); retryLiveUpdatesSettings() }

    fun retryLiveUpdatesSettings() {
        liveUpdatesSettingsJob?.cancel()
        mutableLiveUpdatesSettings.value = mutableLiveUpdatesSettings.value.copy(ready = false, error = null)
        liveUpdatesSettingsJob = viewModelScope.launch {
            try {
                prefs.liveUpdatesEnabled.collect {
                    mutableLiveUpdatesSettings.value = LiveUpdatesSettingsUiState(enabled = it, ready = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableLiveUpdatesSettings.value = mutableLiveUpdatesSettings.value.copy(
                    ready = false, error = "读取失败，请重试")
            }
        }
    }

    fun setLiveUpdatesEnabled(enabled: Boolean) = launchSettingsWrite {
        if (mutableLiveUpdatesSettings.value.ready) prefs.setLiveUpdatesEnabled(enabled)
    }

    fun retryPredictionSettings() {
        predictionSettingsJob?.cancel()
        predictionSettingsJob = viewModelScope.launch {
            mutablePredictionSettings.value = PredictionSettingsUiState()
            try {
                prefs.predictionMaxTicks.collect { mutablePredictionSettings.value = PredictionSettingsUiState(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutablePredictionSettings.value = PredictionSettingsUiState(error = "读取失败，请重试") }
        }
    }

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
    fun setUsername(value: String) = launchSettingsWrite { prefs.setUsername(value) }
    fun setPredictionMaxTicks(value: Int) = launchSettingsWrite {
        prefs.setPredictionMaxTicks(value)
        if (predictionSettingsJob?.isActive != true) retryPredictionSettings()
    }.also { job ->
        job.invokeOnCompletion { cause ->
            if (cause is DataMaintenanceBusyException) viewModelScope.launch {
                android.widget.Toast.makeText(context, "数据恢复或清除中，请稍后再保存设置。", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        if (onboardingWrites.any { !it.completed }) runPendingOnboardingWrites().join()
        if (onboardingWrites.any { !it.completed }) {
            throw IllegalStateException("设置未保存，请重试。", pendingOnboardingFailure)
        }
    }
    fun setReminderEnabled(type: ReminderType, enabled: Boolean) = launchSettingsWrite {
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

    fun setReminderTime(type: ReminderType, time: String) = launchSettingsWrite {
        prefs.setReminderTime(type, time)
    }

    fun setInactiveReminderDays(days: Int) = launchSettingsWrite {
        prefs.setInactiveReminderDays(days)
    }

    fun setMonthlyLengthReminderDay(day: Int) = launchSettingsWrite {
        prefs.setMonthlyLengthReminderDay(day)
    }

    fun sendTestNotification(): Boolean = reminderNotifier.postTest()

    fun exactAlarmsAllowed(): Boolean = reminderScheduler.exactAlarmsAllowed()

    fun refreshReminderSchedules() = launchSettingsWrite {
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
            launchSettingsWrite { prefs.setBiometricUnlockEnabled(false) }
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
                    launchSettingsWrite { prefs.setBiometricUnlockEnabled(true) }
                }
            }
        )
    }

    fun setBackgroundAutoLockEnabled(enabled: Boolean) =
        launchSettingsWrite { prefs.setBackgroundAutoLockEnabled(enabled) }

    fun setBackgroundLockMode(mode: BackgroundLockMode) =
        launchSettingsWrite { prefs.setBackgroundLockMode(mode) }

    fun setThemeMode(mode: String) = launchSettingsWrite { prefs.setThemeMode(mode) }

    private fun launchSettingsWrite(block: suspend () -> Unit): Job =
        prefs.maintenanceGate.launchWrite(viewModelScope) {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                android.widget.Toast.makeText(context, "设置未保存，请重试。", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

    private fun queueOnboardingWrite(block: suspend () -> Unit): Job {
        onboardingWrites.add(OnboardingWrite(block))
        return runPendingOnboardingWrites()
    }

    private fun runPendingOnboardingWrites(): Job {
        val previous = pendingOnboardingWrite
        val pending = onboardingWrites.toList()
        val job = launchSettingsWrite {
            previous?.join()
            pendingOnboardingFailure = null
            try {
                pending.filterNot { it.completed }.forEach {
                    it.operation()
                    it.completed = true
                }
            } catch (failure: Exception) { pendingOnboardingFailure = failure; throw failure }
        }
        job.invokeOnCompletion { failure -> if (failure != null) pendingOnboardingFailure = failure }
        pendingOnboardingWrite = job
        return job
    }
}
data class PredictionSettingsUiState(val maxTicks: Int? = null, val error: String? = null)
data class LiveUpdatesSettingsUiState(val enabled: Boolean = true, val ready: Boolean = false, val error: String? = null)
