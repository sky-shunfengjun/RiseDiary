package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.risediary.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.components.AnimatedUiVisibility
import com.risediary.app.ui.components.InlineStatusContent
import com.risediary.app.ui.components.LiquidActionButton
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoAttachmentCard(
    video: LocalVideoRef?,
    onPlay: () -> Unit,
    onSelect: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    busy: Boolean = false,
    error: String? = null,
    unavailable: Boolean = false,
    playEnabled: Boolean = true,
    selectLabel: String? = null,
    busyText: String? = null,
    titleContent: (@Composable () -> Unit)? = null
) {
    val colors = MiuixTheme.colorScheme
    // Original LiquidButton surface sample; keep the shared recipe and shadow unchanged.
    val buttonSurface = if (LocalRiseDarkTheme.current) Color.White.copy(alpha = 0.3f) else Color.Unspecified
    val ownBackdrop = rememberLayerBackdrop { drawRect(colors.surface); drawContent() }
    val backdrop = ownBackdrop
    val status = listOfNotNull(
        if (busy) busyText ?: stringResource(R.string.video_processing) else null,
        if (unavailable && !busy) stringResource(R.string.video_unavailable) else null
    )
    var retainedStatus by remember { mutableStateOf(status) }
    var retainedError by remember { mutableStateOf(error) }
    SideEffect {
        if (status.isNotEmpty()) retainedStatus = status
        if (error != null) retainedError = error
    }
    RiseCard(Modifier.fillMaxWidth(), allowContentOverflow = true) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().layerBackdrop(ownBackdrop))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(AppIcons.Video, null, Modifier.size(22.dp), tint = colors.primary)
                        Text(stringResource(R.string.video_title), style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                video?.let {
                    Text(it.displayName, style = MiuixTheme.textStyles.body1,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                AnimatedUiVisibility(status.isNotEmpty()) { active ->
                    InlineStatusContent(messages = if (active) status else retainedStatus,
                        backdrop = backdrop, error = false)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (video != null && !unavailable) {
                        LiquidActionButton(stringResource(R.string.video_play_short), AppIcons.PlayArrow,
                            onPlay, backdrop, enabled = !busy && playEnabled, surfaceColor = buttonSurface)
                    }
                    onSelect?.let { action ->
                        LiquidActionButton(selectLabel ?: stringResource(
                            if (video == null) R.string.video_add else R.string.video_replace
                        ), if (video == null) AppIcons.Add else AppIcons.Refresh,
                            action, backdrop, enabled = !busy, surfaceColor = buttonSurface)
                    }
                    if (video != null) onRemove?.let {
                        LiquidActionButton(stringResource(R.string.video_remove), AppIcons.Close,
                            it, backdrop, enabled = !busy, destructive = true, surfaceColor = buttonSurface)
                    }
                }
                AnimatedUiVisibility(error != null) {
                    InlineStatusContent(listOfNotNull(error ?: retainedError), backdrop = backdrop)
                }
            }
        }
    }
}
