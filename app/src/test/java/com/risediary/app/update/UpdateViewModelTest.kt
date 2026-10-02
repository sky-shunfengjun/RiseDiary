package com.risediary.app.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun disabledAutomaticChecksNeverCallGithubButManualChecksStillWork() = runTest(dispatcher) {
        val preferences = FakeUpdatePreferences(UpdateSettings(automaticCheck = false))
        var calls = 0
        val vm = UpdateViewModel(ReleaseSource { calls++; release() }, preferences, FakeDownloads())
        vm.checkAtStartup()
        runCurrent()
        assertEquals(0, calls)
        assertFalse(vm.ui.value.visible)
        vm.openAndCheck()
        runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.ui.value.visible)
    }

    @Test fun automaticCheckRunsOnceAndDismissalDoesNotReopenOnReturn() = runTest(dispatcher) {
        var calls = 0
        val vm = UpdateViewModel(ReleaseSource { calls++; release() }, FakeUpdatePreferences(), FakeDownloads())
        vm.checkAtStartup()
        runCurrent()
        assertTrue(vm.ui.value.visible)
        vm.dismiss()
        vm.checkAtStartup()
        runCurrent()
        assertEquals(1, calls)
        assertFalse(vm.ui.value.visible)
    }

    @Test fun closingDuringManualCheckSuppressesTheLateResult() = runTest(dispatcher) {
        val response = CompletableDeferred<GitHubRelease>()
        val vm = UpdateViewModel(ReleaseSource { response.await() }, FakeUpdatePreferences(), FakeDownloads())
        vm.openAndCheck()
        runCurrent()
        vm.dismiss()
        response.complete(release())
        runCurrent()
        assertFalse(vm.ui.value.visible)
        assertTrue(vm.ui.value.check is UpdateCheckState.Available)
    }

    @Test fun olderOfficialReleaseKeepsItsChangelogWithoutOfferingADowngrade() = runTest(dispatcher) {
        val vm = UpdateViewModel(ReleaseSource { release("v1.0.0") }, FakeUpdatePreferences(), FakeDownloads())
        vm.openAndCheck()
        runCurrent()
        assertTrue(vm.ui.value.check is UpdateCheckState.UpToDate)
        assertEquals("notes", vm.ui.value.release?.body)
    }

    @Test fun checkingFailureIsVisibleOnlyForManualChecks() = runTest(dispatcher) {
        val vm = UpdateViewModel(ReleaseSource { error("offline") }, FakeUpdatePreferences(), FakeDownloads())
        vm.checkAtStartup()
        runCurrent()
        assertFalse(vm.ui.value.visible)
        vm.openAndCheck()
        runCurrent()
        assertTrue(vm.ui.value.visible)
        assertTrue(vm.ui.value.check is UpdateCheckState.Failed)
    }

    @Test fun repeatedDownloadClicksReuseTheTaskAndCancellationChecksForCompletion() = runTest(dispatcher) {
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(), downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        vm.downloadUpdate()
        runCurrent()
        assertEquals(1, downloads.tasks.size)
        assertTrue(vm.ui.value.download is DownloadState.Running)
        vm.requestCancel()
        assertTrue(vm.ui.value.confirmCancel)
        downloads.completed = true
        vm.confirmCancel()
        runCurrent()
        assertTrue(downloads.cancelled.isEmpty())
        assertTrue(vm.ui.value.download is DownloadState.Ready)
    }

    @Test fun persistedTaskIsRecoveredWithoutEnqueuingAnotherDownload() = runTest(dispatcher) {
        val task = DownloadRecord(7, release(), release().assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { tasks.add(task) }
        val prefs = FakeUpdatePreferences(record = task)
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        runCurrent()
        vm.openAndCheck()
        vm.refreshDownload()
        runCurrent()
        assertTrue(vm.ui.value.download is DownloadState.Running)
        assertEquals(1, downloads.tasks.size)
    }

    @Test fun integrityFailureSurvivesRefreshAndCanBeDownloadedAgain() = runTest(dispatcher) {
        val downloads = FakeDownloads().apply { completed = true; verificationError = UpdateError.INTEGRITY }
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(), downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        vm.prepareInstall()
        runCurrent()
        assertEquals(UpdateError.INTEGRITY, (vm.ui.value.download as DownloadState.Failed).reason)
        vm.refreshDownload()
        runCurrent()
        assertTrue(vm.ui.value.download is DownloadState.Failed)
        vm.downloadUpdate()
        runCurrent()
        assertEquals(2, downloads.tasks.size)
    }
    @Test fun failedCheckDoesNotReplaceAnActiveDownload() = runTest(dispatcher) {
        var offline = false
        val vm = UpdateViewModel(ReleaseSource { if (offline) error("offline") else release() }, FakeUpdatePreferences(), FakeDownloads())
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        offline = true
        vm.openAndCheck()
        runCurrent()
        assertEquals(UpdateCheckState.Failed, vm.ui.value.check)
        assertTrue(vm.ui.value.download is DownloadState.Running)
        assertEquals(UpdatePrimaryAction.CANCEL, primaryUpdateAction(vm.ui.value))
    }

    @Test fun permissionDenialDoesNotLoopAndGrantRechecksTheFile() = runTest(dispatcher) {
        val downloads = FakeDownloads().apply { completed = true }
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(), downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        vm.prepareInstall()
        runCurrent()
        assertNotNull(vm.ui.value.installUri)
        assertEquals(1, downloads.verifications)
        vm.onInstallPermissionResult(false)
        runCurrent()
        assertNull(vm.ui.value.installUri)
        assertEquals(UpdateError.PERMISSION, vm.ui.value.error)
        assertEquals(1, downloads.verifications)
        vm.prepareInstall()
        runCurrent()
        vm.onInstallPermissionResult(true)
        runCurrent()
        assertEquals(3, downloads.verifications)
        assertNotNull(vm.ui.value.installUri)
        assertTrue(vm.ui.value.visible)
    }

    @Test fun cancellationRemovesOnlyItsTaskAndAllowsANewChannelOnRetry() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences()
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        vm.setChannel(UpdateChannel.PROXY_7ED)
        runCurrent()
        assertEquals(UpdateChannel.OFFICIAL, downloads.tasks.single().channel)
        vm.requestCancel()
        vm.confirmCancel()
        runCurrent()
        assertEquals(listOf(7L), downloads.cancelled)
        assertNull(prefs.downloadRecord.value)
        assertEquals(DownloadState.Idle, vm.ui.value.download)
        vm.downloadUpdate()
        runCurrent()
        assertEquals(UpdateChannel.PROXY_7ED, downloads.tasks.last().channel)
    }

    @Test fun completedFileMissingOffersRetryEvenAfterACheckFailure() = runTest(dispatcher) {
        val task = DownloadRecord(7, release(), release().assets.single(), UpdateChannel.OFFICIAL,
            authorization = authorizeDownload(release(), release().assets.single(), UpdateSettings(), "v1.1.2"))
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { error("offline") }, FakeUpdatePreferences(record = task), downloads)
        runCurrent()
        vm.openAndCheck()
        runCurrent()
        assertEquals(UpdatePrimaryAction.DOWNLOAD, primaryUpdateAction(vm.ui.value))
        vm.downloadUpdate()
        runCurrent()
        assertEquals(1, downloads.tasks.size)
    }

    @Test fun recoveredIntegrityFailureIsNeverChangedBackToReady() = runTest(dispatcher) {
        val task = DownloadRecord(7, release(), release().assets.single(), UpdateChannel.OFFICIAL, verificationFailed = true)
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(record = task), FakeDownloads().apply { completed = true })
        runCurrent()
        vm.refreshDownload()
        runCurrent()
        assertEquals(UpdateError.INTEGRITY, (vm.ui.value.download as DownloadState.Failed).reason)
        assertNull(vm.ui.value.installUri)
    }

    @Test fun tappingDownloadWaitsForPersistedTaskBeforeEnqueuing() = runTest(dispatcher) {
        val stored = kotlinx.coroutines.flow.MutableSharedFlow<DownloadRecord?>(replay = 1)
        val prefs = object : UpdatePreferences {
            override val settings = MutableStateFlow(UpdateSettings())
            override val downloadRecord = stored
            override suspend fun setAutomaticCheck(enabled: Boolean) = Unit
            override suspend fun setChannel(channel: UpdateChannel) = Unit
            override suspend fun setForceCheck(enabled: Boolean) = Unit
            override suspend fun setReleaseChannel(channel: ReleaseChannel) = Unit
            override suspend fun setDeveloperEnabled(enabled: Boolean) = Unit
            override suspend fun restoreDeveloperDefaults() = Unit
            override suspend fun saveDownload(record: DownloadRecord?) { stored.emit(record) }
        }
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        assertTrue(downloads.tasks.isEmpty())
        stored.emit(DownloadRecord(70, release(), release().assets.single(), UpdateChannel.OFFICIAL))
        runCurrent()
        assertTrue(vm.ui.value.download is DownloadState.Running)
        assertTrue(downloads.tasks.isEmpty())
    }

    @Test fun repeatedInstallTapsCreateOnlyOneInstallationRequest() = runTest(dispatcher) {
        val downloads = FakeDownloads().apply { completed = true }
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(), downloads)
        vm.openAndCheck()
        runCurrent()
        vm.downloadUpdate()
        runCurrent()
        vm.prepareInstall()
        vm.prepareInstall()
        runCurrent()
        assertEquals(1, downloads.verifications)
    }
    @Test fun permissionReturnAfterProcessRecreationWaitsForRestoredDownload() = runTest(dispatcher) {
        val stored = kotlinx.coroutines.flow.MutableSharedFlow<DownloadRecord?>(replay = 1)
        val prefs = object : UpdatePreferences {
            override val settings = MutableStateFlow(UpdateSettings(automaticCheck = false))
            override val downloadRecord = stored
            override suspend fun setAutomaticCheck(enabled: Boolean) = Unit
            override suspend fun setChannel(channel: UpdateChannel) = Unit
            override suspend fun setForceCheck(enabled: Boolean) = Unit
            override suspend fun setReleaseChannel(channel: ReleaseChannel) = Unit
            override suspend fun setDeveloperEnabled(enabled: Boolean) = Unit
            override suspend fun restoreDeveloperDefaults() = Unit
            override suspend fun saveDownload(record: DownloadRecord?) { stored.emit(record) }
        }
        val downloads = FakeDownloads().apply { completed = true }
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.onInstallPermissionResult(true)
        runCurrent()
        assertNull(vm.ui.value.installUri)
        stored.emit(DownloadRecord(70, release(), release().assets.single(), UpdateChannel.OFFICIAL))
        runCurrent()
        assertNotNull(vm.ui.value.installUri)
        assertTrue(vm.ui.value.visible)
        assertEquals(1, downloads.verifications)
    }
    @Test fun forceMakesOlderReleaseDownloadableForManualAndAutomaticChecks() = runTest(dispatcher) {
        for (automatic in listOf(false, true)) {
            val downloads = FakeDownloads()
            val vm = UpdateViewModel(ReleaseSource { release("v1.0.0") },
                FakeUpdatePreferences(UpdateSettings(forceCheck = true)), downloads)
            if (automatic) vm.checkAtStartup() else vm.openAndCheck()
            runCurrent()
            assertTrue(vm.ui.value.visible)
            assertTrue(vm.ui.value.check is UpdateCheckState.Available)
            vm.downloadUpdate()
            runCurrent()
            assertEquals(1, downloads.tasks.size)
        }
    }
    @Test fun forceDoesNotOverrideTheAutomaticMasterSwitchOrCreateAnApk() = runTest(dispatcher) {
        var calls = 0
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { calls++; release("v1.0.0").copy(assets = emptyList()) },
            FakeUpdatePreferences(UpdateSettings(automaticCheck = false, forceCheck = true)), downloads)
        vm.checkAtStartup(); runCurrent()
        assertEquals(0, calls)
        vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.CHECK, primaryUpdateAction(vm.ui.value))
        vm.downloadUpdate(); runCurrent()
        assertTrue(downloads.tasks.isEmpty())
    }
    @Test fun switchingPolicyDiscardsEvenANonCooperativeLateResponseAndPreservesDownloads() = runTest(dispatcher) {
        val late = CompletableDeferred<GitHubRelease>()
        var calls = 0
        val seen = mutableListOf<ReleaseChannel>()
        val task = DownloadRecord(7, release(), release().assets.single(), UpdateChannel.OFFICIAL)
        val prefs = FakeUpdatePreferences(UpdateSettings(developerEnabled = true), task)
        val vm = UpdateViewModel(ReleaseSource { channel ->
            seen.add(channel)
            if (++calls == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { late.await() }
            else release("v1.0.0-beta1")
        }, prefs, FakeDownloads())
        vm.openAndCheck(); runCurrent()
        vm.setReleaseChannel(ReleaseChannel.PREVIEW); runCurrent()
        assertEquals(UpdateCheckState.Idle, vm.ui.value.check)
        vm.openAndCheck(); runCurrent()
        assertEquals("v1.0.0-beta1", vm.ui.value.release?.tagName)
        late.complete(release("v99.0.0")); runCurrent()
        assertEquals("v1.0.0-beta1", vm.ui.value.release?.tagName)
        assertEquals(listOf(ReleaseChannel.STABLE, ReleaseChannel.PREVIEW), seen)
        assertTrue(vm.ui.value.download is DownloadState.Running)
        assertEquals(task, prefs.downloadRecord.value)
    }
    @Test fun developerAuthenticationPersistsAndResetOnlyChangesDeveloperSettings() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences(UpdateSettings(automaticCheck = false, channel = UpdateChannel.PROXY_7ED))
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, FakeDownloads())
        vm.openDeveloper(); vm.verifyDeveloperPassword("wrong"); runCurrent()
        assertFalse(prefs.settings.value.developerEnabled)
        assertEquals(DeveloperError.PASSWORD, vm.ui.value.developer.error)
        vm.verifyDeveloperPassword("1095102874"); runCurrent()
        assertTrue(prefs.settings.value.developerEnabled)
        vm.setForceCheck(true); runCurrent(); vm.setReleaseChannel(ReleaseChannel.PREVIEW); runCurrent()
        vm.requestDeveloperReset(); vm.cancelDeveloperReset()
        assertTrue(prefs.settings.value.forceCheck)
        vm.requestDeveloperReset(); vm.confirmDeveloperReset(); runCurrent()
        assertFalse(vm.ui.value.developer.visible)
        assertFalse(prefs.settings.value.developerEnabled)
        assertFalse(prefs.settings.value.forceCheck)
        assertEquals(ReleaseChannel.STABLE, prefs.settings.value.releaseChannel)
        assertFalse(prefs.settings.value.automaticCheck)
        assertEquals(UpdateChannel.PROXY_7ED, prefs.settings.value.channel)
    }
    @Test fun authenticationAndResetFailuresKeepTheMenuForRetry() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences().apply { failDeveloperSave = true }
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, FakeDownloads())
        vm.openDeveloper(); vm.verifyDeveloperPassword("1095102874"); runCurrent()
        assertTrue(vm.ui.value.developer.visible)
        assertFalse(prefs.settings.value.developerEnabled)
        assertEquals(DeveloperError.SAVE, vm.ui.value.developer.error)
        prefs.failDeveloperSave = false
        vm.verifyDeveloperPassword("1095102874"); runCurrent()
        prefs.failDeveloperSave = true
        vm.requestDeveloperReset(); vm.confirmDeveloperReset(); runCurrent()
        assertTrue(vm.ui.value.developer.visible)
        assertTrue(prefs.settings.value.developerEnabled)
        assertEquals(DeveloperError.SAVE, vm.ui.value.developer.error)
        vm.dismissDeveloper()
        assertNull(vm.ui.value.developer.error)
    }
    @Test fun enabledDeveloperAccessSurvivesRecreationAndAutomaticPromptWaitsForExit() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences(UpdateSettings(developerEnabled = true))
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, FakeDownloads())
        runCurrent()
        vm.openDeveloper(); vm.checkAtStartup(); runCurrent()
        assertFalse(vm.ui.value.visible)
        assertTrue(vm.ui.value.developer.visible)
        vm.dismissDeveloper()
        assertFalse(vm.ui.value.visible)
        vm.onDeveloperDismissFinished()
        assertTrue(vm.ui.value.visible)
        val recreated = UpdateViewModel(ReleaseSource { release() }, prefs, FakeDownloads())
        runCurrent()
        assertTrue(recreated.ui.value.settings.developerEnabled)
    }
    @Test fun lateSaveFailureCannotLeakIntoAReopenedDeveloperSheet() = runTest(dispatcher) {
        for (reset in listOf(false, true)) {
            val barrier = CompletableDeferred<Unit>()
            val prefs = FakeUpdatePreferences(UpdateSettings(developerEnabled = reset)).apply {
                failDeveloperSave = true; developerSaveBarrier = barrier
            }
            val vm = UpdateViewModel(ReleaseSource { release() }, prefs, FakeDownloads())
            runCurrent()
            vm.openDeveloper()
            if (reset) { vm.requestDeveloperReset(); vm.confirmDeveloperReset() }
            else vm.verifyDeveloperPassword("1095102874")
            runCurrent()
            assertTrue(vm.ui.value.developer.busy)
            vm.dismissDeveloper(); vm.openDeveloper()
            barrier.complete(Unit); runCurrent()
            assertNull(vm.ui.value.developer.error)
            assertFalse(vm.ui.value.developer.busy)
        }
    }

    @Test fun settingsReadFailureDuringDownloadIsReportedWithoutEnqueuing() = runTest(dispatcher) {
        val base = FakeUpdatePreferences()
        var failRead = false
        val prefs = object : UpdatePreferences by base {
            override val settings = base.settings.map {
                if (failRead) throw java.io.IOException("read failed")
                it
            }
        }
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.openAndCheck(); runCurrent()
        assertTrue(vm.ui.value.check is UpdateCheckState.Available)
        failRead = true
        vm.downloadUpdate(); runCurrent()
        assertEquals(UpdateError.SETTINGS, vm.ui.value.error)
        assertTrue(downloads.tasks.isEmpty())
    }

    @Test fun failedOldDownloadCannotOfferDownloadForANewerReleaseWithoutApk() = runTest(dispatcher) {
        val old = release("v98.0.0")
        val task = DownloadRecord(7, old, old.assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { release().copy(assets = emptyList()) },
            FakeUpdatePreferences(record = task), downloads)
        runCurrent()
        vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.CHECK, primaryUpdateAction(vm.ui.value))
        vm.downloadUpdate(); runCurrent()
        assertTrue(downloads.tasks.isEmpty())
    }

    @Test fun failedOldDownloadCannotBypassUpToDateCheck() = runTest(dispatcher) {
        val old = release("v1.0.0")
        val task = DownloadRecord(7, old, old.assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { old }, FakeUpdatePreferences(record = task), downloads)
        runCurrent()
        vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.CHECK, primaryUpdateAction(vm.ui.value))
        vm.downloadUpdate(); runCurrent()
        assertTrue(downloads.tasks.isEmpty())
    }
    @Test fun successfulNewCheckRetriesTheNewReleaseRatherThanOldTask() = runTest(dispatcher) {
        val old = release("v98.0.0")
        val task = DownloadRecord(7, old, old.assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { release() }, FakeUpdatePreferences(record = task), downloads)
        runCurrent(); vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.DOWNLOAD, primaryUpdateAction(vm.ui.value))
        vm.downloadUpdate(); runCurrent()
        assertEquals("v99.0.0", downloads.tasks.single().release.tagName)
    }

    @Test fun legacyFailedTaskNeedsNewCheckBeforeOfflineRetry() = runTest(dispatcher) {
        val task = DownloadRecord(7, release(), release().assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { error("offline") }, FakeUpdatePreferences(record = task), downloads)
        runCurrent(); vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.CHECK, primaryUpdateAction(vm.ui.value))
        assertEquals(UpdateError.RECHECK_REQUIRED, vm.ui.value.downloadEligibilityError)
        vm.downloadUpdate(); runCurrent()
        assertTrue(downloads.tasks.isEmpty())
    }

    @Test fun unchangedAuthorizedTaskHasExplicitOriginalVersionOfflineRetry() = runTest(dispatcher) {
        val release = release()
        val task = DownloadRecord(7, release, release.assets.single(), UpdateChannel.OFFICIAL,
            authorization = authorizeDownload(release, release.assets.single(), UpdateSettings(), "v1.1.2"))
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val vm = UpdateViewModel(ReleaseSource { error("offline") }, FakeUpdatePreferences(record = task), downloads)
        runCurrent(); vm.openAndCheck(); runCurrent()
        assertEquals(UpdatePrimaryAction.DOWNLOAD, primaryUpdateAction(vm.ui.value))
        assertTrue(vm.ui.value.retryingOriginalVersion)
        vm.downloadUpdate(); runCurrent()
        assertEquals(release, downloads.tasks.single().release)
    }

    @Test fun revokedForceOrPreviewAuthorizationCannotRetryOldTarget() = runTest(dispatcher) {
        for (preview in listOf(false, true)) {
            val oldSettings = UpdateSettings(forceCheck = true,
                releaseChannel = if (preview) ReleaseChannel.PREVIEW else ReleaseChannel.STABLE)
            val old = release("v1.0.0").copy(prerelease = preview)
            val task = DownloadRecord(7, old, old.assets.single(), UpdateChannel.OFFICIAL,
                authorization = authorizeDownload(old, old.assets.single(), oldSettings, "v1.1.2"))
            val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
            val vm = UpdateViewModel(ReleaseSource { error("offline") }, FakeUpdatePreferences(record = task), downloads)
            runCurrent(); vm.openAndCheck(); runCurrent()
            assertEquals(UpdatePrimaryAction.CHECK, primaryUpdateAction(vm.ui.value))
            vm.downloadUpdate(); runCurrent()
            assertTrue(downloads.tasks.isEmpty())
        }
    }

    @Test fun policyChangeDuringTaskQueryIsCheckedAgainBeforeEnqueue() = runTest(dispatcher) {
        val old = release("v98.0.0")
        val task = DownloadRecord(7, old, old.assets.single(), UpdateChannel.OFFICIAL)
        val downloads = FakeDownloads().apply { queryError = UpdateError.FILE_MISSING }
        val prefs = FakeUpdatePreferences(record = task)
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        runCurrent(); vm.openAndCheck(); runCurrent()
        val barrier = CompletableDeferred<Unit>()
        downloads.queryBarrier = barrier
        vm.downloadUpdate(); runCurrent()
        prefs.settings.value = prefs.settings.value.copy(forceCheck = true)
        runCurrent(); barrier.complete(Unit); runCurrent()
        assertTrue(downloads.tasks.isEmpty())
        assertEquals(UpdateError.RECHECK_REQUIRED, vm.ui.value.error)
    }

    @Test fun completionBetweenCancellationQueryAndRemoveFollowsCancellation() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences()
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.openAndCheck(); runCurrent(); vm.downloadUpdate(); runCurrent()
        downloads.completeAfterCancellationQuery = true
        vm.requestCancel(); vm.confirmCancel(); runCurrent()
        assertEquals(listOf(7L), downloads.cancelled)
        assertEquals(DownloadState.Idle, vm.ui.value.download)
        assertNull(prefs.downloadRecord.value)
        vm.refreshDownload(); runCurrent()
        assertEquals(DownloadState.Idle, vm.ui.value.download)
    }

    @Test fun cancellationRemoveFailurePreservesRecordAndAllowsRetry() = runTest(dispatcher) {
        val prefs = FakeUpdatePreferences()
        val downloads = FakeDownloads()
        val vm = UpdateViewModel(ReleaseSource { release() }, prefs, downloads)
        vm.openAndCheck(); runCurrent(); vm.downloadUpdate(); runCurrent()
        val stored = prefs.downloadRecord.value
        downloads.cancellationRemoveError = true
        vm.requestCancel(); vm.confirmCancel(); runCurrent()
        assertEquals(stored, prefs.downloadRecord.value)
        assertTrue(vm.ui.value.download is DownloadState.Running)
        assertEquals(UpdateError.DOWNLOAD, vm.ui.value.error)
        downloads.cancellationRemoveError = false
        vm.requestCancel(); vm.confirmCancel(); runCurrent()
        assertNull(prefs.downloadRecord.value)
    }
    private fun release(tag: String = "v99.0.0") = GitHubRelease(tag, tag, RELEASES_URL, "notes",
        listOf(GitHubAsset(5, "RiseDiary-$tag.apk", "https://github.com/sky-shunfengjun/RiseDiary/releases/download/$tag/RiseDiary-$tag.apk", 1024)))
}

