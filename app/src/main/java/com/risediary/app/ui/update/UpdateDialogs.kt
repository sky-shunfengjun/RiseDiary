package com.risediary.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.risediary.app.R
import com.risediary.app.update.GitHubRelease
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
internal fun UpdateAvailableDialog(
    currentVersion: String,
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    LiquidAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.SystemUpdate,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_available_message,
                    currentVersion,
                    release.tagName
                )
            )
        },
        confirmButton = {
            TextButton(
                text = stringResource(R.string.update_open_release),
                onClick = { onOpenRelease(release.releaseUrl) },
                colors = liquidDialogConfirmButtonColors()
            )
        },
        dismissButton = {
            TextButton(
                text = stringResource(R.string.update_later),
                onClick = onDismiss,
                colors = liquidDialogCancelButtonColors()
            )
        }
    )
}

@Composable
internal fun UpdateStatusDialog(
    currentVersion: String,
    state: UpdateStatusDialogState,
    onDismiss: () -> Unit
) {
    val (title, message, icon) = when (state) {
        UpdateStatusDialogState.UP_TO_DATE -> Triple(
            R.string.update_latest_title,
            R.string.update_latest_message,
            AppIcons.SystemUpdate
        )
        UpdateStatusDialogState.FAILED -> Triple(
            R.string.update_failed_title,
            R.string.update_failed_message,
            AppIcons.Info
        )
    }
    LiquidAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(title)) },
        text = {
            Text(
                if (state == UpdateStatusDialogState.UP_TO_DATE) {
                    stringResource(message, currentVersion)
                } else {
                    stringResource(message)
                }
            )
        },
        confirmButton = {
            TextButton(
                text = stringResource(R.string.action_confirm),
                onClick = onDismiss,
                colors = liquidDialogConfirmButtonColors()
            )
        }
    )
}

internal enum class UpdateStatusDialogState {
    UP_TO_DATE,
    FAILED,
}
