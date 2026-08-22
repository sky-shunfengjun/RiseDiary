package com.risediary.app.ui.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
fun AppLockSettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val lockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricUnlockEnabled.collectAsStateWithLifecycle()
    val backgroundAutoLockEnabled by
        vm.backgroundAutoLockEnabled.collectAsStateWithLifecycle()
    val backgroundLockMode by vm.backgroundLockMode.collectAsStateWithLifecycle()
    var showDisableDialog by remember { mutableStateOf(false) }

    SecondaryPageScaffold(
        title = stringResource(R.string.settings_app_lock),
        onBack = { navController.popBackStack() }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroupHeader(stringResource(R.string.settings_app_lock_access_group))
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_app_lock),
                    subtitle =
                        if (lockEnabled) stringResource(R.string.settings_app_lock_on)
                        else stringResource(R.string.settings_app_lock_off),
                    checked = lockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled != lockEnabled) {
                            if (enabled) {
                                navController.navigate("lock_setup")
                            } else {
                                showDisableDialog = true
                            }
                        }
                    }
                )
                if (lockEnabled) {
                    SettingsDivider()
                    SettingsNavItem(
                        icon = Icons.Default.LockReset,
                        title = stringResource(R.string.settings_change_pin),
                        subtitle = stringResource(R.string.settings_change_pin_summary),
                        onClick = { navController.navigate("lock_change") }
                    )
                    SettingsDivider()
                    val biometricAvailable = vm.biometricAvailable
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_biometric_unlock),
                        subtitle = when {
                            !biometricAvailable ->
                                stringResource(R.string.settings_biometric_unavailable)
                            biometricEnabled ->
                                stringResource(R.string.settings_biometric_on)
                            else ->
                                stringResource(R.string.settings_biometric_off)
                        },
                        checked = biometricEnabled,
                        enabled = biometricAvailable,
                        onCheckedChange = { enabled ->
                            vm.requestBiometricUnlock(enabled, activity)
                        }
                    )
                }
            }

            if (lockEnabled) {
                SettingsGroupHeader(stringResource(R.string.settings_app_lock_background_group))
                RiseCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsToggleItem(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.settings_background_auto_lock),
                        subtitle =
                            if (backgroundAutoLockEnabled) {
                                stringResource(R.string.settings_background_auto_lock_on)
                            } else {
                                stringResource(R.string.settings_background_auto_lock_off)
                            },
                        checked = backgroundAutoLockEnabled,
                        onCheckedChange = vm::setBackgroundAutoLockEnabled
                    )
                    if (backgroundAutoLockEnabled) {
                        SettingsDivider()
                        SettingsBackgroundLockModeItem(
                            value = backgroundLockMode,
                            onSelect = vm::setBackgroundLockMode
                        )
                    }
                }
            }
        }
    }

    if (showDisableDialog) {
        LiquidAlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text(stringResource(R.string.settings_app_lock_disable_title)) },
            text = { Text(stringResource(R.string.settings_app_lock_disable_message)) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.settings_app_lock_continue_verify),
                    onClick = {
                        showDisableDialog = false
                        navController.navigate("lock_disable")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        disabledColor = androidx.compose.ui.graphics.Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledTextColor = MiuixTheme.colorScheme.error
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDisableDialog = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}