private class FakeUpdatePreferences(
    settings: UpdateSettings = UpdateSettings(), record: DownloadRecord? = null
) : UpdatePreferences {
    override val settings = MutableStateFlow(settings)
    override val downloadRecord = MutableStateFlow(record)
    override suspend fun setAutomaticCheck(enabled: Boolean) { settings.value = settings.value.copy(automaticCheck = enabled) }
    override suspend fun setChannel(channel: UpdateChannel) { settings.value = settings.value.copy(channel = channel) }
    var failDeveloperSave = false
    var developerSaveBarrier: CompletableDeferred<Unit>? = null
    override suspend fun setForceCheck(enabled: Boolean) { settings.value = settings.value.copy(forceCheck = enabled) }
    override suspend fun setReleaseChannel(channel: ReleaseChannel) { settings.value = settings.value.copy(releaseChannel = channel) }
    override suspend fun setDeveloperEnabled(enabled: Boolean) {
        developerSaveBarrier?.await()
        if (failDeveloperSave) throw java.io.IOException("save failed")
        settings.value = settings.value.copy(developerEnabled = enabled)
    }
    override suspend fun restoreDeveloperDefaults() {
        developerSaveBarrier?.await()
        if (failDeveloperSave) throw java.io.IOException("save failed")
        settings.value = settings.value.copy(forceCheck = false, releaseChannel = ReleaseChannel.STABLE, developerEnabled = false)
    }
    override suspend fun saveDownload(record: DownloadRecord?) { downloadRecord.value = record }
}

