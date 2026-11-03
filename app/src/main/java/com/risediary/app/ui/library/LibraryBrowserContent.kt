package com.risediary.app.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.*
import com.risediary.app.R
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LibraryBrowserContent(state: LibraryBrowserState, transfer: LibraryTransferState,
    grid: LazyGridState, headerHeight: Dp, onMore: () -> Unit, onRetry: () -> Unit, onRetryCleanup: () -> Unit,
    onImport: () -> Unit, onCancelTransfer: () -> Unit, onRetryTransfer: () -> Unit, onDismissTransfer: (Boolean) -> Unit,
    onClick: (LibraryEntry) -> Unit, onSelect: (LibraryEntry) -> Unit, onRename: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit, onDelete: (LibraryEntry) -> Unit,
    bottomSpacing: Dp = mainPageBottomSpacing(), recommendation: (@Composable () -> Unit)? = null,
    onCover: (LibraryEntry) -> Unit = {}) {
    if (!state.loading && state.error == null && state.folderId != null && state.entries.isEmpty() &&
        transfer.items.isEmpty() && state.cleanupPending == 0) {
        Box(Modifier.fillMaxSize().padding(top = headerHeight + 8.dp, bottom = minOf(bottomSpacing, 16.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(AppIcons.Folder, null, Modifier.size(64.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = .2f))
                Spacer(Modifier.height(12.dp))
                Text("此文件夹暂无素材", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        return
    }
    val list = state.view == LibraryViewMode.LIST
    // The full-bleed recommendation must stay attached to the top edge during a pull.
    val edgeEffect = if (recommendation == null) rememberOverscrollEffect() else null
    LazyVerticalGrid(if (list) GridCells.Fixed(1) else GridCells.Adaptive(150.dp), state = grid,
        overscrollEffect = edgeEffect,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (recommendation == null) headerHeight + 8.dp else 0.dp, bottom = bottomSpacing),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (list) 4.dp else 16.dp)) {
        if (recommendation != null) item(key = "daily-recommendation", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.libraryFullBleed()) { recommendation() }
        }
        state.recommendationError?.takeIf { state.folderId == null }?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { RiseFieldError(message); TextButton("重试推荐", onRetry) }
        } }
        if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (transfer.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryTransferCard(transfer, onCancelTransfer, onRetryTransfer, onDismissTransfer)
        }
        if (state.cleanupPending > 0) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.cleanupPending} 个素材的文件尚未清理，空间还未完全释放。", style = MiuixTheme.textStyles.body2)
                TextButton("重试清理", onRetryCleanup, enabled = !state.busy)
            }
        }
        state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RiseFieldError(message)
                TextButton("重试读取", onRetry, enabled = !state.busy)
            }
        } }
        if (!state.loading && state.error == null && state.entries.isEmpty() && state.folderId == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.searching) Text(stringResource(R.string.library_empty_title), style = MiuixTheme.textStyles.title3)
                RiseEmptyState(if (state.searching && state.query.isNotBlank()) "没有找到匹配的素材或文件夹，换个名称试试。"
                    else stringResource(R.string.library_empty_description), icon = AppIcons.Video)
                if (!state.searching) RiseDialogActionButton(RiseDialogAction(stringResource(R.string.library_import), onImport, DialogActionTone.Normal,
                    enabled = !state.busy && !transfer.running), emphasized = true)
            }
        }
        items(state.entries, key = { it.key }, contentType = { if (it.folder != null) "folder" else "video" }) { entry ->
            LibraryBrowserItem(entry, list, false, entry.key in state.selected, !state.busy,
                onClick = { onClick(entry) }, onSelect = { onSelect(entry) }, onRename = { onRename(entry) },
                onMove = { onMove(entry) }, onDelete = { onDelete(entry) }, onCover = if (entry.asset != null) ({ onCover(entry) }) else null)
        }
        if (state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(stringResource(R.string.library_load_more), onMore) }
    }
}

