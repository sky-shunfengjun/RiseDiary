package com.risediary.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.risediary.app.data.library.*
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class LibraryNameRequest(val key: String?, val name: String)

@Composable
internal fun LibraryPopup(show: Boolean, dismiss: () -> Unit, content: @Composable () -> Unit) {
    OverlayListPopup(show = show, onDismissRequest = dismiss, alignment = PopupPositionProvider.Align.End) {
        val maxWidth = (LocalWindowInfo.current.containerDpSize.width - 32.dp).coerceAtLeast(0.dp)
        val maxHeight = (LocalWindowInfo.current.containerDpSize.height - 48.dp).coerceAtLeast(48.dp)
        ListPopupColumn {
            Column(Modifier.widthIn(min = 224.dp.coerceAtMost(maxWidth), max = 320.dp.coerceAtMost(maxWidth))
                .heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(8.dp)) { content() }
        }
    }
}

@Composable
internal fun LibraryMenuRow(text: String, icon: ImageVector, selected: Boolean = false, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = when { destructive -> MiuixTheme.colorScheme.error; selected -> MiuixTheme.colorScheme.primary; else -> MiuixTheme.colorScheme.onSurface }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, onClick = onClick).semantics { this.selected = selected }
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(text, Modifier.weight(1f), color = color)
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun LibraryMenuDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

@Composable
internal fun LibraryNameDialog(request: LibraryNameRequest, busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(request) { mutableStateOf(request.name) }
    LiquidAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (request.key == null) "新建文件夹" else "重命名") },
        text = { RiseLabeledField("名称", name, { name = it.take(1000) }, error = error, enabled = !busy) },
        confirmAction = RiseDialogAction(if (busy) "保存中…" else "保存", { onConfirm(name) }, DialogActionTone.Normal, enabled = !busy && name.isNotBlank()),
        dismissAction = RiseDialogAction("取消", onDismiss, DialogActionTone.Normal, enabled = !busy))
}

@Composable
internal fun LibraryMoveDialog(snapshot: LibrarySnapshot, keys: Set<String>, current: String?, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val tree = remember(snapshot) { LibraryTree(snapshot) }
    val selection = remember(snapshot, keys) { runCatching { tree.selection(keys) }.getOrNull() }
    val blocked = selection?.folderIds.orEmpty()
    var target by remember(keys) { mutableStateOf(current?.takeIf { it !in blocked }) }
    val exists = target == null || snapshot.folders.any { it.id == target }
    LiquidAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("移动到") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tree.path(target), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body2)
            if (target != null) TextButton("返回上一级", { target = snapshot.folders.firstOrNull { it.id == target }?.parentId }, enabled = !busy)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                val children = snapshot.folders.filter { it.parentId == target && it.id !in blocked }.sortedBy { it.name }
                if (children.isEmpty()) item { RiseEmptyState("此位置没有下级文件夹") }
                items(children, key = { it.id }) { folder -> RiseListItem(folder.name, { if (!busy) target = folder.id },
                    modifier = Modifier.fillMaxWidth(), icon = AppIcons.Folder) { Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp)) } }
            }
            RiseFieldError(if (selection == null || !exists) "内容已经变化，请关闭后重新选择" else error)
        }
    }, confirmAction = RiseDialogAction(if (busy) "移动中…" else "移动到这里", { onConfirm(target) }, DialogActionTone.Normal,
        enabled = !busy && selection != null && exists && target !in blocked),
        dismissAction = RiseDialogAction("取消", onDismiss, DialogActionTone.Normal, enabled = !busy))
}
