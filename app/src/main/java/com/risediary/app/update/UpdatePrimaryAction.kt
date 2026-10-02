package com.risediary.app.update

internal enum class UpdatePrimaryAction { DISABLED, CHECK, DOWNLOAD, CANCEL, INSTALL }

internal fun primaryUpdateAction(ui: UpdateUiState): UpdatePrimaryAction = when (ui.download) {
    DownloadState.Starting, is DownloadState.Verifying -> UpdatePrimaryAction.DISABLED
    is DownloadState.Running, is DownloadState.Paused -> UpdatePrimaryAction.CANCEL
    is DownloadState.Ready -> UpdatePrimaryAction.INSTALL
    is DownloadState.Failed -> if (ui.download.record != null) UpdatePrimaryAction.DOWNLOAD else if (ui.release != null && ui.check is UpdateCheckState.Available &&
        selectReleaseApk(ui.release) != null) UpdatePrimaryAction.DOWNLOAD else
        if (ui.check == UpdateCheckState.Checking) UpdatePrimaryAction.DISABLED else UpdatePrimaryAction.CHECK
    DownloadState.Idle -> when (ui.check) {
        UpdateCheckState.Checking -> UpdatePrimaryAction.DISABLED
        is UpdateCheckState.Available -> if (selectReleaseApk(ui.check.release) != null)
            UpdatePrimaryAction.DOWNLOAD else UpdatePrimaryAction.DISABLED
        else -> UpdatePrimaryAction.CHECK
    }
}
