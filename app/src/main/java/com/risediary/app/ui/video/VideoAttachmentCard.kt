package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoAttachmentCard(
    video: LocalVideoRef?,
    onPlay: () -> Unit,
    onSelect: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    busy: Boolean = false,
    error: String? = null
) {
    RiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("视频", fontWeight = FontWeight.SemiBold)
            video?.let { Text(it.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (video != null) TextButton(text = "播放", onClick = onPlay, enabled = !busy)
                onSelect?.let { action ->
                    TextButton(text = if (video == null) "添加视频" else "更换",
                        onClick = action, enabled = !busy)
                }
                if (video != null) onRemove?.let { TextButton(text = "移除", onClick = it, enabled = !busy) }
            }
            error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
        }
    }
}
