package com.risediary.app.ui.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.risediary.app.R
import com.risediary.app.update.GitHubRelease
import com.risediary.app.ui.components.LiquidAlertDialog

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
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
            TextButton(onClick = { onOpenRelease(release.releaseUrl) }) {
                Text(stringResource(R.string.update_open_release))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
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
            Icons.Default.SystemUpdate
        )
        UpdateStatusDialogState.FAILED -> Triple(
            R.string.update_failed_title,
            R.string.update_failed_message,
            Icons.Default.Info
        )
    }
    LiquidAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    )
}

internal enum class UpdateStatusDialogState {
    UP_TO_DATE,
    FAILED
}
