package com.risediary.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.components.RisePreferenceLink
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.SystemBarIconOverride
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun VideoSourceSheet(show: Boolean, onDismiss: () -> Unit, onLibrary: () -> Unit, onPhone: () -> Unit) {
    WindowBottomSheet(show = show && LocalPageEffectsActive.current, title = "选择视频来源", onDismissRequest = onDismiss) {
        SystemBarIconOverride(forceLightIcons = false)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            top.yukonga.miuix.kmp.basic.Card(Modifier.fillMaxWidth()) {
                Column {
                    RisePreferenceLink("素材库", onLibrary, summary = "浏览素材，手动开始计时", icon = AppIcons.Video)
                    RisePreferenceLink("手机视频", onPhone, summary = "选择视频，首次播放自动计时", icon = AppIcons.Folder)
                }
            }
        }
    }
}
