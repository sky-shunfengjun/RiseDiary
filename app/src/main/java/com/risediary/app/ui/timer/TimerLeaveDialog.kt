package com.risediary.app.ui.timer

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TimerLeaveDialog(
    session: TimerSession,
    sessionId: String?,
    vm: TimerViewModel,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
    beforeDiscard: () -> Unit = {},
    starting: Boolean = false
) {
    val blocked = starting || vm.busy || vm.discardComplete
    val owned = sessionId != null && session.sessionId == sessionId
    fun dismiss() {
        if (blocked || vm.discarding) return
        onDismiss()
        if (owned && session.isTerminal && session.finishCandidate == null) vm.openRecord()
    }
    LiquidAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = Modifier.verticalScroll(rememberScrollState()),
        title = { Text(stringResource(R.string.timer_leave_dialog_title)) },
        text = {
            Column {
                Text(stringResource(when {
                    starting -> R.string.timer_leave_starting
                    session.status == TimerStatus.PAUSED -> R.string.timer_leave_paused_message
                    else -> R.string.timer_leave_dialog_message
                }))
                vm.error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            }
        },
        neutralButton = {
            TextButton(
                text = stringResource(when {
                    vm.discarding && vm.busy -> R.string.timer_leave_terminating
                    vm.discarding -> R.string.timer_leave_retry_terminate
                    else -> R.string.timer_leave_terminate
                }),
                onClick = { vm.discardTimer(expectedSessionId = sessionId, beforeSend = beforeDiscard) },
                enabled = !blocked && owned && session.finishCandidate == null &&
                    (session.isActive || session.isTerminal),
                colors = ButtonDefaults.textButtonColors(
                    color = Color.Transparent, disabledColor = Color.Transparent,
                    textColor = MiuixTheme.colorScheme.error,
                    disabledTextColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                )
            )
        },
        confirmButton = {
            TextButton(
                text = stringResource(if (session.status == TimerStatus.PAUSED)
                    R.string.timer_leave_paused_confirm else R.string.timer_leave_confirm),
                onClick = onLeave,
                enabled = !blocked && !vm.discarding && owned && session.isActive && session.finishCandidate == null,
                colors = liquidDialogConfirmButtonColors()
            )
        },
        dismissButton = {
            TextButton(stringResource(R.string.timer_leave_cancel), ::dismiss,
                enabled = !blocked && !vm.discarding, colors = liquidDialogCancelButtonColors())
        }
    )
}
