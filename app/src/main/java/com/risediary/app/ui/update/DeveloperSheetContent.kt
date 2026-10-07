package com.risediary.app.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.update.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Password is intentionally ordinary, transient Compose state, never saveable or logged. */
@Composable
internal fun DeveloperSheetContent(
    ui: UpdateUiState,
    viewModel: UpdateViewModel,
    authorized: Boolean,
    show: Boolean,
    onRestartUpdateIntro: (() -> Unit)? = null,
    onChannelExpanded: (Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val enabled = show && !ui.developer.busy
    LaunchedEffect(show, authorized) {
        if (!show || authorized) { password = ""; focus.clearFocus() }
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!authorized) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = password,
                        onValueChange = { password = it.filter { c -> c in '0'..'9' }.take(32) },
                        label = stringResource(R.string.developer_password),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("developer_password"),
                    )
                    TextButton(
                        text = stringResource(R.string.developer_verify),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { focus.clearFocus(); viewModel.verifyDeveloperPassword(password) },
                    )
                }
            }
        } else {
            Column {
                SmallTitle(stringResource(R.string.developer_update_section))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.developer_force),
                        summary = stringResource(R.string.developer_force_summary),
                        checked = ui.settings.forceCheck,
                        onCheckedChange = viewModel::setForceCheck,
                        enabled = enabled,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    key(enabled) {
                        OverlaySpinnerPreference(
                            title = stringResource(R.string.developer_channel),
                            items = listOf(DropdownItem(stringResource(R.string.developer_stable)),
                                DropdownItem(stringResource(R.string.developer_preview))),
                            selectedIndex = ReleaseChannel.entries.indexOf(ui.settings.releaseChannel),
                            onSelectedIndexChange = { index ->
                                ReleaseChannel.entries.getOrNull(index)?.let(viewModel::setReleaseChannel)
                            },
                            renderInRootScaffold = false,
                            onExpandedChange = onChannelExpanded,
                            enabled = enabled,
                        )
                    }
                }
            }
            PlayerDiagnosticsEntry(viewModel.videoDiagnostics, enabled)
            if (onRestartUpdateIntro != null) Card(
                Modifier.fillMaxWidth().testTag("developer_update_intro"),
                colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
                onClick = { if (enabled) onRestartUpdateIntro() },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(com.risediary.app.ui.icons.AppIcons.Refresh, null, Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.developer_update_intro))
                        Text(stringResource(R.string.developer_update_intro_summary),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2)
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
                onClick = { if (enabled) viewModel.requestDeveloperReset() },
            ) {
                Text(stringResource(R.string.developer_reset), Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    color = Color(0xFFEF5350))
            }
        }
        ui.developer.error?.let { error ->
            Text(stringResource(if (error == DeveloperError.PASSWORD) R.string.developer_wrong_password
                else R.string.developer_save_error), color = Color(0xFFEF5350))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun DeveloperResetDialog(show: Boolean, viewModel: UpdateViewModel) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.developer_reset),
        onDismissRequest = viewModel::cancelDeveloperReset,
        renderInRootScaffold = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.developer_reset_message))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = stringResource(R.string.developer_reset_cancel), modifier = Modifier.weight(1f),
                    onClick = viewModel::cancelDeveloperReset)
                TextButton(text = stringResource(R.string.developer_reset_confirm), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(), onClick = viewModel::confirmDeveloperReset)
            }
        }
    }
}
@Composable
private fun PlayerDiagnosticsEntry(diagnostics: com.risediary.app.media.VideoDiagnostics, enabled: Boolean) {
    var show by remember { mutableStateOf(false) }
    val active by diagnostics.enabled.collectAsState()
    val snapshot by diagnostics.snapshot.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = updateSheetCardColor()),
        onClick = { if (enabled) show = true }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(com.risediary.app.ui.icons.AppIcons.Video, null, Modifier.size(24.dp))
            Column { Text("播放器诊断"); Text(if (active) "已开启，仅保留当前会话" else "查看掉帧与缓冲情况",
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
    }
    OverlayDialog(show = show, title = "播放器诊断", renderInRootScaffold = false,
        onDismissRequest = { show = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SwitchPreference(title = "开启诊断", summary = "仅保存在内存，不包含文件名、路径或记录内容",
                checked = active, onCheckedChange = diagnostics::setEnabled)
            if (active) Text(snapshot.report())
            else Text("开启后播放同一视频，可对比普通与全屏模式的掉帧和缓冲。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("关闭", modifier = Modifier.weight(1f), onClick = { show = false })
                TextButton("复制", modifier = Modifier.weight(1f), enabled = active, onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("播放器诊断", snapshot.report()))
                })
            }
        }
    }
}
