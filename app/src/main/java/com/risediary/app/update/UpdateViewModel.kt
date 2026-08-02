package com.risediary.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val release: GitHubRelease) : UpdateCheckState
    data object Failed : UpdateCheckState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val releaseChecker: GitHubReleaseChecker
) : ViewModel() {

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var automaticCheckStarted = false

    fun checkForUpdate(force: Boolean = false) {
        if (checkJob?.isActive == true) return
        if (!force && automaticCheckStarted) return
        if (!force) automaticCheckStarted = true

        checkJob = viewModelScope.launch {
            _state.value = UpdateCheckState.Checking
            try {
                val latest = releaseChecker.fetchLatestRelease()
                _state.value = if (
                    latest != null && isNewerVersion(currentVersion, latest.tagName)
                ) {
                    UpdateCheckState.Available(latest)
                } else {
                    UpdateCheckState.UpToDate
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = UpdateCheckState.Failed
            }
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateCheckState.Checking) {
            _state.value = UpdateCheckState.Idle
        }
    }
}
