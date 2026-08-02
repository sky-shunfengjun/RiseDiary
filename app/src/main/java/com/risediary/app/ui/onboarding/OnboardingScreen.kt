package com.risediary.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.risediary.app.R
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.reminder.ReminderType
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.LiquidDialogHost
import com.risediary.app.ui.components.ProvideLiquidDialogHost
import com.risediary.app.ui.components.WheelColumn
import com.risediary.app.ui.components.rememberLiquidDialogHostState
import com.risediary.app.ui.lock.AppLockScreen
import com.risediary.app.ui.lock.LockMode
import com.risediary.app.ui.settings.SettingsViewModel
import com.risediary.app.ui.settings.openExactAlarmSettings
import com.risediary.app.ui.settings.openReminderNotificationSettings
import com.risediary.app.ui.settings.reminderNotificationsAvailable
import com.risediary.app.ui.theme.RiseDiaryTheme
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.launch

enum class OnboardingMode {
    FIRST_RUN,
    REVIEW
}

private const val WELCOME_PAGE = 0
private const val PROFILE_PAGE = 1
private const val THEME_PAGE = 2
private const val RECORDING_PAGE = 3
private const val PRIVACY_PAGE = 4

@Composable
fun OnboardingScreen(
    mode: OnboardingMode = OnboardingMode.FIRST_RUN,
    onDone: () -> Unit,
    onManageAppLock: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val systemDark = isSystemInDarkTheme()

    val persistedUsername by settingsViewModel.username.collectAsStateWithLifecycle()
    val persistedTheme by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val persistedVolumeMode by
        settingsViewModel.defaultVolumeMode.collectAsStateWithLifecycle()
    val persistedMlPerSpurt by settingsViewModel.mlPerSpurt.collectAsStateWithLifecycle()
    val appLockEnabled by settingsViewModel.appLockEnabled.collectAsStateWithLifecycle()
    val biometricEnabled by
        settingsViewModel.biometricUnlockEnabled.collectAsStateWithLifecycle()
    val dailyReminderEnabled by
        settingsViewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val inactiveReminderEnabled by
        settingsViewModel.inactiveReminderEnabled.collectAsStateWithLifecycle()
    val dailyReminderTime by
        settingsViewModel.dailyReminderTime.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableIntStateOf(WELCOME_PAGE) }
    var usernameDraft by rememberSaveable { mutableStateOf(persistedUsername) }
    var usernameDirty by rememberSaveable { mutableStateOf(false) }
    var themeDraft by rememberSaveable { mutableStateOf(persistedTheme) }
    var themeDirty by rememberSaveable { mutableStateOf(false) }
    var volumeModeDraft by rememberSaveable { mutableStateOf(persistedVolumeMode) }
    var mlPerSpurtDraft by rememberSaveable { mutableFloatStateOf(persistedMlPerSpurt) }
    var recordingDirty by rememberSaveable { mutableStateOf(false) }
    var reminderTimeDraft by rememberSaveable { mutableStateOf(dailyReminderTime) }
    var reminderTimeDirty by rememberSaveable { mutableStateOf(false) }

    var showLockSetup by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showNotificationBlockedDialog by rememberSaveable { mutableStateOf(false) }
    var showExactAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    val onboardingScope = rememberCoroutineScope()

    LaunchedEffect(persistedUsername) {
        if (!usernameDirty) usernameDraft = persistedUsername
    }
    LaunchedEffect(persistedTheme) {
        if (!themeDirty) themeDraft = persistedTheme
    }
    LaunchedEffect(persistedVolumeMode, persistedMlPerSpurt) {
        if (!recordingDirty) {
            volumeModeDraft = persistedVolumeMode
            mlPerSpurtDraft = persistedMlPerSpurt
        }
    }
    LaunchedEffect(dailyReminderTime) {
        if (!reminderTimeDirty) reminderTimeDraft = dailyReminderTime
    }

    val enableRecommendedReminders = {
        settingsViewModel.applyRecommendedReminders(true, reminderTimeDraft)
        if (!settingsViewModel.exactAlarmsAllowed()) showExactAlarmDialog = true
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && reminderNotificationsAvailable(context)) {
            enableRecommendedReminders()
        } else {
            showNotificationBlockedDialog = true
        }
    }

    fun requestRecommendedReminders(enabled: Boolean) {
        if (!enabled) {
            settingsViewModel.applyRecommendedReminders(false, reminderTimeDraft)
            return
        }
        val runtimePermissionMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        when {
            runtimePermissionMissing ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            reminderNotificationsAvailable(context) -> enableRecommendedReminders()
            else -> showNotificationBlockedDialog = true
        }
    }

    fun finish(saveReminderTime: Boolean) {
        if (finishing) return
        finishing = true
        onboardingScope.launch {
            settingsViewModel.awaitOnboardingWrites()
            viewModel.finish(
                firstRun = mode == OnboardingMode.FIRST_RUN,
                reminderTime = reminderTimeDraft.takeIf { saveReminderTime }
            )
            onDone()
        }
    }

    BackHandler(enabled = page > WELCOME_PAGE || mode == OnboardingMode.REVIEW) {
        if (page > WELCOME_PAGE) page-- else onDone()
    }

    if (showLockSetup) {
        AppLockScreen(
            mode = LockMode.CREATE,
            onDone = {
                settingsViewModel.applyOnboardingLockDefaults()
                showLockSetup = false
                if (settingsViewModel.biometricAvailable) {
                    settingsViewModel.requestBiometricUnlock(true, activity)
                }
            },
            onCancel = { showLockSetup = false }
        )
        return
    }

    // The draft is the source of truth while the flow is open. Persisted settings may
    // still be catching up after saving the theme, which previously caused a brief light
    // flash on the following pages.
    val previewTheme = resolveOnboardingPreviewTheme(themeDraft, persistedTheme)
    val previewDark = when (previewTheme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    RiseDiaryTheme(darkTheme = previewDark) {
        val backdrop = rememberLayerBackdrop()
        val dialogHostState = rememberLiquidDialogHostState()
        ProvideLiquidDialogHost(dialogHostState) {
            Box(modifier = Modifier.fillMaxSize()) {
                CockpitBackdrop(modifier = Modifier.layerBackdrop(backdrop))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (mode == OnboardingMode.FIRST_RUN) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier
                        }
                    )
                    .imePadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (page > WELCOME_PAGE) {
                    SetupProgressHeader(
                        step = page,
                        backdrop = backdrop,
                        onBack = { page-- }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 4 } + fadeOut())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "onboarding_page"
                ) { currentPage ->
                    when (currentPage) {
                        WELCOME_PAGE -> WelcomeOnboardingPage()
                        PROFILE_PAGE -> ProfileOnboardingPage(
                            username = usernameDraft,
                            onUsernameChange = {
                                usernameDirty = true
                                usernameDraft = it.take(40)
                            }
                        )
                        THEME_PAGE -> ThemeOnboardingPage(
                            themeMode = themeDraft,
                            backdrop = backdrop,
                            onThemeSelected = {
                                themeDirty = true
                                themeDraft = it
                            }
                        )
                        RECORDING_PAGE -> RecordingOnboardingPage(
                            volumeMode = volumeModeDraft,
                            mlPerSpurt = mlPerSpurtDraft,
                            backdrop = backdrop,
                            onVolumeModeSelected = {
                                recordingDirty = true
                                volumeModeDraft = it
                            },
                            onMlPerSpurtChange = {
                                recordingDirty = true
                                mlPerSpurtDraft = it
                            }
                        )
                        else -> PrivacyOnboardingPage(
                            appLockEnabled = appLockEnabled,
                            biometricAvailable = settingsViewModel.biometricAvailable,
                            biometricEnabled = biometricEnabled,
                            recommendedRemindersEnabled =
                                dailyReminderEnabled && inactiveReminderEnabled,
                            reminderTime = reminderTimeDraft,
                            backdrop = backdrop,
                            onCreateAppLock = { showLockSetup = true },
                            onManageAppLock = onManageAppLock,
                            onEnableBiometric = {
                                settingsViewModel.requestBiometricUnlock(true, activity)
                            },
                            onRecommendedRemindersChange = ::requestRecommendedReminders,
                            onEditReminderTime = { showTimePicker = true }
                        )
                    }
                }

                when (page) {
                    WELCOME_PAGE -> WelcomeAction(
                        backdrop = backdrop,
                        onClick = { page = PROFILE_PAGE }
                    )
                    PROFILE_PAGE -> SetupBottomActions(
                        backdrop = backdrop,
                        onLater = {
                            usernameDraft = persistedUsername
                            usernameDirty = false
                            page = THEME_PAGE
                        },
                        onContinue = {
                            viewModel.saveProfile(usernameDraft) {
                                usernameDirty = false
                                page = THEME_PAGE
                            }
                        }
                    )
                    THEME_PAGE -> SetupBottomActions(
                        backdrop = backdrop,
                        onLater = {
                            themeDraft = persistedTheme
                            themeDirty = false
                            page = RECORDING_PAGE
                        },
                        onContinue = {
                            viewModel.saveTheme(themeDraft) {
                                themeDirty = false
                                page = RECORDING_PAGE
                            }
                        }
                    )
                    RECORDING_PAGE -> SetupBottomActions(
                        backdrop = backdrop,
                        onLater = {
                            volumeModeDraft = persistedVolumeMode
                            mlPerSpurtDraft = persistedMlPerSpurt
                            recordingDirty = false
                            page = PRIVACY_PAGE
                        },
                        onContinue = {
                            viewModel.saveRecordingPreferences(
                                mode = volumeModeDraft,
                                mlPerSpurt = mlPerSpurtDraft
                            ) {
                                recordingDirty = false
                                page = PRIVACY_PAGE
                            }
                        }
                    )
                    else -> SetupBottomActions(
                        backdrop = backdrop,
                        laterLabel = stringResource(R.string.onboarding_finish_later),
                        continueLabel = stringResource(
                            if (mode == OnboardingMode.FIRST_RUN) {
                                R.string.onboarding_save_and_start
                            } else {
                                R.string.onboarding_done
                            }
                        ),
                        onLater = { finish(saveReminderTime = false) },
                        onContinue = { finish(saveReminderTime = true) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
                if (showTimePicker) {
            val parsed = remember(reminderTimeDraft) {
                runCatching { LocalTime.parse(reminderTimeDraft) }
                    .getOrDefault(LocalTime.of(22, 0))
            }
            var pickedHour by remember(reminderTimeDraft) {
                mutableIntStateOf(parsed.hour)
            }
            var pickedMinute by remember(reminderTimeDraft) {
                mutableIntStateOf(parsed.minute)
            }
            LiquidAlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = {
                    Text(
                        stringResource(R.string.settings_choose_reminder_time),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelColumn(
                            label = stringResource(R.string.time_hour),
                            range = 0..23,
                            value = pickedHour,
                            onValueChange = { pickedHour = it },
                            modifier = Modifier.weight(1f)
                        )
                        WheelColumn(
                            label = stringResource(R.string.time_minute),
                            range = 0..59,
                            value = pickedMinute,
                            onValueChange = { pickedMinute = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            reminderTimeDirty = true
                            reminderTimeDraft = String.format(
                                Locale.ROOT,
                                "%02d:%02d",
                                pickedHour,
                                pickedMinute
                            )
                            if (dailyReminderEnabled && inactiveReminderEnabled) {
                                settingsViewModel.applyRecommendedReminders(
                                    true,
                                    reminderTimeDraft
                                )
                            }
                            showTimePicker = false
                        }
                    ) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
                }

                if (showNotificationBlockedDialog) {
            LiquidAlertDialog(
                onDismissRequest = { showNotificationBlockedDialog = false },
                title = { Text(stringResource(R.string.settings_notification_blocked_title)) },
                text = { Text(stringResource(R.string.settings_notification_blocked_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotificationBlockedDialog = false
                            openReminderNotificationSettings(context)
                        }
                    ) { Text(stringResource(R.string.settings_open_system_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationBlockedDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
                }

                if (showExactAlarmDialog) {
            LiquidAlertDialog(
                onDismissRequest = { showExactAlarmDialog = false },
                title = { Text(stringResource(R.string.settings_exact_alarm_dialog_title)) },
                text = { Text(stringResource(R.string.settings_exact_alarm_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExactAlarmDialog = false
                            openExactAlarmSettings(context)
                        }
                    ) { Text(stringResource(R.string.settings_open_system_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showExactAlarmDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
                }
                LiquidDialogHost(
                    state = dialogHostState,
                    backdrop = backdrop
                )
            }
        }
    }
}
