package com.risediary.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferences
) : ViewModel() {
    fun saveProfile(username: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            preferences.setUsername(username.trim().take(40).ifBlank { "机长" })
            onSaved()
        }
    }

    fun saveTheme(themeMode: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            preferences.setThemeMode(themeMode)
            onSaved()
        }
    }

    fun saveRecordingPreferences(
        mode: DefaultVolumeMode,
        mlPerSpurt: Float,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            preferences.setDefaultVolumeMode(mode)
            preferences.setMlPerSpurt(mlPerSpurt.coerceIn(1f, 10f))
            onSaved()
        }
    }

    suspend fun finish(
        firstRun: Boolean,
        reminderTime: String?
    ) {
        preferences.finishOnboarding(firstRun, reminderTime)
    }
}