private class FakeDownloads : UpdateDownloads {
    val tasks = mutableListOf<DownloadRecord>()
    val cancelled = mutableListOf<Long>()
    var completed = false
    var verificationError: UpdateError? = null
    var queryError: UpdateError? = null
    var verifications = 0
    override suspend fun enqueue(release: GitHubRelease, asset: GitHubAsset, channel: UpdateChannel): DownloadRecord =
        DownloadRecord(7, release, asset, channel).also(tasks::add)
    var queryBarrier: CompletableDeferred<Unit>? = null
    override suspend fun query(record: DownloadRecord): DownloadState {
        queryBarrier?.await()
        return if (queryError != null) DownloadState.Failed(record, queryError!!) else if (completed) DownloadState.Ready(record) else DownloadState.Running(record, 35)
    }
    var completeAfterCancellationQuery = false
    var cancellationRemoveError = false
    override suspend fun cancel(record: DownloadRecord): DownloadCancellation = cancelDownloadWithPolicy(
        query = {
            val result = if (completed) CancellationQueryState.COMPLETED else CancellationQueryState.PRESENT
            if (completeAfterCancellationQuery) completed = true
            result
        },
        remove = {
            if (cancellationRemoveError) throw java.io.IOException("remove failed")
            cancelled.add(record.id)
            1
        }
    )
    override suspend fun verifyForInstall(record: DownloadRecord): String {
        verifications++
        verificationError?.let { throw UpdateDownloadException(it) }
        return "content://downloads/7"
    }
}
