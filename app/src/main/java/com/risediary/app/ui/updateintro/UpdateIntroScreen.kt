package com.risediary.app.ui.updateintro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.*
import com.risediary.app.R
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.*
import com.risediary.app.ui.onboarding.original.originalOnboardingBackground
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import com.risediary.app.ui.policy.PolicySheet
import com.risediary.app.ui.theme.SystemBarIconOverride
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateIntroScreen(
    mode: UpdateIntroMode,
    onDone: () -> Unit,
    viewModel: UpdateIntroViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val motion = viewModel.motion
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pageActive = LocalPageEffectsActive.current
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var notificationsAllowed by remember(context) { mutableStateOf(onboardingNotificationsAllowed(context)) }
    var permissionRequested by remember { mutableStateOf(false) }
    val currentUi by rememberUpdatedState(ui)
    val currentDone by rememberUpdatedState(onDone)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(viewModel) { viewModel.start() }
    DisposableEffect(viewModel, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.endSession() }
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed) notificationsAllowed = onboardingNotificationsAllowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = onboardingNotificationsAllowed(context)
    }
    fun requestNotifications() {
        val state = currentUi
        if (!pageActive || !resumed || !state.ready || state.step != UpdateIntroStep.NOTIFICATIONS ||
            state.saving || state.transitioning || state.finished) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val canRequest = !permissionRequested || activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                } == true
                if (canRequest) { permissionRequested = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                else openOnboardingSystemPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            } else openOnboardingSystemPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }.onSuccess { viewModel.clearActionFailure() }.onFailure { viewModel.reportActionFailure() }
    }
    fun goBack() {
        if (!pageActive || !resumed) return
        focus.clearFocus(); keyboard?.hide()
        if (!viewModel.back()) {
            if (mode == UpdateIntroMode.AUTO) activity?.finish() else currentDone()
        }
    }
    // Both system exit and review return own their predictive animation on the first page.
    PageBackHandler(enabled = ui.document == null &&
        (ui.step != UpdateIntroStep.SUCCESS || ui.saving || ui.transitioning)) { goBack() }
    SystemBarIconOverride(forceLightIcons = ui.step == UpdateIntroStep.SUCCESS || ui.step == UpdateIntroStep.COMPLETE)
    GuideMotionClock(motion, resumed && pageActive, durationScale)
    fun next() {
        if (!pageActive || !resumed || !motion.canContinue) return
        focus.clearFocus(); keyboard?.hide()
        viewModel.next(mode) { currentDone() }
    }
    fun retry() {
        if (!pageActive || ui.saving || ui.transitioning || motion.isTransitioning) return
        when {
            ui.readError != null -> viewModel.retryRead()
            ui.saveError != null -> next()
            ui.actionError -> requestNotifications()
        }
    }
    val backdrop = rememberLayerBackdrop()
    val dialogs = rememberLiquidDialogHostState()
    val scrolls = List(UpdateIntroStep.entries.size) { rememberScrollState() }
    val blurThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val spec = remember { GuideSceneSpec(completePage = UpdateIntroStep.COMPLETE.ordinal, welcomeGraphic = GuideWelcomeGraphic.UPDATE_CHECK,
        welcomeTitle = R.string.update_intro_success, welcomeSubtitle = R.string.update_intro_version,
        statementButton = R.string.update_intro_agree, immersiveBody = true) }
    ProvideLiquidDialogHost(dialogs) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(originalOnboardingBackground()))
            NativeGuideHost(
                ui = GuideSceneUiState(ui.saving, ui.transitioning, ui.modalOpen, ui.canContinue),
                frame = motion.visualFrame, active = resumed && pageActive,
                onNext = ::next, onBack = ::goBack,
                onTransitionSettled = { id, index -> UpdateIntroStep.entries.getOrNull(index)?.let { viewModel.transitionSettled(id, it) } },
                primaryEnabled = motion.canContinue, introCenter = motion.introCenter, spec = spec,
                topBlurProgress = { index -> (scrolls[index].value / blurThreshold).coerceIn(0f, 1f) },
                onGeometry = { center, button, viewport ->
                    motion.placeViewport(viewport); motion.placeWelcomeButton(button)
                    motion.startIntro(center, animationsEnabled = durationScale > 0f)
                },
                content = { index, interactive ->
                    val step = UpdateIntroStep.entries[index]
                    val enabled = interactive && ui.ready
                    val status: @Composable () -> Unit = {
                        if (step == ui.step) {
                            val errors = listOfNotNull(ui.readError, ui.saveError,
                                if (ui.actionError) stringResource(R.string.oobe_system_action_error) else null)
                            if (errors.isNotEmpty()) Column(Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                errors.forEach { Text(it, style = MiuixTheme.textStyles.body2, color = originalOnboardingText()) }
                                OnboardingAction(stringResource(R.string.action_retry), AppIcons.Refresh, backdrop,
                                    ::retry, enabled = interactive)
                            } else if (ui.loading) Text(stringResource(R.string.oobe_loading),
                                Modifier.padding(vertical = 8.dp), color = originalOnboardingText())
                        }
                    }
                    when (step) {
                        UpdateIntroStep.SUCCESS -> Unit
                        UpdateIntroStep.STATEMENT -> StatementOnboardingPage(ui.acceptedStatement, interactive,
                            scrolls[index], backdrop, viewModel::setAccepted, viewModel::openDocument, compact = true)
                        UpdateIntroStep.RECORDING_TIMER -> RecordingTimerIntroPage(ui, scrolls[index],
                            enabled, viewModel::setPrediction, status)
                        UpdateIntroStep.NOTIFICATIONS -> NotificationsIntroPage(ui, scrolls[index], backdrop,
                            enabled, notificationsAllowed, ::requestNotifications, viewModel::setLiveUpdates, status)
                        UpdateIntroStep.VIDEO_PRIVACY -> VideoPrivacyIntroPage(ui, scrolls[index],
                            enabled, viewModel::setVideoHidden, status)
                        UpdateIntroStep.COMPLETE -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                GuideHero(null, stringResource(R.string.update_intro_complete), null, scrolls[index])
                            }
                            Column(Modifier.padding(horizontal = 30.dp, vertical = 8.dp)) { status() }
                        }
                    }
                },
            )
            LiquidDialogHost(dialogs, backdrop)
            // A retained reading session cannot draw its overlay above app-lock verification.
            PolicySheet(if (pageActive) ui.document else null, viewModel::closeDocument)
        }
    }
}
