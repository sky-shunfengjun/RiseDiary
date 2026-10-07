package com.risediary.app.ui.tags

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Tag
import com.risediary.app.data.repository.TagRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class TagManagerReliabilityTest {
    @Test fun failedTagDeleteKeepsDialogCallbackPendingAndRetrySucceedsOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Tags()
        val vm = model(repository)
        try {
            runCurrent()
            repository.failDelete = true
            var closed = 0
            vm.delete(repository.original) { closed++ }; vm.delete(repository.original) { closed++ }; runCurrent()
            assertEquals(0, closed); assertEquals(listOf(repository.original), repository.rows)
            assertNotNull(vm.error.value)
            repository.failDelete = false
            vm.retryWrite(); vm.retryWrite(); runCurrent()
            assertEquals(1, closed); assertTrue(repository.rows.isEmpty())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun readFailureRetainsTagsAndDisablesWritesUntilReadRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = Tags().apply { failRead = true }
        val vm = model(repository)
        try {
            runCurrent()
            assertTrue(vm.readFailed.value); assertEquals(repository.rows, vm.tags.value)
            vm.delete(repository.original); runCurrent()
            assertEquals(0, repository.deletes)
            repository.failRead = false; vm.retryRead(); runCurrent()
            assertFalse(vm.readFailed.value)
            vm.delete(repository.original); runCurrent()
            assertEquals(1, repository.deletes)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private fun model(repository: Tags): TagManagerViewModel {
        // No Android services are used by valid deletion/reading; test the real VM and repository boundary.
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val context = unsafe.allocateInstance(ContextWrapper::class.java) as ContextWrapper
        return TagManagerViewModel(repository, context, DataMaintenanceGate())
    }
    private class Tags : TagRepository {
        val original = Tag(id = 1, name = "原标签", color = "#112233", sortOrder = 0)
        var rows = listOf(original); var failRead = false; var failDelete = false; var deletes = 0
        override val allTags: Flow<List<Tag>> = flow { emit(rows); if (failRead) throw IOException("read failed") }
        override suspend fun getAll() = rows
        override suspend fun getById(id: Long) = rows.find { it.id == id }
        override suspend fun getByName(name: String) = rows.find { it.name == name }
        override suspend fun count() = rows.size
        override suspend fun insert(tag: Tag): Long { rows = rows + tag; return tag.id }
        override suspend fun update(tag: Tag) { rows = rows.map { if (it.id == tag.id) tag else it } }
        override suspend fun updateAll(tags: List<Tag>) { rows = tags }
        override suspend fun delete(tag: Tag) { if (failDelete) throw IOException("delete failed"); deletes++; rows = rows.filterNot { it.id == tag.id } }
    }
}
