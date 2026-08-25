package com.risediary.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.ui.components.LiquidSnackbarTone
import com.risediary.app.ui.components.showLiquidSnackbar
import com.risediary.app.ui.theme.CardRed
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
fun BackupRestoreScreen(
    snackbarHostState: SnackbarHostState,
    vm: BackupViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val showClearDialog by vm.showClearConfirm.collectAsStateWithLifecycle()
    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(vm::importBackup)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let(vm::exportBackupTo)
    }

    // Show snackbar on state changes
    LaunchedEffect(state, message, snackbarHostState) {
        when (state) {
            BackupState.SUCCESS -> {
                try {
                    snackbarHostState.showLiquidSnackbar(
                        message = message,
                        tone = LiquidSnackbarTone.SUCCESS
                    )
                } finally {
                    vm.resetState()
                }
            }
            BackupState.ERROR -> {
                try {
                    snackbarHostState.showLiquidSnackbar(
                        message = message,
                        tone = LiquidSnackbarTone.ERROR
                    )
                } finally {
                    vm.resetState()
                }
            }
            else -> {}
        }
    }

    SecondaryPageScaffold(
        title = stringResource(R.string.settings_backup),
        onBack = { navigator.pop() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Export ──
            Text(stringResource(R.string.backup_export_section), style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.backup_export_description),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var showExportConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showExportConfirm = true },
                        enabled = state != BackupState.WORKING,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        if (state == BackupState.WORKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_exporting))
                        } else {
                            Icon(AppIcons.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_export_section))
                        }
                    }

                    if (showExportConfirm) {
                        LiquidAlertDialog(
                            onDismissRequest = { showExportConfirm = false },
title = { Text(stringResource(R.string.backup_confirm_export_title)) },
                            text = { Text(stringResource(R.string.backup_confirm_export_message)) },
                            confirmButton = {
                                TextButton(
                                    text = stringResource(R.string.backup_confirm_export_title),
                                    onClick = {
                                        showExportConfirm = false
                                        if (vm.needsUserSelectedExportDestination) {
                                            exportLauncher.launch(vm.defaultFilename())
                                        } else {
                                            vm.exportBackup()
                                        }
                                    },
                                    colors = liquidDialogConfirmButtonColors()
                                )
                            },
                            dismissButton = {
                                TextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { showExportConfirm = false },
                                    colors = liquidDialogCancelButtonColors()
                                )
                            }
                        )
                    }
                }
            }

            // ── Import ──
            Text(stringResource(R.string.backup_restore_section), style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.backup_restore_description),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var showRestoreConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showRestoreConfirm = true },
                        enabled = state != BackupState.WORKING,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        if (state == BackupState.WORKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = MiuixTheme.colorScheme.onSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_restoring))
                        } else {
                            Icon(AppIcons.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_select_file))
                        }
                    }

                    if (showRestoreConfirm) {
                        LiquidAlertDialog(
                            onDismissRequest = { showRestoreConfirm = false },
title = { Text(stringResource(R.string.backup_confirm_restore_title)) },
                            text = { Text(stringResource(R.string.backup_confirm_restore_message)) },
                            confirmButton = {
                                TextButton(
                                    text = stringResource(R.string.backup_confirm_restore_title),
                                    onClick = {
                                        showRestoreConfirm = false
                                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        color = Color.Transparent,
                                        textColor = MiuixTheme.colorScheme.error,
                                        disabledColor = Color.Transparent,
                                        disabledTextColor = MiuixTheme.colorScheme.error
                                    )
                                )
                            },
                            dismissButton = {
                                TextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { showRestoreConfirm = false },
                                    colors = liquidDialogCancelButtonColors()
                                )
                            }
                        )
                    }
                }
            }

            // ── Clear ──
            Text(stringResource(R.string.backup_clear_section), style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = CardRed)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.backup_clear_description),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { vm.showClearDialog() },
                        enabled = state != BackupState.WORKING,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = CardRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(AppIcons.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_clear_all))
                    }
                }
            }

            // ── Clear confirmation dialog ──
            if (showClearDialog) {
                var inputText by remember { mutableStateOf("") }
                val confirmText = stringResource(R.string.backup_clear_confirmation_text)
                LiquidAlertDialog(
                    onDismissRequest = {
                        vm.dismissClearDialog()
                        inputText = ""
                    },
                    containerColor = MiuixTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.backup_clear_danger_title), color = CardRed) },
                    text = {
                        Column {
                            Text(stringResource(R.string.backup_clear_danger_message))
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = confirmText,
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        val confirmed = inputText.trim() == confirmText
                        TextButton(
                            text = stringResource(R.string.backup_clear_confirm_button),
                            onClick = {
                                vm.clearAllData()
                                vm.dismissClearDialog()
                                inputText = ""
                            },
                            enabled = confirmed,
                            colors = ButtonDefaults.textButtonColors(
                                color = Color.Transparent,
                                textColor = if (confirmed) {
                                    CardRed
                                } else {
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                                disabledColor = Color.Transparent,
                                disabledTextColor =
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        )
                    },
                    dismissButton = {
                        TextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = {
                                vm.dismissClearDialog()
                                inputText = ""
                            },
                            colors = liquidDialogCancelButtonColors()
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
