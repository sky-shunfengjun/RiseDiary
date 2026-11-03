package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.*
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.*

@Composable
internal fun LibraryToolbar(state: LibraryBrowserState, onSearch: () -> Unit,
    onView: (LibraryViewMode) -> Unit, onSort: (LibrarySort) -> Unit, onMoveSelection: () -> Unit, onDeleteSelection: () -> Unit) {
    var viewing by remember { mutableStateOf(false) }
    val enabled = !state.busy
    if (state.selected.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onMoveSelection, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Reorder, "移动所选", Modifier.size(22.dp)) }
        IconButton(onDeleteSelection, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Delete, "删除所选", Modifier.size(22.dp)) }
    } else FloatingToolbar(modifier = Modifier.testTag("library-tools"), outSidePadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.padding(horizontal = 2.dp)) {
            IconButton(onSearch, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Search, "搜索整个素材库", Modifier.size(20.dp)) }
            Box {
                IconButton({ viewing = true }, Modifier.size(48.dp), enabled = enabled) { Icon(AppIcons.Tune, "视图与排序", Modifier.size(20.dp)) }
                LibraryPopup(viewing, { viewing = false }) {
                    LibraryViewMenu(state.view, state.sort, { viewing = false; onView(it) }, { viewing = false; onSort(it) })
                }
            }
        }
    }
}

@Composable
internal fun LibraryViewMenu(view: LibraryViewMode, sort: LibrarySort, onView: (LibraryViewMode) -> Unit, onSort: (LibrarySort) -> Unit) {
    LibraryMenuRow("网格", AppIcons.Dashboard, selected = view == LibraryViewMode.GRID) { onView(LibraryViewMode.GRID) }
    LibraryMenuRow("列表", AppIcons.List, selected = view == LibraryViewMode.LIST) { onView(LibraryViewMode.LIST) }
    LibraryMenuDivider()
    LibrarySort.entries.forEach { option ->
        val icon = when (option) { LibrarySort.NEWEST -> AppIcons.Schedule; LibrarySort.NAME -> AppIcons.Notes; LibrarySort.SIZE -> AppIcons.Storage; LibrarySort.DURATION -> AppIcons.Timer }
        LibraryMenuRow("按${option.label}", icon, selected = sort == option) { onSort(option) }
    }
}
