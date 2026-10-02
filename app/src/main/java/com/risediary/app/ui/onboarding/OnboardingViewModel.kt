package com.risediary.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {
    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    val errorMessage = mutableErrorMessage.asStateFlow()

    fun saveProfile(username: String, onSaved: () -> Unit) = saveSetting(onSaved) {
        preferences.setUsername(username.trim().take(40).ifBlank { "机长" })
    }

    fun saveTheme(themeMode: String, onSaved: () -> Unit) = saveSetting(onSaved) {
        preferences.setThemeMode(themeMode)
    }

    fun saveRecordingPreferences(mode: DefaultVolumeMode, mlPerSpurt: Float, onSaved: () -> Unit) = saveSetting(onSaved) {
        preferences.setDefaultVolumeMode(mode)
        preferences.setMlPerSpurt(mlPerSpurt.coerceIn(1f, 10f))
    }

    private fun saveSetting(onSaved: () -> Unit, action: suspend () -> Unit): Job {
        mutableErrorMessage.value = null
        val job = preferences.maintenanceGate.launchWrite(viewModelScope) {
            try {
                action()
                onSaved()
            } catch (error: DataMaintenanceBusyException) {
                reportSaveFailure(error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportSaveFailure(error)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause is DataMaintenanceBusyException) {
                viewModelScope.launch { reportSaveFailure(cause) }
            }
        }
        return job
    }

    /** Completion is published only after the final, atomic settings write succeeded. */
    suspend fun finish(firstRun: Boolean, reminderTime: String?): Boolean {
        mutableErrorMessage.value = null
        return try {
            preferences.finishOnboarding(firstRun, reminderTime)
            true
        } catch (error: DataMaintenanceBusyException) {
            reportSaveFailure(error)
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportSaveFailure(error)
            false
        }
    }

    internal fun reportSaveFailure(error: Exception) {
        mutableErrorMessage.value = if (error is DataMaintenanceBusyException) {
            "数据恢复或清除中，请稍后重试；当前填写内容已保留"
        } else {
            "设置保存失败，请稍后重试；当前填写内容已保留"
        }
    }
}