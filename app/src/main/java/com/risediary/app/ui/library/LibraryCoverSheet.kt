package com.risediary.app.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.data.library.LibraryAsset
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.SystemBarIconOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun LibraryCoverSheet(asset: LibraryAsset?, onDismiss: () -> Unit, vm: LibraryCoverViewModel = hiltViewModel()) {
    val current by vm.state.collectAsStateWithLifecycle()
    val problem by vm.error.collectAsStateWithLifecycle()
    var retained by remember { mutableStateOf<LibraryCoverEditState?>(null) }
    LaunchedEffect(asset?.id) { asset?.let(vm::open) }
    LaunchedEffect(current.done) { if (current.done && asset != null) { onDismiss(); vm.close() } }
    DisposableEffect(vm) { onDispose { vm.close() } }
    if (asset != null) SideEffect { retained = current }
    val state = if (asset != null) current else retained ?: return
    val visible = asset != null && LocalPageEffectsActive.current
    val enabled = visible && !state.saving
    fun dismiss() { if (!current.saving) { onDismiss(); vm.close() } }
    WindowBottomSheet(show = visible, title = "修改封面",
        modifier = Modifier.height(LocalWindowInfo.current.containerDpSize.height * .85f).testTag("library-cover-sheet"),
        insideMargin = DpSize(0.dp, 0.dp), enableNestedScroll = false, allowDismiss = !state.saving,
        startAction = { IconButton(::dismiss, Modifier.padding(start = 12.dp).size(48.dp), enabled = enabled) {
            Icon(AppIcons.Close, "关闭封面编辑", Modifier.size(24.dp))
        } }, onDismissRequest = ::dismiss, onDismissFinished = { if (asset == null) retained = null }) {
        SystemBarIconOverride(forceLightIcons = false)
        val background = MiuixTheme.colorScheme.surface
        val backdrop = rememberLayerBackdrop { drawRect(background); drawContent() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(background))
            LibraryCoverSheetLayout(
                compact = LocalWindowInfo.current.containerDpSize.height < 500.dp || LocalDensity.current.fontScale > 1.5f,
                modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                actions = {
                    RiseDialogActions(RiseDialogAction(if (state.saving) "正在保存…" else "保存封面", vm.editor::save, DialogActionTone.Normal,
                        enabled && !state.loading && state.frame != null && state.frame.positionMillis == state.positionMillis),
                        dismissAction = RiseDialogAction("取消", ::dismiss, DialogActionTone.Normal, enabled),
                        neutralAction = RiseDialogAction("恢复自动封面", vm.editor::restoreAutomatic, DialogActionTone.Normal, enabled && state.assetId != null))
                }) {
                    val bytes = state.frame?.jpeg
                    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, bytes) {
                        value = withContext(Dispatchers.Default) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).aspectRatio(16f / 9f)
                        .background(MiuixTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                        image?.let { Image(it, "封面预览", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        if (state.loading || state.assetId == null && problem == null) CircularProgressIndicator()
                    }
                    val duration = state.frame?.durationMillis ?: asset?.durationMillis ?: 0L
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(libraryDuration(state.positionMillis), style = MiuixTheme.textStyles.body2)
                        Text(libraryDuration(duration), style = MiuixTheme.textStyles.body2)
                    }
                    LiquidSlider(value = { if (duration > 0) (state.positionMillis.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f },
                        onValueChange = { vm.editor.seek((it.toDouble() * duration).toLong()) }, valueRange = 0f..1f, steps = 0,
                        backdrop = backdrop, enabled = enabled && duration > 1L, modifier = Modifier.testTag("library-cover-timeline"))
                    Text("拖动选择一个画面作为封面", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    RiseFieldError(problem ?: state.error)
                    if (state.error != null) RiseDialogActionButton(RiseDialogAction("重试预览", vm.editor::retry, DialogActionTone.Normal, enabled))
            }
        }
    }
}

/** On short or large-font windows, actions scroll with content instead of being clipped. */
@Composable
internal fun LibraryCoverSheetLayout(compact: Boolean, modifier: Modifier = Modifier,
    actions: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        actions()
    }
}
