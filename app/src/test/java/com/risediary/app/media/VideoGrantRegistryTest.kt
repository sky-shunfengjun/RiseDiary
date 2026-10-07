package com.risediary.app.media

import com.risediary.app.data.DataMaintenanceGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoGrantRegistryTest {
    private class Files : VideoFileAccess {
        val grants = mutableSetOf("shared", "draft", "undo", "unused")
        var cleanupCalls = 0
        override suspend fun acquire(uriString: String, flags: Int) =
            Result.success(LocalVideoRef(uriString, "video.mp4"))
        override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
        override suspend fun releaseUnused(referencedUris: Set<String>) {
            cleanupCalls++
            grants.retainAll(referencedUris)
        }
    }

    @Test fun cleanupRetainsSharedRecordOpenDraftAndPendingUndo() = runTest {
        val files = Files()
        val records = mutableSetOf("shared")
        val registry = VideoGrantRegistry({ records.toSet() }, files, DataMaintenanceGate(), backgroundScope)
        registry.retain("form", setOf("draft"))
        registry.retain("pending-delete", setOf("undo"))
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("shared", "draft", "undo"), files.grants)
        registry.forget("pending-delete")
        runCurrent()
        assertEquals(setOf("shared", "draft"), files.grants)
        registry.forget("form")
        runCurrent()
        assertEquals(setOf("shared"), files.grants)
    }

    @Test fun removingOneSharedRecordKeepsPermissionUntilLastRecordIsGone() = runTest {
        val files = Files()
        var records = listOf("shared", "shared")
        val registry = VideoGrantRegistry({ records.toSet() }, files, DataMaintenanceGate(), backgroundScope)
        records = records.drop(1)
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("shared"), files.grants)
        records = emptyList()
        registry.requestCleanup()
        runCurrent()
        assertEquals(emptySet<String>(), files.grants)
    }

    @Test fun cleanupWaitsForMaintenanceRollbackBeforeReadingReferences() = runTest {
        val files = Files()
        var records = setOf("shared")
        val gate = DataMaintenanceGate()
        val registry = VideoGrantRegistry({ records }, files, gate, backgroundScope)
        runCurrent()
        val before = files.cleanupCalls
        gate.maintenance {
            records = emptySet()
            registry.requestCleanup()
            runCurrent()
            assertEquals(before, files.cleanupCalls)
            assertEquals(setOf("shared"), files.grants)
            records = setOf("shared")
        }
        runCurrent()
        assertEquals(setOf("shared"), files.grants)
    }

    @Test fun cleanupCannotRevokeAFileWhileSelectionIsStillBeingAcquired() = runTest {
        val files = Files().apply { grants.clear(); grants += "draft" }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val access = object : VideoFileAccess by files {
            override suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef> {
                files.grants += uriString
                entered.complete(Unit)
                release.await()
                return Result.success(LocalVideoRef(uriString, "new.mp4"))
            }
        }
        val registry = VideoGrantRegistry({ emptySet() }, access, DataMaintenanceGate(), backgroundScope)
        registry.retain("form", setOf("draft"))
        val acquiring = async { registry.acquire("form", "new", 1, setOf("draft")) }
        entered.await()
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("draft", "new"), files.grants)
        release.complete(Unit)
        acquiring.await().getOrThrow()
        runCurrent()
        assertEquals(setOf("draft", "new"), files.grants)
        registry.forget("form")
        runCurrent()
        assertEquals(emptySet<String>(), files.grants)
    }

    @Test fun failedSelectionKeepsThePreviousDraftVideo() = runTest {
        val files = Files()
        val access = object : VideoFileAccess by files {
            override suspend fun acquire(uriString: String, flags: Int) =
                Result.failure<LocalVideoRef>(java.io.IOException("provider rejected permission"))
        }
        val registry = VideoGrantRegistry({ emptySet() }, access, DataMaintenanceGate(), backgroundScope)
        registry.retain("form", setOf("draft"))
        org.junit.Assert.assertTrue(registry.acquire("form", "new", 1, setOf("draft")).isFailure)
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("draft"), files.grants)
    }

    @Test fun recoveryRequiredKeepsPermissionsUntilRecoverySucceeds() = runTest {
        val files = Files()
        val gate = DataMaintenanceGate().apply { requireRecovery() }
        val registry = VideoGrantRegistry({ setOf("shared") }, files, gate, backgroundScope)
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("shared", "draft", "undo", "unused"), files.grants)
        gate.maintenance(recovery = true) { }
        runCurrent()
        assertEquals(setOf("shared"), files.grants)
    }

    @Test fun failedDatabaseReadDoesNotReleaseAnyGrant() = runTest {
        val files = Files()
        val registry = VideoGrantRegistry({ error("database unavailable") }, files, DataMaintenanceGate(), backgroundScope)
        registry.requestCleanup()
        runCurrent()
        assertEquals(setOf("shared", "draft", "undo", "unused"), files.grants)
        assertEquals(0, files.cleanupCalls)
    }
}