@Composable
internal fun LibraryBrowserItem(entry: LibraryEntry, list: Boolean, showPath: Boolean, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, onSelect: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onMenuOpen: () -> Unit = {},
    onCover: (() -> Unit)? = null) {
    var menu by remember(entry.key) { mutableStateOf(false) }
    Box {
        val itemModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = { onMenuOpen(); menu = true }, onLongClickLabel = "更多操作")
            .semantics(mergeDescendants = true) {
                contentDescription = entry.name
                this.selected = selected
                customActions = listOf(CustomAccessibilityAction("更多操作") { if (enabled) { onMenuOpen(); menu = true; true } else false })
            }
        @Composable fun thumbnail(modifier: Modifier) {
            if (entry.asset != null) LibraryCover(entry.asset, modifier.clip(RoundedCornerShape(14.dp)))
            else Box(modifier.background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Folder, null, Modifier.size(if (list) 28.dp else 40.dp), tint = MiuixTheme.colorScheme.primary)
            }
        }
        @Composable fun names(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body2,
                    fontWeight = if (entry.folder != null) FontWeight.Medium else FontWeight.Normal)
                Text(entry.asset?.let { "${libraryDuration(it.durationMillis)} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, it.sizeBytes)}" } ?: "文件夹",
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (showPath) Text(entry.path, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        if (list) Row(itemModifier.heightIn(min = 80.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thumbnail(Modifier.width(80.dp).height(60.dp)); names(Modifier.weight(1f))
            if (selected) Icon(AppIcons.CheckCircle, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary)
            else if (entry.folder != null) Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp))
        } else Column(itemModifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                thumbnail(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                if (selected) Icon(AppIcons.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp), tint = MiuixTheme.colorScheme.primary)
            }
            names(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
        }
        LibraryPopup(menu, { menu = false }) {
            if (onCover != null) {
                LibraryMenuRow("修改封面", AppIcons.Video, enabled = enabled) { menu = false; onCover() }
                LibraryMenuDivider()
            }
            LibraryMenuRow("重命名", AppIcons.Edit, enabled = enabled) { menu = false; onRename() }
            LibraryMenuRow("移动", AppIcons.Reorder, enabled = enabled) { menu = false; onMove() }
            LibraryMenuDivider()
            LibraryMenuRow("多选", AppIcons.CheckCircle, enabled = enabled) { menu = false; onSelect() }
            LibraryMenuDivider()
            LibraryMenuRow("删除", AppIcons.Delete, destructive = true, enabled = enabled) { menu = false; onDelete() }
        }
    }
}

@Composable
private fun LibraryTransferCard(transfer: LibraryTransferState, cancel: () -> Unit, retry: () -> Unit, dismiss: (Boolean) -> Unit) {
    RiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.library_transfer_count, transfer.completed, transfer.items.size), fontWeight = FontWeight.SemiBold)
            if (transfer.running) {
                val context = LocalContext.current
                Text(transfer.totalBytes?.let { stringResource(R.string.library_transfer_bytes,
                    android.text.format.Formatter.formatShortFileSize(context, transfer.bytes), android.text.format.Formatter.formatShortFileSize(context, it)) }
                    ?: stringResource(R.string.library_transfer_title), style = MiuixTheme.textStyles.body2)
                TextButton(stringResource(R.string.action_cancel), cancel)
            } else {
                Text(transfer.message, style = MiuixTheme.textStyles.body2)
                if (transfer.remaining.isNotEmpty()) {
                    TextButton(stringResource(R.string.action_retry), retry); TextButton(stringResource(R.string.library_ignore_pending), { dismiss(true) })
                } else TextButton(stringResource(R.string.library_dismiss_transfer), { dismiss(false) })
            }
            transfer.items.filter { it.status == "failed" || it.status == "duplicate" }.take(5).forEach { item ->
                Text(if (item.status == "duplicate") stringResource(R.string.library_duplicate, item.name, item.detail) else "${item.name}：${item.detail}", style = MiuixTheme.textStyles.footnote1)
            }
        }
    }
}

internal val LocalLibraryCoverRevision = staticCompositionLocalOf { 0L }

@Composable
internal fun LibraryCover(asset: LibraryAsset, modifier: Modifier = Modifier, highResolution: Boolean = false) {
    val context = LocalContext.current
    val revision = LocalLibraryCoverRevision.current
    val file = remember(asset.id) { LibraryFileStorage(File(context.filesDir, "RiseDiaryLibrary")).cover(asset.id) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, asset.id, asset.coverFrameMillis, revision, highResolution) {
        value = null
        value = withContext(Dispatchers.IO) {
            LibraryCoverFileAccess.mutex.withLock {
                runCatching { android.util.AtomicFile(file).openRead().use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = if (highResolution) 1 else 2 })?.asImageBitmap()
                } }.getOrNull()
            }
        }
    }
    Box(modifier.background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(AppIcons.Video, null, Modifier.size(28.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

internal fun libraryDuration(millis: Long?): String = millis?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) } ?: "—"
