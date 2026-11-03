package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Reuses the established add-button glass recipe; only its layout size is smaller. */
@Composable
internal fun LibraryCreateButton(backdrop: Backdrop, enabled: Boolean, onImport: () -> Unit, onCreateFolder: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        LiquidGlassButton(onClick = { expanded = true }, backdrop = backdrop,
            modifier = Modifier.size(52.dp).testTag("library-create-action"), enabled = enabled, isInteractive = enabled,
            tint = MiuixTheme.colorScheme.primary.copy(alpha = .035f), height = 52.dp, horizontalPadding = 0.dp,
            highlightIntensity = .36f, highlightRadiusMultiplier = .98f, pressExpansion = 2.dp) {
            Icon(AppIcons.Add, "添加素材", Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        }
        LibraryPopup(expanded, { expanded = false }) {
            LibraryMenuRow("导入视频", AppIcons.Download, enabled = enabled) { expanded = false; onImport() }
            LibraryMenuDivider()
            LibraryMenuRow("新建文件夹", AppIcons.Folder, enabled = enabled) { expanded = false; onCreateFolder() }
        }
    }
}
