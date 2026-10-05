package com.risediary.app.ui.records

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.risediary.app.R
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlayerController
import com.risediary.app.ui.components.LiquidActionButton
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.ui.video.*
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DetailVideoCard(
    video: LocalVideoRef,
    state: DetailVideoState,
    controller: VideoPlayerController,
    owner: VideoSurfaceOwner,
    onShow: () -> Unit,
    onHide: () -> Unit,
    onRetry: () -> Unit,
    onRelink: () -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    active: Boolean = true
) {
    val controlsEnabled = state.controlsEnabled && !busy && active
    val cover: (@Composable (Backdrop) -> Unit)? = if (state.hidden || !state.prepared ||
        state.loading || state.problem != null || busy) ({ backdrop ->
        DetailVideoCover(state, busy, backdrop, onShow, onRetry, onRelink, active)
    }) else null
    LocalVideoPlayer(controller, owner, false, onFullScreen, modifier,
        titleContent = {
            DetailVideoHeader(video.displayName, showHide = state.settingsReady && !state.hidden,
                onHide = onHide, enabled = active)
        }, concealed = state.hidden, controlsEnabled = controlsEnabled,
        surfaceEnabled = state.settingsReady && state.prepared && !state.hidden && !busy,
        coverContent = cover, compactConcealed = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailVideoHeader(filename: String, showHide: Boolean, onHide: () -> Unit, enabled: Boolean) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(colors.primary.copy(alpha = 0.09f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(AppIcons.Video, null, Modifier.size(22.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.video_title), style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                Text(filename, color = colors.onSurfaceVariantSummary, fontSize = 13.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        com.risediary.app.ui.components.AnimatedUiVisibility(showHide) { visible ->
            val backdrop = rememberAttachmentBackdrop()
            val description = stringResource(R.string.detail_video_hide)
            Box {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop))
                com.risediary.app.ui.components.LiquidGlassButton(onHide, backdrop,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = description },
                    enabled = visible && enabled, isInteractive = visible && enabled,
                    horizontalPadding = 14.dp, surfaceColor = attachmentButtonSurface(),
                    tint = colors.primary.copy(alpha = 0.06f),
                    highlightIntensity = 0.38f, highlightRadiusMultiplier = 1f, pressExpansion = 2.dp) {
                    Icon(AppIcons.VisibilityOff, null, Modifier.size(20.dp), tint = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun rememberAttachmentBackdrop(): com.kyant.backdrop.backdrops.LayerBackdrop {
    val surface = if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    val current by rememberUpdatedState(surface)
    return com.kyant.backdrop.backdrops.rememberLayerBackdrop { drawRect(current); drawContent() }
}

@Composable
private fun attachmentButtonSurface(): Color = if (LocalRiseDarkTheme.current) Color.White.copy(alpha = 0.3f) else Color.Unspecified

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailVideoCover(
    state: DetailVideoState,
    busy: Boolean,
    backdrop: Backdrop,
    onShow: () -> Unit,
    onRetry: () -> Unit,
    onRelink: () -> Unit,
    enabled: Boolean = true
) {
    val waiting = busy || state.loading || !state.settingsReady && !state.settingsFailed
    val problem = state.problem.takeUnless { state.hidden && !state.settingsFailed }
    val coverModifier = if (state.hidden) Modifier.fillMaxWidth()
        else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    Column(coverModifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AnimatedContent(targetState = waiting to problem, label = "detail_video_cover_status",
            transitionSpec = { (fadeIn(tween(170)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(190) })) }) { (loading, error) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) CircularProgressIndicator() else {
                    Icon(if (error == null) AppIcons.VisibilityOff else AppIcons.Info, null,
                        Modifier.size(32.dp), tint = MiuixTheme.colorScheme.primary)
                }
                Text(if (loading) stringResource(R.string.video_loading) else error ?: stringResource(R.string.detail_video_hidden),
                    color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        if (!waiting) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically) {
                if (problem == null && state.hidden) {
                    LiquidActionButton(stringResource(R.string.detail_video_show), AppIcons.Visibility,
                        onShow, backdrop, enabled = enabled && state.settingsReady, surfaceColor = attachmentButtonSurface())
                } else {
                    LiquidActionButton(stringResource(R.string.action_retry), AppIcons.Refresh,
                        onRetry, backdrop, enabled = enabled, surfaceColor = attachmentButtonSurface())
                    if (!state.settingsFailed) {
                        LiquidActionButton(stringResource(R.string.video_relink), AppIcons.Refresh,
                            onRelink, backdrop, enabled = enabled, surfaceColor = attachmentButtonSurface())
                    }
                }
            }
        }
    }
}
