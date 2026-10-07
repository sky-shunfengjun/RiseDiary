package com.risediary.app.update

internal enum class UpdatePrimaryAction { DISABLED, CHECK, DOWNLOAD, CANCEL, INSTALL }

internal fun primaryUpdateAction(ui: UpdateUiState): UpdatePrimaryAction = when (ui.download) {
    DownloadState.Starting, is DownloadState.Verifying -> UpdatePrimaryAction.DISABLED
    is DownloadState.Running, is DownloadState.Paused -> UpdatePrimaryAction.CANCEL
    is DownloadState.Ready -> if (cachedPackageIsApplicable(ui, ui.download.record)) UpdatePrimaryAction.INSTALL
        else when (val target = resolveUpdateDownloadTarget(ui)) {
            is UpdateDownloadTarget.Authorized -> UpdatePrimaryAction.DOWNLOAD
            is UpdateDownloadTarget.Blocked -> if (target.checking) UpdatePrimaryAction.DISABLED else UpdatePrimaryAction.CHECK
        }
    is DownloadState.Failed, DownloadState.Idle -> when (val target = resolveUpdateDownloadTarget(ui)) {
        is UpdateDownloadTarget.Authorized -> UpdatePrimaryAction.DOWNLOAD
        is UpdateDownloadTarget.Blocked -> if (target.checking) UpdatePrimaryAction.DISABLED else UpdatePrimaryAction.CHECK
    }
}