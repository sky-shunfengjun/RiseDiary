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