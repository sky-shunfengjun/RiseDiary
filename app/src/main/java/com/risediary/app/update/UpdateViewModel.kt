package com.risediary.app.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val release: GitHubRelease) : UpdateCheckState
    data object Failed : UpdateCheckState
}

enum class DeveloperError { PASSWORD, SAVE }
data class DeveloperUiState(
    val visible: Boolean = false,
    val busy: Boolean = false,
    val confirmReset: Boolean = false,
    val error: DeveloperError? = null,
)

data class UpdateUiState(
    val developer: DeveloperUiState = DeveloperUiState(),
    val visible: Boolean = false,
    val settingsPage: Boolean = false,
    val settings: UpdateSettings = UpdateSettings(),
    val check: UpdateCheckState = UpdateCheckState.Idle,
    val release: GitHubRelease? = null,
    val download: DownloadState = DownloadState.Idle,
    val confirmCancel: Boolean = false,
    val installUri: String? = null,
    val error: UpdateError? = null,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val checkAuthorization: DownloadAuthorization? = null
) {
    val retryingOriginalVersion: Boolean
        get() = (resolveUpdateDownloadTarget(this) as? UpdateDownloadTarget.Authorized)?.retryOriginal == true
    val downloadEligibilityError: UpdateError?
        get() = if (download.isActive() || download is DownloadState.Ready) null
            else (resolveUpdateDownloadTarget(this) as? UpdateDownloadTarget.Blocked)?.reason
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val releaseSource: ReleaseSource,
    private val preferences: UpdatePreferences,
    private val downloads: UpdateDownloads
) : ViewModel() {
    val currentVersion = BuildConfig.VERSION_NAME
    private val _ui = MutableStateFlow(UpdateUiState())
    val ui = _ui.asStateFlow()
    // Kept during migration of the old entry points; both use this one app-owned VM.
    val state = ui.map { it.check }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateCheckState.Idle)
    private var developerSession = 0L
    private var observedPolicy: UpdateCheckPolicy? = null
    private var checkGeneration = 0L
    private var pendingAutomaticUpdate = false
    private val preferenceMutex = Mutex()
    private var checkJob: Job? = null
    private var installJob: Job? = null
    private val downloadRestored = CompletableDeferred<Unit>()
    private var restoreFailed = false
    private var automaticCheckStarted = false
    private var allowAutomaticPresentation = false
    private var record: DownloadRecord? = null
    private val downloadMutex = Mutex()

    init {
        viewModelScope.launch {
            preferences.settings.catch { _ui.update { it.copy(error = UpdateError.SETTINGS) } }
                .collect { value ->
                    if (observedPolicy != null && value.checkPolicy() != _ui.value.settings.checkPolicy()) invalidateCheckPolicy()
                    observedPolicy = value.checkPolicy()
                    _ui.update { it.copy(settings = value) }
                    if (!value.automaticCheck) pendingAutomaticUpdate = false
                }
        }
        viewModelScope.launch {
            preferences.downloadRecord.distinctUntilChanged().catch {
                restoreFailed = true
                _ui.update { it.copy(error = UpdateError.SETTINGS) }
                downloadRestored.complete(Unit)
            }.collect { value ->
                downloadMutex.withLock {
                    record = value
                    if (value != null && _ui.value.download == DownloadState.Idle) refreshDownloadLocked()
                }
                downloadRestored.complete(Unit)
            }
        }
    }

    fun checkAtStartup() {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            try {
                if (preferences.settings.first().automaticCheck) {
                    allowAutomaticPresentation = !_ui.value.visible
                    checkRelease()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _ui.update { it.copy(check = UpdateCheckState.Failed) } }
        }
    }

    fun openAndCheck() {
        allowAutomaticPresentation = false
        _ui.update { it.copy(visible = true, settingsPage = false, confirmCancel = false, error = null) }
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch { checkRelease() }
    }

    private suspend fun checkRelease() {
        val token = checkGeneration
        try {
            val policy = preferences.settings.first().checkPolicy()
            if (token != checkGeneration) return
            _ui.update { it.copy(check = UpdateCheckState.Checking, checkAuthorization = null, error = null) }
            val release = releaseSource.fetchLatestRelease(policy.channel)
            // Network implementations can finish after cancellation; neither old policy nor old job may commit.
            if (token != checkGeneration || preferences.settings.first().checkPolicy() != policy) return
            val available = isReleaseAvailable(currentVersion, release, policy)
            val asset = selectReleaseApk(release)
            val authorization = asset?.let { authorizeDownload(release, it,
                _ui.value.settings.copy(forceCheck = policy.force, releaseChannel = policy.channel), currentVersion) }
            val automatic = available && allowAutomaticPresentation && _ui.value.settings.automaticCheck
            if (automatic && _ui.value.developer.visible) pendingAutomaticUpdate = true
            _ui.update {
                it.copy(check = if (available) UpdateCheckState.Available(release) else UpdateCheckState.UpToDate,
                    release = release, checkAuthorization = authorization,
                    error = if (available && asset == null) UpdateError.APK_UNAVAILABLE else null,
                    visible = it.visible || (automatic && !it.developer.visible))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (token == checkGeneration) _ui.update { it.copy(check = UpdateCheckState.Failed, release = null, checkAuthorization = null, error = UpdateError.CHECK) }
        } finally { if (token == checkGeneration) allowAutomaticPresentation = false }
    }

    private fun invalidateCheckPolicy() {
        checkGeneration++
        checkJob?.cancel()
        checkJob = null
        allowAutomaticPresentation = false
        pendingAutomaticUpdate = false
        _ui.update { it.copy(check = UpdateCheckState.Idle, release = null, checkAuthorization = null) }
    }
    fun checkForUpdate(force: Boolean = false) { if (force) openAndCheck() else checkAtStartup() }
    fun dismiss() {
        allowAutomaticPresentation = false
        _ui.update { it.copy(visible = false, settingsPage = false, confirmCancel = false) }
    }
    fun showSettings() { _ui.update { it.copy(settingsPage = true) } }
    fun backToUpdate() { _ui.update { it.copy(settingsPage = false) } }
    fun setAutomaticCheck(enabled: Boolean) = savePreference { preferences.setAutomaticCheck(enabled) }
    fun setChannel(channel: UpdateChannel) = savePreference { preferences.setChannel(channel) }
    private fun savePreference(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { preferenceMutex.withLock { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _ui.update { it.copy(error = UpdateError.SETTINGS) } }
        }
    }

    fun openDeveloper() {
        developerSession++
        _ui.update { it.copy(visible = false, settingsPage = false, confirmCancel = false,
            developer = DeveloperUiState(visible = true)) }
    }
    fun dismissDeveloper() {
        developerSession++
        _ui.update { it.copy(developer = DeveloperUiState()) }
    }
    fun onDeveloperDismissFinished() {
        if (!_ui.value.developer.visible && pendingAutomaticUpdate && _ui.value.settings.automaticCheck &&
            _ui.value.check is UpdateCheckState.Available) {
            _ui.update { it.copy(visible = true, settingsPage = false) }
        }
        pendingAutomaticUpdate = false
    }
    fun verifyDeveloperPassword(password: String) {
        if (!_ui.value.developer.visible || _ui.value.developer.busy) return
        if (!isDeveloperPasswordValid(password)) {
            _ui.update { it.copy(developer = it.developer.copy(error = DeveloperError.PASSWORD)) }
            return
        }
        saveDeveloperPreference { preferences.setDeveloperEnabled(true) }
    }
    fun setForceCheck(enabled: Boolean) {
        if (!_ui.value.settings.developerEnabled || _ui.value.developer.busy) return
        invalidateCheckPolicy()
        saveDeveloperPreference { preferences.setForceCheck(enabled) }
    }
    fun setReleaseChannel(channel: ReleaseChannel) {
        if (!_ui.value.settings.developerEnabled || _ui.value.developer.busy) return
        invalidateCheckPolicy()
        saveDeveloperPreference { preferences.setReleaseChannel(channel) }
    }
    private fun saveDeveloperPreference(action: suspend () -> Unit) {
        val session = developerSession
        _ui.update { it.copy(developer = it.developer.copy(busy = true, error = null)) }
        viewModelScope.launch {
            try {
                preferenceMutex.withLock { action() }
                val saved = preferences.settings.first()
                _ui.update { it.copy(settings = saved, developer =
                    if (session == developerSession) it.developer.copy(busy = false) else it.developer) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (session == developerSession) _ui.update { it.copy(developer = it.developer.copy(busy = false,
                error = if (it.developer.visible) DeveloperError.SAVE else null)) } }
        }
    }
    fun requestDeveloperReset() {
        if (_ui.value.settings.developerEnabled && !_ui.value.developer.busy)
            _ui.update { it.copy(developer = it.developer.copy(confirmReset = true)) }
    }
    fun cancelDeveloperReset() { _ui.update { it.copy(developer = it.developer.copy(confirmReset = false)) } }
    fun confirmDeveloperReset() {
        val session = developerSession
        if (!_ui.value.developer.confirmReset || _ui.value.developer.busy) return
        _ui.update { it.copy(developer = it.developer.copy(busy = true, confirmReset = false, error = null)) }
        invalidateCheckPolicy()
        viewModelScope.launch {
            try {
                preferenceMutex.withLock { preferences.restoreDeveloperDefaults() }
                val saved = preferences.settings.first()
                _ui.update { it.copy(settings = saved, developer =
                    if (session == developerSession) DeveloperUiState() else it.developer) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (session == developerSession) _ui.update { it.copy(developer = it.developer.copy(busy = false,
                error = if (it.developer.visible) DeveloperError.SAVE else null)) } }
        }
    }
    fun downloadUpdate() {
        viewModelScope.launch {
            downloadRestored.await()
            if (restoreFailed) { reportActionError(UpdateError.SETTINGS); return@launch }
            downloadMutex.withLock {
                if (_ui.value.download.isActive() || _ui.value.download is DownloadState.Ready) return@withLock
                preferenceMutex.withLock preferenceLock@{
                    val token = checkGeneration
                    val existing = record
                    var enqueued: DownloadRecord? = null
                    try {
                        val liveSettings = preferences.settings.first()
                        if (token != checkGeneration) { reportActionError(UpdateError.RECHECK_REQUIRED); return@preferenceLock }
                        var target = resolveUpdateDownloadTarget(_ui.value.copy(settings = liveSettings))
                        if (target !is UpdateDownloadTarget.Authorized) {
                            reportActionError((target as UpdateDownloadTarget.Blocked).reason ?: UpdateError.RECHECK_REQUIRED)
                            return@preferenceLock
                        }
                        if (existing != null) {
                            refreshDownloadLocked()
                            if (_ui.value.download.isActive() || _ui.value.download is DownloadState.Ready) return@preferenceLock
                        }
                        // A query can suspend; validate both the check generation and current strategy again before enqueue.
                        val latestSettings = preferences.settings.first()
                        target = resolveUpdateDownloadTarget(_ui.value.copy(settings = latestSettings))
                        if (token != checkGeneration || target !is UpdateDownloadTarget.Authorized) {
                            reportActionError((target as? UpdateDownloadTarget.Blocked)?.reason ?: UpdateError.RECHECK_REQUIRED)
                            return@preferenceLock
                        }
                        _ui.update { it.copy(download = DownloadState.Starting, error = null) }
                        enqueued = downloads.enqueue(target.release, target.asset, latestSettings.channel)
                            .copy(authorization = target.authorization)
                        record = enqueued
                        preferences.saveDownload(enqueued)
                        refreshDownloadLocked()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (enqueued != null) {
                            _ui.update { it.copy(error = UpdateError.SETTINGS) }
                            refreshDownloadLocked()
                        } else if (_ui.value.download == DownloadState.Starting) {
                            _ui.update { it.copy(download = DownloadState.Failed(existing, UpdateError.DOWNLOAD)) }
                        } else reportActionError(UpdateError.SETTINGS)
                    }
                }
            }
        }
    }
    fun refreshDownload() { viewModelScope.launch { downloadMutex.withLock { refreshDownloadLocked() } } }
    private suspend fun refreshDownloadLocked() {
        val current = record ?: return
        if (_ui.value.download is DownloadState.Verifying) return
        if (current.verificationFailed) {
            _ui.update { it.copy(download = DownloadState.Failed(current, UpdateError.INTEGRITY)) }
            return
        }
        try {
            val value = downloads.query(current)
            _ui.update { it.copy(download = value, confirmCancel = it.confirmCancel && value.isActive()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _ui.update { it.copy(download = DownloadState.Failed(current, UpdateError.DOWNLOAD), confirmCancel = false) } }
    }

    fun requestCancel() {
        if (_ui.value.download is DownloadState.Running || _ui.value.download is DownloadState.Paused) {
            _ui.update { it.copy(confirmCancel = true) }
        }
    }
    fun keepDownloading() { _ui.update { it.copy(confirmCancel = false) } }
    fun confirmCancel() {
        _ui.update { it.copy(confirmCancel = false) }
        viewModelScope.launch { downloadMutex.withLock {
            val current = record ?: return@withLock
            try {
                when (downloads.cancel(current)) {
                    DownloadCancellation.CANCELLED -> {
                        try {
                            preferences.saveDownload(null)
                            record = null
                            _ui.update { it.copy(download = DownloadState.Idle, error = null) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            _ui.update { it.copy(download = DownloadState.Failed(current, UpdateError.FILE_MISSING),
                                error = UpdateError.SETTINGS) }
                        }
                    }
                    DownloadCancellation.COMPLETED -> refreshDownloadLocked()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _ui.update { it.copy(error = UpdateError.DOWNLOAD) } }
        } }
    }

    fun prepareInstall() {
        if (installJob?.isActive == true || _ui.value.installUri != null) return
        installJob = viewModelScope.launch { downloadMutex.withLock {
            val current = (_ui.value.download as? DownloadState.Ready)?.record ?: return@withLock
            _ui.update { it.copy(download = DownloadState.Verifying(current), error = null) }
            try {
                val uri = downloads.verifyForInstall(current)
                _ui.update { it.copy(download = DownloadState.Ready(current), installUri = uri) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val reason = (error as? UpdateDownloadException)?.reason ?: UpdateError.FILE_MISSING
                val failed = if (reason == UpdateError.INTEGRITY) current.copy(verificationFailed = true) else current
                record = failed
                _ui.update { it.copy(download = DownloadState.Failed(failed, reason)) }
                if (failed.verificationFailed) {
                    try { preferences.saveDownload(failed) }
                    catch (_: java.io.IOException) { _ui.update { it.copy(error = UpdateError.SETTINGS) } }
                }
            }
        } }
    }
    fun onInstallPermissionResult(granted: Boolean) {
        consumeInstallRequest()
        _ui.update { it.copy(visible = true, settingsPage = false) }
        if (!granted) {
            reportActionError(UpdateError.PERMISSION)
            return
        }
        viewModelScope.launch {
            downloadRestored.await()
            if (restoreFailed) { reportActionError(UpdateError.SETTINGS); return@launch }
            downloadMutex.withLock { refreshDownloadLocked() }
            prepareInstall()
        }
    }
    fun consumeInstallRequest() { _ui.update { it.copy(installUri = null) } }
    fun reportActionError(error: UpdateError) { _ui.update { it.copy(error = error) } }
}
