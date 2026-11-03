package com.risediary.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.library.*
import com.risediary.app.service.LibraryTransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryBrowserState(
    val snapshot: LibrarySnapshot = LibrarySnapshot(), val entries: List<LibraryEntry> = emptyList(),
    val folderId: String? = null, val path: String = "素材库", val searching: Boolean = false, val query: String = "",
    val view: LibraryViewMode = LibraryViewMode.GRID, val sort: LibrarySort = LibrarySort.NEWEST,
    val selected: Set<String> = emptySet(), val loading: Boolean = true, val hasMore: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val cleanupPending: Int = 0,
    val folderDepth: Int = 0, val searchEntries: List<LibraryEntry> = emptyList(), val searchHasMore: Boolean = false,
    val recommendation: LibraryAsset? = null, val recommendationError: String? = null, val coverRevision: Long = 0L
)
private data class BrowserLocation(val folderId: String?, val searching: Boolean = false, val query: String = "", val limit: Int = 100,
    val selected: Set<String> = emptySet(), val depth: Int = 0, val searchLimit: Int = 100)
private data class BrowserOperation(val busy: Boolean = false, val error: String? = null, val cleanupPending: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(private val repository: LibraryRepository,
    private val preferences: UserPreferences, private val saved: SavedStateHandle,
    val transfers: LibraryTransferManager, @ApplicationContext private val context: Context,
    private val recommender: LibraryRecommender) : ViewModel() {
    private val location = MutableStateFlow(BrowserLocation(null))
    private var folderHistory: LibraryFolderHistory? = null
    private val operation = MutableStateFlow(BrowserOperation())
    private val refresh = MutableStateFlow(0)
    private val folderLimits = HashMap<String?, Int>()
    private var lastSnapshot = LibrarySnapshot()
    private val content = refresh.flatMapLatest {
        repository.snapshots.onEach { lastSnapshot = it }.catch { e ->
            if (e is CancellationException) throw e
            operation.update { it.copy(error = "素材库读取失败，请重试") }
            emit(lastSnapshot)
        }
    }
    private val localDay = flow {
        while (true) {
            val now = java.time.ZonedDateTime.now()
            emit(now.toLocalDate())
            val untilMidnight = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis()
            kotlinx.coroutines.delay(untilMidnight.coerceIn(1L, 60_000L))
        }
    }.distinctUntilChanged()
    val state = combine(content, location, preferences.libraryView, preferences.librarySort, repository.catalogError) { snapshot, where, view, sort, catalogError ->
        withContext(Dispatchers.Default) {
            val folder = where.folderId
            val tree = LibraryTree(snapshot)
            val all = tree.entries(folder, "", sort)
            val results = if (where.query.isBlank()) emptyList() else tree.entries(null, where.query, sort)
            val existing = snapshot.assets.filter { it.state == "ready" }.map { "asset:${it.id}" }.toSet() + snapshot.folders.map { "folder:${it.id}" }
            LibraryBrowserState(snapshot, all.take(where.limit), folder, tree.path(folder), where.searching, where.query, view, sort,
                where.selected.intersect(existing), loading = false, hasMore = all.size > where.limit, error = catalogError,
                folderDepth = where.depth, searchEntries = results.take(where.searchLimit), searchHasMore = results.size > where.searchLimit)
        }
    }.combine(localDay) { browser, day ->
        try { browser.copy(recommendation = recommender.pick(day, browser.snapshot.assets)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { browser.copy(recommendationError = "每日推荐暂时无法读取，请重试") }
    }.combine(repository.coverRevision) { browser, revision -> browser.copy(coverRevision = revision) }
    .combine(operation) { state, op -> state.copy(busy = op.busy, error = op.error ?: state.error, cleanupPending = op.cleanupPending) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryBrowserState())

    init {
        viewModelScope.launch {
            try { transfers.refresh(); retryCleanupNow() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { showError("素材库任务读取失败，请重试") }
        }
    }
    fun more() { location.update { it.copy(limit = it.limit + 100) } }
    fun startFolderSession(root: String) {
        if (folderHistory != null) return
        val savedPages = saved.get<ArrayList<String>>("library-folder-pages").orEmpty()
        folderHistory = LibraryFolderHistory(if (savedPages.firstOrNull() == root) savedPages else listOf(root))
        displayFolderHistory()
    }
    fun reconcileFolders(available: Set<String>): Boolean {
        val history = folderHistory ?: return true
        if (!history.reconcile(available)) return false
        if (history.current != location.value.folderId) displayFolderHistory()
        return true
    }
    private fun displayFolderHistory() {
        val history = folderHistory ?: return
        saved["library-folder-pages"] = ArrayList(history.pages)
        location.value = BrowserLocation(history.current, limit = folderLimits[history.current] ?: 100, depth = history.depth)
    }
    fun openFolder(id: String) {
        val history = folderHistory ?: return
        folderLimits[location.value.folderId] = location.value.limit
        history.open(id)
        displayFolderHistory()
    }
    fun search() {
        location.update { it.copy(searching = true, selected = emptySet()) }
    }
    fun query(value: String) { location.update { it.copy(query = value.take(1000), searchLimit = 100, selected = emptySet()) } }
    fun searchMore() { location.update { it.copy(searchLimit = it.searchLimit + 100) } }
    fun closeSearch() { location.update { it.copy(searching = false, selected = emptySet()) } }
    fun finishSearchClose() { if (!location.value.searching) location.update { it.copy(query = "", searchLimit = 100) } }
    fun back(): Boolean {
        if (operation.value.busy) return true
        when {
            state.value.selected.isNotEmpty() -> clearSelection()
            location.value.searching -> closeSearch()
            folderHistory?.back() == true -> {
                folderLimits[location.value.folderId] = location.value.limit
                displayFolderHistory()
            }
            else -> return false
        }
        return true
    }
    fun toggle(key: String) { location.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) } }
    fun clearSelection() { location.update { it.copy(selected = emptySet()) } }
    fun view(value: LibraryViewMode) = action { preferences.setLibraryView(value) }
    fun sort(value: LibrarySort) = action { preferences.setLibrarySort(value) }
    fun createFolder(name: String, done: () -> Unit) = action { repository.createFolder(state.value.folderId, name); done() }
    fun rename(key: String, name: String, done: () -> Unit) = action { repository.rename(key, name); done() }
    fun move(keys: Set<String>, target: String?, done: () -> Unit) = action { repository.move(keys, target); clearSelection(); done() }
    fun previewDelete(keys: Set<String>, done: (LibraryDeletePreview) -> Unit) = action { done(repository.previewDelete(keys)) }
    fun delete(keys: Set<String>, expected: LibraryDeletePreview, done: () -> Unit) = action {
        val remaining = repository.delete(keys, expected)
        operation.update { it.copy(cleanupPending = remaining) }
        clearSelection(); done()
    }
    fun retryCleanup() = action { retryCleanupNow() }
    private suspend fun retryCleanupNow() {
        val count = repository.retryCleanup()
        operation.update { it.copy(cleanupPending = count) }
    }
    fun retryRead() = action { repository.writeCatalog(); refresh.value++; retryCleanupNow() }
    fun clearError() { operation.update { it.copy(error = null) } }
    fun refreshRecommendation() { refresh.value++ }
    fun import(uris: List<Uri>, folder: String? = state.value.folderId) = action {
        if (uris.isNotEmpty()) { transfers.enqueue(uris, folder); startService() }
    }
    fun retryTransfer() = action { startService() }
    fun dismissTransfer(discardPending: Boolean = false) = action { transfers.dismissResult(discardPending) }
    private fun action(block: suspend () -> Unit) {
        if (operation.value.busy) return
        operation.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e.message ?: "操作未完成，请重试") }
            finally { operation.update { it.copy(busy = false) } }
        }
    }
    private fun startService() { ContextCompat.startForegroundService(context, Intent(context, LibraryTransferService::class.java)) }
    private fun showError(message: String) { operation.update { it.copy(error = message) } }
}
