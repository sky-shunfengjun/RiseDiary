/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.*
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.lock.AppLockScreen
import com.risediary.app.ui.lock.LockMode
import com.risediary.app.ui.policy.PolicySheet
import com.risediary.app.ui.settings.SettingsViewModel
import com.risediary.app.ui.theme.RiseDiaryTheme
import com.risediary.app.ui.theme.SystemBarIconOverride
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class OnboardingMode { FIRST_RUN, REVIEW }

@Composable
fun OnboardingScreen(
    mode: OnboardingMode = OnboardingMode.FIRST_RUN,
    onDone: () -> Unit,
    onSecurityVerified: ((String?) -> Unit)? = null,
    onManageAppLock: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val motion = viewModel.motion
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val persistedTheme by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pageActive = LocalPageEffectsActive.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val updatedUi by rememberUpdatedState(ui)
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var notificationsAllowed by remember(context) { mutableStateOf(onboardingNotificationsAllowed(context)) }
    var permissionRequested by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.start() }
    DisposableEffect(viewModel, activity) {
        onDispose {
            // Activity recreation keeps the memory session; leaving this guide releases it.
            if (activity?.isChangingConfigurations != true) viewModel.endSession()
        }
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed) {
                notificationsAllowed = onboardingNotificationsAllowed(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = onboardingNotificationsAllowed(context)
        settingsViewModel.refreshReminderSchedules()
    }
    fun requestNotifications() {
        if (!pageActive || !updatedUi.ready || updatedUi.step != OnboardingStep.NOTIFICATIONS || updatedUi.transitioning || updatedUi.saving) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val canRequest = !permissionRequested || activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } == true
                if (canRequest) {
                    permissionRequested = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openOnboardingSystemPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            } else openOnboardingSystemPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }.onSuccess { viewModel.clearActionFailure() }.onFailure { viewModel.reportActionFailure(OnboardingPermissionAction.NOTIFICATIONS) }
    }
    fun goBack() {
        if (!pageActive) return
        focus.clearFocus()
        keyboard?.hide()
        if (!viewModel.back()) {
            if (mode == OnboardingMode.REVIEW) onDone() else activity?.finish()
        }
    }
    // Welcome in review mode belongs to the normal navigator, retaining its predictive return.
    PageBackHandler(enabled = ui.document == null && (ui.step != OnboardingStep.WELCOME || ui.modalOpen || ui.saving || ui.transitioning)) { goBack() }

    val previewTheme = resolveOnboardingPreviewTheme(ui.themeMode, persistedTheme)
    // Outside the draft theme: leaving an unsaved preview restores the parent's bars.
    SystemBarIconOverride(forceLightIcons = ui.step == OnboardingStep.WELCOME || ui.step == OnboardingStep.COMPLETE)
    RiseDiaryTheme(themeMode = previewTheme) {
        if (ui.lockSetup) {
            AppLockScreen(
                mode = LockMode.CREATE,
                onDone = {
                    settingsViewModel.applyOnboardingLockDefaults()
                    viewModel.closeLockSetup()
                },
                onCancel = viewModel::closeLockSetup,
                onCredentialVerified = viewModel::recordCredential,
            )
        } else {
            val backdrop = rememberLayerBackdrop()
            val scrollStates = List(OnboardingStep.entries.size) { rememberScrollState() }
            val dialogHost = rememberLiquidDialogHostState()
            LaunchedEffect(motion, durationScale) {
                if (durationScale <= 0f) motion.advanceBy(0f, durationScale = 0f)
            }
            OnboardingMotionClock(motion, resumed && pageActive, durationScale)
            fun next() {
                if (!pageActive || !motion.canContinue) return
                focus.clearFocus()
                keyboard?.hide()
                viewModel.next(mode == OnboardingMode.FIRST_RUN,
                    beforeSave = settingsViewModel::awaitOnboardingWrites,
                    onFinished = { onSecurityVerified?.invoke(viewModel.verifiedCredential()); onDone() })
            }
            fun retry() {
                if (!pageActive || ui.saving || ui.transitioning || motion.isTransitioning) return
                if (ui.readError != null) viewModel.retryRead()
                else if (ui.saveError == null && ui.actionError != null) {
                    when (ui.actionError) {
                        OnboardingPermissionAction.NOTIFICATIONS -> requestNotifications()
                        null -> Unit
                    }
                } else next()
            }
            ProvideLiquidDialogHost(dialogHost) {
                Box(Modifier.fillMaxSize()) {
                    // Sample a background sibling; glass dialogs cannot sample their own tree.
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(
                        com.risediary.app.ui.onboarding.original.originalOnboardingBackground()
                    ))
                    NativeOnboardingHost(
                        ui = ui,
                        frame = motion.visualFrame,
                        active = resumed && pageActive,
                        onNext = ::next,
                        onBack = ::goBack,
                        onTransitionSettled = viewModel::transitionSettled,
                        primaryEnabled = motion.canContinue,
                        introCenter = motion.introCenter,
                        onGeometry = { center, button, viewport ->
                            motion.placeViewport(viewport)
                            motion.placeWelcomeButton(button)
                            motion.startIntro(center, animationsEnabled = durationScale > 0f)
                        },
                        content = { step, interactive ->
                            val controlsEnabled = interactive && (step.ordinal < OnboardingStep.PROFILE.ordinal || ui.ready)
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    when (step) {
                                        OnboardingStep.WELCOME -> Unit
                                        OnboardingStep.STATEMENT -> StatementOnboardingPage(ui.acceptedStatement, interactive, scrollStates[step.ordinal], backdrop, viewModel::setAccepted, viewModel::openDocument)
                                        OnboardingStep.PROFILE -> ProfileOnboardingPage(ui.username, controlsEnabled, scrollStates[step.ordinal], viewModel::setUsername)
                                        OnboardingStep.THEME -> ThemeOnboardingPage(ui.themeMode, scrollStates[step.ordinal], viewModel::setTheme, enabled = controlsEnabled)
                                        OnboardingStep.PREDICTION -> RecordingOnboardingPage(ui.predictionMaxTicks.takeIf { ui.ready }, scrollStates[step.ordinal], viewModel::setPrediction, enabled = controlsEnabled)
                                        OnboardingStep.PRIVACY -> PrivacyOnboardingPage(
                                            ui, scrollStates[step.ordinal], backdrop, controlsEnabled,
                                            settingsViewModel.biometricAvailable, viewModel::openLockSetup,
                                            onManageAppLock?.let { manage -> { if (controlsEnabled) manage() } },
                                            { checked -> if (controlsEnabled) settingsViewModel.requestBiometricUnlock(checked, activity) }, viewModel::setVideoHidden,
                                        )
                                        OnboardingStep.NOTIFICATIONS -> NotificationsOnboardingPage(
                                            ui, scrollStates[step.ordinal], backdrop, controlsEnabled, notificationsAllowed,
                                            ::requestNotifications, viewModel::setLiveUpdates,
                                        )
                                        OnboardingStep.COMPLETE -> OnboardingHero(true, scrollStates[step.ordinal], resumed && pageActive)
                                    }
                                }
                                if (step == ui.step && step.ordinal >= OnboardingStep.PROFILE.ordinal) {
                                    val messages = listOfNotNull(ui.readError, ui.saveError,
                                        ui.actionError?.let { stringResource(R.string.oobe_system_action_error) })
                                    if (messages.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 8.dp)) {
                                        messages.forEach { Text(it, style = MiuixTheme.textStyles.body2,
                                            color = originalOnboardingText()) }
                                        OnboardingAction(stringResource(R.string.action_retry), AppIcons.Refresh, backdrop, ::retry,
                                            enabled = interactive, modifier = Modifier.padding(top = 8.dp))
                                    } else if (ui.loading) Text(stringResource(R.string.oobe_loading), style = MiuixTheme.textStyles.body2,
                                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 8.dp), color = originalOnboardingText())
                                }
                            }
                        },
                    )
                    LiquidDialogHost(state = dialogHost, backdrop = backdrop)
                    PolicySheet(ui.document, viewModel::closeDocument)
                }
            }
        }
    }
}

internal fun onboardingNotificationsAllowed(context: Context): Boolean = runCatching {
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
}.getOrDefault(false)

internal fun openOnboardingSystemPage(context: Context, intent: Intent) {
    try { context.startActivity(intent) }
    catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
}
