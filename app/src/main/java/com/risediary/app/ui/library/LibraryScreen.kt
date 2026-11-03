package com.risediary.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.risediary.app.R
import com.risediary.app.data.library.*
import com.risediary.app.ui.components.*
import com.risediary.app.ui.blockInteractionsAndAccessibility
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.*

private data class FolderPage(val id: String?, val depth: Int)

@Composable
fun LibraryScreen(vm: LibraryViewModel = hiltViewModel(), folderRootId: String? = null, standalone: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transfer by vm.transfers.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val libraryRoute = remember(navigator) { navigator.current() }
    var importInfo by rememberSaveable { mutableStateOf(false) }
    var importFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<LibraryNameRequest?>(null) }
    var moving by remember { mutableStateOf<Set<String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Set<String>, LibraryDeletePreview>?>(null) }
    var coverId by rememberSaveable { mutableStateOf<String?>(null) }
    if (LocalPageEffectsActive.current) LifecycleResumeEffect(vm) {
        vm.refreshRecommendation()
        onPauseOrDispose { }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.import(it, importFolder) }
    LaunchedEffect(folderRootId) { folderRootId?.let(vm::startFolderSession) }
    LaunchedEffect(folderRootId, state.snapshot.folders, state.loading) {
        if (folderRootId != null && !state.loading && state.folderDepth > 0 &&
            !vm.reconcileFolders(state.snapshot.folders.map { it.id }.toSet()) && navigator.current() == Route.LibraryFolder(folderRootId)) navigator.pop()
    }
    if (folderRootId != null && (state.loading || state.folderDepth == 0)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val transition = updateTransition(FolderPage(state.folderId, state.folderDepth), label = "library-folder")
    val animating = folderRootId != null && (transition.isRunning || transition.currentState != transition.targetState)
    fun goBack() { if (!animating && !vm.back()) navigator.pop() }
    fun requestImport() { importFolder = state.folderId; importInfo = true }
    fun requestDelete(keys: Set<String>) { vm.previewDelete(keys) { deleting = keys to it } }
    fun openEntry(entry: LibraryEntry) {
        when {
            state.selected.isNotEmpty() -> vm.toggle(entry.key)
            entry.folder != null -> {
                vm.closeSearch()
                if (folderRootId == null) navigator.push(Route.LibraryFolder(entry.folder.id)) else vm.openFolder(entry.folder.id)
            }
            entry.asset != null -> { vm.closeSearch(); navigator.push(Route.LibraryViewer(entry.asset.id)) }
        }
    }
    PageBackHandler(enabled = !state.searching && (state.selected.isNotEmpty() || folderRootId != null)) { goBack() }
    val holder = rememberSaveableStateHolder()
    @Composable fun page(displayed: LibraryBrowserState, interactive: Boolean) {
        val shown = displayed.copy(searching = false, busy = displayed.busy || !interactive)
        holder.SaveableStateProvider("${shown.folderId}|${shown.view}") {
            LibraryPageLayout(shown, folderRootId != null || standalone, ::goBack, toolbar = {
                LibraryToolbar(shown, vm::search, vm::view, vm::sort, { moving = shown.selected }, { requestDelete(shown.selected) })
            }, floatingAction = { backdrop ->
                if (shown.selected.isEmpty()) LibraryCreateButton(backdrop, !shown.loading && !shown.busy && !transfer.running,
                    ::requestImport, { edit = LibraryNameRequest(null, "") })
            }) { grid, header, bottom ->
                LibraryBrowserContent(shown, transfer, grid, header, onMore = vm::more, onRetry = vm::retryRead,
                    onRetryCleanup = vm::retryCleanup, onImport = ::requestImport, onCancelTransfer = vm.transfers::cancel,
                    onRetryTransfer = vm::retryTransfer, onDismissTransfer = vm::dismissTransfer, onClick = ::openEntry,
                    onSelect = { vm.toggle(it.key) }, onRename = { edit = LibraryNameRequest(it.key, it.name) },
                    onMove = { moving = setOf(it.key) }, onDelete = { requestDelete(setOf(it.key)) }, bottomSpacing = bottom,
                    onCover = { coverId = it.asset?.id }, recommendation = if (!standalone && folderRootId == null && shown.recommendation != null) ({
                        val density = LocalDensity.current
                        val height = maxOf(LocalWindowInfo.current.containerDpSize.height / 3, header + maxOf(140.dp, (130f * density.fontScale).dp))
                        LibraryRecommendationCard(shown.recommendation, height, !shown.busy && shown.selected.isEmpty(),
                            { if (navigator.current() == libraryRoute) navigator.push(Route.LibraryViewer(shown.recommendation.id)) })
                    }) else null)
            }
        }
    }
    CompositionLocalProvider(LocalLibraryCoverRevision provides state.coverRevision) {
    if (folderRootId == null) page(state, true)
    else transition.AnimatedContent(modifier = Modifier.fillMaxSize().clipToBounds(), transitionSpec = {
        val direction = if (targetState.depth > initialState.depth) 1 else -1
        ((slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it * direction } + fadeIn(tween(350, easing = FastOutSlowInEasing)))
            togetherWith (slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it * direction } + fadeOut(tween(350, easing = FastOutSlowInEasing))))
            .using(SizeTransform(clip = false))
    }) { frame ->
        var retained by remember { mutableStateOf(state) }
        if (frame.id == state.folderId) SideEffect { retained = state }
        val displayed = if (frame.id == state.folderId) state else retained
        Box(Modifier.fillMaxSize().blockInteractionsAndAccessibility(animating || frame != transition.targetState)) {
            page(displayed, !animating && frame == transition.targetState)
        }
    }
    LibrarySearchSheet(state, onQuery = vm::query, onDismiss = vm::closeSearch, onDismissFinished = vm::finishSearchClose,
        onMore = vm::searchMore, onClick = ::openEntry, onSelect = { vm.toggle(it.key) },
        onRename = { vm.closeSearch(); edit = LibraryNameRequest(it.key, it.name) },
        onMove = { vm.closeSearch(); moving = setOf(it.key) }, onDelete = { vm.closeSearch(); requestDelete(setOf(it.key)) },
        onMoveSelection = { val keys = state.selected; vm.closeSearch(); moving = keys },
        onDeleteSelection = { val keys = state.selected; vm.closeSearch(); requestDelete(keys) }, onClearSelection = vm::clearSelection,
        onCover = { vm.closeSearch(); coverId = it.asset?.id })
    LibraryCoverSheet(coverId?.let { id -> state.snapshot.assets.firstOrNull { it.id == id && it.state == "ready" } }, { coverId = null })
    if (importInfo) LiquidAlertDialog(onDismissRequest = { if (!state.busy) importInfo = false },
        title = { Text(stringResource(R.string.library_import)) }, text = { Text(stringResource(R.string.library_import_notice)) },
        confirmAction = RiseDialogAction(stringResource(R.string.library_choose_videos), { importInfo = false; picker.launch(arrayOf("video/*")) }, DialogActionTone.Normal, enabled = !state.busy),
        dismissAction = RiseDialogAction("取消", { importInfo = false }, DialogActionTone.Normal))
    edit?.let { request -> LibraryNameDialog(request, state.busy, state.error,
        onDismiss = { edit = null; vm.clearError() }, onConfirm = { name ->
            if (request.key == null) vm.createFolder(name) { edit = null } else vm.rename(request.key, name) { edit = null }
        }) }
    moving?.let { keys -> LibraryMoveDialog(state.snapshot, keys, state.folderId, state.busy, state.error,
        onDismiss = { moving = null; vm.clearError() }, onConfirm = { folder -> vm.move(keys, folder) { moving = null } }) }
    deleting?.let { (keys, preview) -> LiquidAlertDialog(onDismissRequest = { if (!state.busy) { deleting = null; vm.clearError() } },
        title = { Text("删除所选内容？") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("将删除 ${preview.folders} 个文件夹、${preview.videos} 个视频。${preview.records} 条日记会失去回看关联，日记内容保留。手机原视频不受影响，删除后无法撤销。")
                RiseFieldError(state.error)
            }
        }, confirmAction = RiseDialogAction(if (state.busy) "正在删除…" else "删除", { vm.delete(keys, preview) { deleting = null } }, DialogActionTone.Destructive, enabled = !state.busy),
        dismissAction = RiseDialogAction("取消", { deleting = null; vm.clearError() }, DialogActionTone.Normal, enabled = !state.busy)) }
    }
}
