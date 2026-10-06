package com.risediary.app.ui.length

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.repository.LengthRecordRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LengthHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val draft = LengthRecord(recordDate = 1_000L, flaccidLengthCm = 6f, erectLengthCm = 12f)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun twoSaveTapsWhileFirstInsertIsPendingCreateOneRecord() = runTest(dispatcher) {
        val repo = MemoryLengths()
        val pending = CompletableDeferred<Unit>()
        repo.beforeInsert = { pending.await() }
        val vm = viewModel(repo)
        runCurrent()
        var saved = 0
        vm.save(draft) { saved++ }
        vm.save(draft) { saved++ }
        runCurrent()
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals("A pending save must reject a second tap synchronously", 1, repo.rows.size)
        assertEquals(1, saved)
    }

    @Test fun insertFailureKeepsInputRetryableAndCreatesOnlyOneCommittedRecord() = runTest(dispatcher) {
        val repo = MemoryLengths()
        var fail = true
        repo.beforeInsert = { if (fail) throw IOException("disk full") }
        val vm = viewModel(repo)
        runCurrent()
        var saved = 0
        vm.save(draft) { saved++ }
        advanceUntilIdle()
        assertNotNull(vm.error.value)
        assertEquals(0, saved)
        assertTrue(repo.rows.isEmpty())
        fail = false
        vm.save(draft) { saved++ }
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertEquals(1, repo.rows.size)
        assertEquals(1, saved)
    }

    @Test fun cancelledInsertDoesNotBecomeAnErrorOrBlockNextSave() = runTest(dispatcher) {
        val repo = MemoryLengths()
        var cancel = true
        repo.beforeInsert = { if (cancel) throw CancellationException("leaving") }
        val vm = viewModel(repo)
        runCurrent()
        vm.save(draft)
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertTrue(repo.rows.isEmpty())
        cancel = false
        vm.save(draft)
        advanceUntilIdle()
        assertEquals(1, repo.rows.size)
    }

    @Test fun cancellingDuringCommitStillHandsOffTheCommittedRecordOnce() = runTest(dispatcher) {
        val repo = MemoryLengths()
        val pending = CompletableDeferred<Unit>()
        repo.beforeInsert = { pending.await() }
        val vm = viewModel(repo)
        runCurrent()
        var saved = 0
        vm.save(draft) { saved++ }
        runCurrent()
        store.clear()
        runCurrent()
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.rows.size)
        assertEquals("A committed write must not leave a retryable editor behind", 1, saved)
        assertNull(vm.error.value)
    }

    @Test fun cancellationAfterCommitDoesNotSwallowCancellationAndRunReminderWork() = runTest(dispatcher) {
        val repo = MemoryLengths()
        var reminders = 0
        val vm = LengthHistoryViewModel(repo, { throw CancellationException("closing") }, { reminders++ }, { "invalid" })
            .let(::track)
        runCurrent()
        var saved = 0
        vm.save(draft) { saved++ }
        advanceUntilIdle()
        assertEquals(1, repo.rows.size)
        assertEquals(1, saved)
        assertEquals("Cancellation after commit must stop optional follow-up work", 0, reminders)
        assertNull(vm.error.value)
    }

    @Test fun readFailureIsVisibleAndCanRetryWithoutCrashing() = runTest(dispatcher) {
        val repo = MemoryLengths()
        repo.rows += draft.copy(id = 1)
        repo.readFailure = IOException("database unavailable")
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertNotNull("Read errors must be rendered rather than escape viewModelScope", vm.error.value)
        assertTrue(vm.periodRecords.value.isEmpty())
        repo.readFailure = null
        vm.load()
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertEquals(repo.rows, vm.periodRecords.value)
    }

    @Test fun deleteFailureLeavesRecordAndCanRetryWithoutCrashing() = runTest(dispatcher) {
        val repo = MemoryLengths()
        val record = draft.copy(id = 1)
        repo.rows += record
        val vm = viewModel(repo)
        runCurrent()
        repo.deleteFailure = IOException("database write failed")
        vm.delete(record)
        advanceUntilIdle()
        assertNotNull("Delete errors must keep the record and expose a retry", vm.error.value)
        assertEquals(listOf(record), vm.periodRecords.value)
        repo.deleteFailure = null
        vm.delete(record)
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertTrue(vm.periodRecords.value.isEmpty())
    }

    @Test fun cancelledDeleteDoesNotBecomeAnErrorOrBlockNextWrite() = runTest(dispatcher) {
        val repo = MemoryLengths()
        val record = draft.copy(id = 1)
        repo.rows += record
        val vm = viewModel(repo)
        runCurrent()
        repo.deleteFailure = CancellationException("leaving")
        vm.delete(record)
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertEquals(listOf(record), vm.periodRecords.value)
        repo.deleteFailure = null
        vm.delete(record)
        advanceUntilIdle()
        assertTrue(vm.periodRecords.value.isEmpty())
    }

    private fun viewModel(repo: MemoryLengths) = LengthHistoryViewModel(repo, {}, {}, { "invalid length" })
        .let(::track)


    private fun track(vm: LengthHistoryViewModel) = ViewModelProvider(store, object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(vm))
    })["lengths", LengthHistoryViewModel::class.java]
    /** Only the disk boundary is fake; the actual ViewModel and write gate run in every test. */
    private class MemoryLengths : LengthRecordRepository {
        val rows = mutableListOf<LengthRecord>()
        var beforeInsert: suspend () -> Unit = {}
        var readFailure: Exception? = null
        var deleteFailure: Exception? = null
        override val allRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
        override suspend fun insert(record: LengthRecord): Long {
            beforeInsert()
            val id = (rows.maxOfOrNull { it.id } ?: 0L) + 1
            rows += record.copy(id = id)
            return id
        }
        override suspend fun update(record: LengthRecord) { rows[rows.indexOfFirst { it.id == record.id }] = record }
        override suspend fun delete(record: LengthRecord) { deleteFailure?.let { throw it }; rows.remove(record) }
        override suspend fun getById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun getAll(): List<LengthRecord> { readFailure?.let { throw it }; return rows.toList() }
        override suspend fun getCurrentMonthRecord() = rows.lastOrNull()
        override suspend fun getThisYearRecords() = rows.toList()
        override suspend fun count() = rows.size
        override suspend fun getSince(since: Long) = rows.filter { it.recordDate >= since }
        override suspend fun maxErectLength() = rows.maxOfOrNull { it.erectLengthCm } ?: 0f
        override suspend fun firstErectLength() = rows.firstOrNull()?.erectLengthCm
    }
}
