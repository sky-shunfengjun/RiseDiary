package com.risediary.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.risediary.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ModeSelectScreen(vm: ModeSelectViewModel = hiltViewModel()) {
    val navigator = LocalNavigator.current
    LaunchedEffect(vm.nextRoute) {
        vm.nextRoute?.let { navigator.push(it); vm.consumeRoute() }
    }
    SecondaryPageScaffold(title = stringResource(R.string.mode_select_title), onBack = { navigator.pop() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val choices = listOf(
                Triple(FlightEntry.NORMAL, stringResource(R.string.mode_select_timer), stringResource(R.string.mode_select_timer_summary)),
                Triple(FlightEntry.VIDEO, stringResource(R.string.mode_select_video), stringResource(R.string.mode_select_video_summary)),
                Triple(FlightEntry.MANUAL, stringResource(R.string.mode_select_manual), stringResource(R.string.mode_select_manual_summary))
            )
            choices.forEach { (entry, title, summary) ->
                RiseCard(modifier = Modifier.fillMaxWidth(), onClick = { if (!vm.busy) vm.open(entry) }) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (entry) {
                            FlightEntry.NORMAL -> AppIcons.Schedule
                            FlightEntry.VIDEO -> AppIcons.PlayArrow
                            FlightEntry.MANUAL -> AppIcons.Edit
                        }, null, Modifier.size(40.dp), tint = MiuixTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = MiuixTheme.textStyles.title4.fontSize)
                            Spacer(Modifier.height(4.dp))
                            Text(summary, fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Icon(AppIcons.ChevronRight, null, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
    if (vm.currentTimer != null) {
        LiquidAlertDialog(onDismissRequest = vm::dismissCurrent,
            title = { Text("请先处理当前计时") },
            confirmButton = { TextButton("返回计时", vm::returnToTimer, colors = liquidDialogConfirmButtonColors()) },
            dismissButton = { TextButton("取消", vm::dismissCurrent, colors = liquidDialogCancelButtonColors()) })
    }
    vm.error?.let { message ->
        LiquidAlertDialog(onDismissRequest = vm::dismissError, title = { Text(message) },
            confirmButton = { TextButton("知道了", vm::dismissError, colors = liquidDialogConfirmButtonColors()) })
    }
}
