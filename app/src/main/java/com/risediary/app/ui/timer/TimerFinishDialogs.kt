package com.risediary.app.ui.timer

import com.risediary.app.ui.components.RiseDialogAction
import com.risediary.app.ui.components.DialogActionTone

import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.components.LiquidAlertDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TimerFinishDialogs(session: TimerSession, persistenceError: Boolean, vm: TimerViewModel) {
    if (session.finishCandidate == null) return
    LiquidAlertDialog(
            onDismissRequest = { if (!persistenceError && !vm.busy) vm.cancelFinish() },
            modifier = Modifier.verticalScroll(rememberScrollState()),
            title = { Text("结束计时", style = MiuixTheme.textStyles.title3) },
            text = { Text(if (persistenceError) stringResource(R.string.timer_error_save)
            else stringResource(R.string.timer_finish_dialog_message)) },
            confirmAction = RiseDialogAction(text = stringResource(if (persistenceError) R.string.action_retry else R.string.timer_confirm_end), onClick = if (persistenceError) vm::retryPersistence else vm::confirmFinish, tone = DialogActionTone.Normal, enabled = !vm.busy),
            dismissAction = RiseDialogAction(text = "取消", onClick = vm::cancelFinish, tone = DialogActionTone.Normal, enabled = !persistenceError && !vm.busy),
            adaptiveActions = true
        )
}
