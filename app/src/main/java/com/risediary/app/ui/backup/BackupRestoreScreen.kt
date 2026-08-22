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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.LiquidAlertDialog
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

@Composable
fun BackupRestoreScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    vm: BackupViewModel = hiltViewModel()
) {
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
        title = "备份与恢复",
        onBack = { navController.navigateUp() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Export ──
            Text("导出备份", style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "将所有记录和设置导出为 ZIP 文件，保存到 Downloads 文件夹。",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var showExportConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showExportConfirm = true },
                        enabled = state != BackupState.WORKING,
                        modifier = Modifier.fillMaxWidth()
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
                            Text("导出中...")
                        } else {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导出备份")
                        }
                    }

                    if (showExportConfirm) {
                        LiquidAlertDialog(
                            onDismissRequest = { showExportConfirm = false },
title = { Text("确认导出") },
                            text = { Text("将导出所有记录和设置到 Downloads 文件夹。文件名为 RiseDiary_backup_日期.zip。") },
                            confirmButton = {
                                TextButton(
                                    text = "确认导出",
                                    onClick = {
                                        showExportConfirm = false
                                        if (vm.needsUserSelectedExportDestination) {
                                            exportLauncher.launch(vm.defaultFilename())
                                        } else {
                                            vm.exportBackup()
                                        }
                                    }
                                )
                            },
                            dismissButton = {
                                TextButton(
                                    text = "取消",
                                    onClick = { showExportConfirm = false }
                                )
                            }
                        )
                    }
                }
            }

            // ── Import ──
            Text("恢复备份", style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "从 ZIP 备份文件恢复数据。当前数据将被覆盖。",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var showRestoreConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showRestoreConfirm = true },
                        enabled = state != BackupState.WORKING,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondary,
                            contentColor = MiuixTheme.colorScheme.onSecondary
                        )
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
                            Text("恢复中...")
                        } else {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("选择备份文件")
                        }
                    }

                    if (showRestoreConfirm) {
                        LiquidAlertDialog(
                            onDismissRequest = { showRestoreConfirm = false },
title = { Text("确认恢复") },
                            text = { Text("当前所有数据将被备份中的内容覆盖，此操作不可撤销。确定要继续吗？") },
                            confirmButton = {
                                TextButton(
                                    text = "确认恢复",
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
                                    text = "取消",
                                    onClick = { showRestoreConfirm = false }
                                )
                            }
                        )
                    }
                }
            }

            // ── Clear ──
            Text("清除数据", style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = CardRed)

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "删除所有飞行记录、标签、成就和设置。此操作不可恢复，建议先导出备份。",
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
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清除所有数据")
                    }
                }
            }

            // ── Clear confirmation dialog ──
            if (showClearDialog) {
                var inputText by remember { mutableStateOf("") }
                LiquidAlertDialog(
                    onDismissRequest = {
                        vm.dismissClearDialog()
                        inputText = ""
                    },
                    containerColor = MiuixTheme.colorScheme.surface,
                    title = { Text("危险操作", color = CardRed) },
                    text = {
                        Column {
                            Text("此操作将永久删除所有数据。请输入「确认删除」以继续：")
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = "确认删除",
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        val confirmed = inputText.trim() == "确认删除"
                        TextButton(
                            text = "确认清除",
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
                            text = "取消",
                            onClick = {
                                vm.dismissClearDialog()
                                inputText = ""
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
