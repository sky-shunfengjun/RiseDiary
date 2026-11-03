package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.SystemBarIconOverride
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LibraryPageLayout(state: LibraryBrowserState, secondary: Boolean, onBack: () -> Unit,
    toolbar: @Composable () -> Unit, floatingAction: (@Composable (Backdrop) -> Unit)? = null,
    content: @Composable (LazyGridState, Dp, Dp) -> Unit) {
    val grid = rememberLazyGridState()
    val density = LocalDensity.current
    if (secondary) {
        val name = if (state.folderId == null) "素材库" else state.snapshot.folders.firstOrNull { it.id == state.folderId }?.name ?: "文件夹"
        val title = if (state.selected.isNotEmpty()) "已选 ${state.selected.size} 项" else name
        SecondaryPageScaffold(title = title, actions = { toolbar() }, floatingActionButton = floatingAction,
            reserveFloatingActionButtonSpace = false, titleContent = { modifier ->
            LibraryFadingText(title, MiuixTheme.textStyles.title1.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                MiuixTheme.colorScheme.onSurface, modifier)
        }, onBack = onBack, topBlurProgress = rememberTopBlurProgress(grid)) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryFadingText(state.path, MiuixTheme.textStyles.body2, MiuixTheme.colorScheme.onSurfaceVariantSummary, Modifier.fillMaxWidth())
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content(grid, 4.dp, if (floatingAction != null) 80.dp else 16.dp) }
            }
        }
    } else {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomBarHeight = mainBottomBarHeight()
        val createBottom = navigationBottom + 8.dp + bottomBarHeight + 12.dp
        val contentBottom = if (floatingAction != null) maxOf(mainPageBottomSpacing(), createBottom + 52.dp + 16.dp) else mainPageBottomSpacing()
        val source = rememberLayerBackdrop()
        val blurProgress = rememberTopBlurProgress(grid)
        val hasHero = state.recommendation != null
        if (hasHero && LocalPageEffectsActive.current) SystemBarIconOverride(forceLightIcons = blurProgress() < .5f)
        var headerHeight by remember(density, statusTop) { mutableStateOf(statusTop + (if (density.fontScale > 1.5f) 156.dp else 76.dp)) }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(source)) {
                PageTopBlurLayout(progress = blurProgress, topBarHeight = headerHeight - statusTop, fadeHeight = 24.dp,
                    overlay = {
                        BoxWithConstraints(Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                            .padding(start = 20.dp, end = 20.dp, top = statusTop + 16.dp, bottom = 12.dp)) {
                            val stacked = maxWidth < 240.dp || density.fontScale > 1.5f
                            @Composable fun title(modifier: Modifier) {
                                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                                    if (state.selected.isNotEmpty()) IconButton(onBack, Modifier.size(48.dp), enabled = !state.busy) {
                                        Icon(AppIcons.Close, "退出多选", Modifier.size(22.dp), tint = if (hasHero && blurProgress() < .65f) Color.White else MiuixTheme.colorScheme.onSurface)
                                    }
                                    Text(if (state.selected.isNotEmpty()) "已选 ${state.selected.size} 项" else "素材库", Modifier.weight(1f),
                                        style = MiuixTheme.textStyles.title1.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (hasHero && blurProgress() < .65f) Color.White else MiuixTheme.colorScheme.onSurface)
                                }
                            }
                            if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                title(Modifier.fillMaxWidth()); Box(Modifier.align(Alignment.End)) { toolbar() }
                            } else Row(verticalAlignment = Alignment.CenterVertically) { title(Modifier.weight(1f)); toolbar() }
                        }
                    }) { content(grid, headerHeight, contentBottom) }
            }
            if (floatingAction != null) Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                .padding(end = 12.dp, bottom = 8.dp + bottomBarHeight + 12.dp).width(64.dp), contentAlignment = Alignment.Center) {
                floatingAction(source)
            }
        }
    }
}
