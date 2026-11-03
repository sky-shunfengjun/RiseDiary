package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.LibraryEntry
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.SystemBarIconOverride
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun LibrarySearchSheet(state: LibraryBrowserState, onQuery: (String) -> Unit, onDismiss: () -> Unit,
    onDismissFinished: () -> Unit, onMore: () -> Unit, onClick: (LibraryEntry) -> Unit, onSelect: (LibraryEntry) -> Unit,
    onRename: (LibraryEntry) -> Unit, onMove: (LibraryEntry) -> Unit, onDelete: (LibraryEntry) -> Unit,
    onMoveSelection: () -> Unit, onDeleteSelection: () -> Unit, onClearSelection: () -> Unit,
    onCover: (LibraryEntry) -> Unit = {}) {
    var retained by remember { mutableStateOf<LibraryBrowserState?>(null) }
    if (state.searching) SideEffect { retained = state }
    val shown = if (state.searching) state else retained ?: return
    val visible = state.searching && LocalPageEffectsActive.current
    val enabled = visible && !state.busy
    val list = rememberLazyListState()
    LaunchedEffect(shown.query) { list.scrollToItem(0) }
        WindowBottomSheet(show = visible, title = "搜索素材库", modifier = Modifier.height(LocalWindowInfo.current.containerDpSize.height * .85f)
            .imePadding().testTag("library-search-sheet"), insideMargin = DpSize(0.dp, 0.dp), enableNestedScroll = false,
            allowDismiss = !state.busy,
            startAction = {
                val keyboard = LocalSoftwareKeyboardController.current
                IconButton({ keyboard?.hide(); onDismiss() }, Modifier.padding(start = 12.dp).size(48.dp), enabled = enabled) {
                Icon(AppIcons.Close, "关闭搜索", Modifier.size(24.dp))
            } }, onDismissRequest = { if (!state.busy) onDismiss() },
            onDismissFinished = { if (!state.searching) { retained = null; onDismissFinished() } }) {
            SystemBarIconOverride(forceLightIcons = false)
            val keyboard = LocalSoftwareKeyboardController.current
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibrarySearchInput(shown.query, onQuery, { keyboard?.hide() }, enabled)
                if (shown.selected.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("已选 ${shown.selected.size} 项 · 取消", onClearSelection, Modifier.weight(1f), enabled = enabled)
                    IconButton(onMoveSelection, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Reorder, "移动所选", Modifier.size(22.dp)) }
                    IconButton(onDeleteSelection, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Delete, "删除所选", Modifier.size(22.dp)) }
                }
                RiseFieldError(shown.error)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (shown.query.isNotBlank() && shown.searchEntries.isEmpty()) item { RiseEmptyState("没有找到匹配的素材或文件夹，换个名称试试。") }
                    items(shown.searchEntries, key = { it.key }) { entry ->
                        LibraryBrowserItem(entry, list = true, showPath = true, selected = entry.key in shown.selected, enabled = enabled,
                            onClick = { keyboard?.hide(); onClick(entry) }, onSelect = { onSelect(entry) }, onRename = { onRename(entry) },
                            onMove = { onMove(entry) }, onDelete = { onDelete(entry) }, onMenuOpen = { keyboard?.hide() },
                            onCover = if (entry.asset != null) ({ onCover(entry) }) else null)
                    }
                    if (shown.searchHasMore) item { TextButton("加载更多", onMore, enabled = enabled) }
                }
            }
        }
}

@Composable
internal fun LibrarySearchInput(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, enabled: Boolean = true) {
    // BasicTextField must receive each edit synchronously. Results may acknowledge an older query.
    var editor by rememberSaveable { mutableStateOf(query) }
    InputField(query = editor, onQueryChange = { value ->
        val next = value.take(1000)
        editor = next
        onQuery(next)
    }, onSearch = { onSearch() }, expanded = true, onExpandedChange = {},
        label = "搜索素材或文件夹名称", enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索整个素材库" })
}
