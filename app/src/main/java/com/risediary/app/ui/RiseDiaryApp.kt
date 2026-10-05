package com.risediary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.about.AboutScreen
import com.risediary.app.ui.about.ThirdPartyLibsScreen
import com.risediary.app.ui.achievement.AchievementWallScreen
import com.risediary.app.ui.backup.BackupRestoreScreen
import com.risediary.app.ui.components.springAnimateToPage
import com.risediary.app.ui.components.LiquidGlassBottomBar
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.HyperIslandNavigationMotion
import com.risediary.app.ui.components.StableNavigationBackHost
import com.risediary.app.ui.components.PageBackScope
import com.risediary.app.ui.components.LocalPageBackEnabled
import com.risediary.app.ui.components.PageTransitionLayer
import com.risediary.app.ui.components.rememberRootBottomBarProgress
import com.risediary.app.ui.components.LiquidDialogHost
import com.risediary.app.ui.components.LiquidSnackbarHost
import com.risediary.app.ui.components.ProvideLiquidDialogHost
import com.risediary.app.ui.components.ProvidePageBackdrop
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.rememberLiquidDialogHostState
import com.risediary.app.ui.form.RecordFormScreen
import com.risediary.app.ui.home.HomeScreen
import com.risediary.app.ui.length.LengthHistoryScreen
import com.risediary.app.ui.lock.AppLockScreen
import com.risediary.app.ui.lock.LockMode
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.navigation3.rememberNavigator
import com.risediary.app.ui.onboarding.OnboardingScreen
import com.risediary.app.ui.onboarding.OnboardingMode
import com.risediary.app.ui.records.RecordsScreen
import com.risediary.app.ui.records.RecordDetailScreen
import com.risediary.app.ui.records.RecordsViewModel
import com.risediary.app.ui.components.showLiquidSnackbar
import com.risediary.app.ui.components.LiquidSnackbarTone
import androidx.compose.material3.SnackbarResult
import com.risediary.app.ui.settings.CardOrderScreen
import com.risediary.app.ui.settings.AppLockSettingsScreen
import com.risediary.app.ui.settings.SettingsScreen
import com.risediary.app.ui.settings.ReminderSettingsScreen
import com.risediary.app.ui.tags.TagManagerScreen
import com.risediary.app.ui.timer.TimerScreen
import com.risediary.app.ui.timer.TimerCoordinatorViewModel
import com.risediary.app.ui.update.UpdateSheetHost
import com.risediary.app.ui.update.UpdateSheetBackdrop
import com.risediary.app.ui.update.UpdateSheetPresentation
import com.risediary.app.update.UpdateViewModel
import com.risediary.app.service.TimerStatus
import com.risediary.app.service.TimerNotificationEntry
import com.risediary.app.service.resolveTimerNotificationRoute
import com.risediary.app.reminder.NotificationDestination
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * MainPagerState 改编自 KernelSU Manager（GPL-3.0-only）：
 * 底部胶囊与主页面 HorizontalPager 的双向联动，翻页使用与 KernelSU 相同的弹簧动画。
 */
val LocalMainPagerState = staticCompositionLocalOf<MainPagerState?> { null }

class MainPagerState(
    val pagerState: androidx.compose.foundation.pager.PagerState,
    private val coroutineScope: CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun RiseDiaryApp(
    viewModel: AppGateViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    notificationDestination: StateFlow<NotificationDestination?>,
    timerNotificationEntry: StateFlow<TimerNotificationEntry?>,
    onTimerNotificationConsumed: (TimerNotificationEntry) -> Unit,
    onNotificationDestinationConsumed: (NotificationDestination) -> Unit
) {
    val noticeContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.dataWriteNotices.collect { message ->
            android.widget.Toast.makeText(noticeContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val retainMain by viewModel.retainMainContent.collectAsStateWithLifecycle()
    val maintenanceState by viewModel.maintenanceState.collectAsStateWithLifecycle()
    val requestedDestination by
        notificationDestination.collectAsStateWithLifecycle()
    val requestedTimerEntry by timerNotificationEntry.collectAsStateWithLifecycle()

    when {
        keepsMainContentMounted(appState) || (retainMain && appState == AppGateState.ERROR) -> {
            val locked = appState != AppGateState.MAIN
            Box(modifier = Modifier.fillMaxSize()) {
                MainAppContent(
                    interactionsBlocked = locked,
                    notificationDestination = requestedDestination,
                    timerNotificationEntry = requestedTimerEntry,
                    onTimerNotificationConsumed = onTimerNotificationConsumed,
                    onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                    updateViewModel = updateViewModel
                )
                if (!locked && maintenanceState != com.risediary.app.data.DataMaintenanceGate.State.IDLE) {
                    Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)
                        .background(MiuixTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
                        Text(stringResource(if (maintenanceState == com.risediary.app.data.DataMaintenanceGate.State.RECOVERY_REQUIRED)
                            R.string.data_recovery_required else R.string.data_maintenance_readonly), Modifier.padding(12.dp))
                    }
                }
                if (appState == AppGateState.ERROR) {
                    AppGateReadError(viewModel::retryRead)
                } else if (locked) {
                    AppLockScreen(
                        mode = LockMode.VERIFY,
                        onDone = {},
                        onCredentialVerified = viewModel::onCredentialVerified
                    )
                }
            }
        }
        appState == AppGateState.LOADING -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush()),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        appState == AppGateState.ERROR -> AppGateReadError(viewModel::retryRead)
        else -> {
            OnboardingScreen(
                mode = OnboardingMode.FIRST_RUN,
                onDone = {},
                onSecurityVerified = viewModel::onOnboardingFinished
            )
        }
    }
}

@Composable
private fun AppGateReadError(onRetry: () -> Unit, recovery: com.risediary.app.ui.backup.BackupViewModel = hiltViewModel()) {
    val maintenance by recovery.maintenanceState.collectAsStateWithLifecycle()
    val operation by recovery.state.collectAsStateWithLifecycle()
    var waitingForRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(maintenance, waitingForRecovery) {
        if (waitingForRecovery && maintenance == com.risediary.app.data.DataMaintenanceGate.State.IDLE) {
            waitingForRecovery = false
            onRetry()
        }
    }
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_lock_error_read))
            Spacer(Modifier.height(16.dp))
            if (maintenance == com.risediary.app.data.DataMaintenanceGate.State.RECOVERY_REQUIRED) {
                Text(stringResource(R.string.data_recovery_required))
                TextButton(text = stringResource(R.string.data_retry_recovery),
                    enabled = operation != com.risediary.app.ui.backup.BackupState.WORKING,
                    onClick = { waitingForRecovery = true; recovery.retryRecovery() })
            } else TextButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}
// Covered entries can remain STARTED in miuix-nav. Gate effects by transition
// visibility so both pages keep their blur during a swipe, then stop offscreen.
private inline fun <reified T : Route> NavEntryBuilder.pageEntry(
    motion: HyperIslandNavigationMotion,
    noinline overlay: @Composable BoxScope.() -> Unit = {},
    noinline content: @Composable (T) -> Unit,
) {
    entry<T> { route ->
        val transition = LocalNavTransitionScope.current
        val visible by remember(transition) {
            derivedStateOf { transition.relativeDepth > -1f && transition.relativeDepth < 1f }
        }
        CompositionLocalProvider(
            LocalPageEffectsActive provides (LocalPageEffectsActive.current && visible),
        ) {
            val blocked = route != LocalNavigator.current.current()
            val atFront by remember(transition) {
                derivedStateOf { transition.relativeDepth <= 0f }
            }
            PageBackScope(enabled = !blocked && atFront && LocalPageEffectsActive.current) {
                PageTransitionLayer(route, motion, overlay = overlay) {
                    Box(Modifier.fillMaxSize().blockInteractionsAndAccessibility(blocked)) {
                        content(route)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAppContent(
    interactionsBlocked: Boolean,
    notificationDestination: NotificationDestination?,
    timerNotificationEntry: TimerNotificationEntry?,
    onTimerNotificationConsumed: (TimerNotificationEntry) -> Unit,
    onNotificationDestinationConsumed: (NotificationDestination) -> Unit,
    timerCoordinator: TimerCoordinatorViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel
) {
    val navigator = rememberNavigator(Route.Main)
    remember(navigator) { navigator.discardExpiredForms(timerCoordinator::isFormLive); true }
    val timerSession by timerCoordinator.session.collectAsStateWithLifecycle()
    val timerRestorationReady by timerCoordinator.restorationReady.collectAsStateWithLifecycle()
    val timerPersistenceError by timerCoordinator.persistenceError.collectAsStateWithLifecycle()
    val timerNoticeContext = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pagerCoroutineScope = rememberCoroutineScope()
    val recordsViewModel: RecordsViewModel = hiltViewModel()
    val deletedMessage = stringResource(R.string.records_deleted)
    val undoAction = stringResource(R.string.action_undo)
    fun showDetailDeletionUndo(target: com.risediary.app.data.entity.Flight) {
        if (recordsViewModel.pendingDeletions.value.none { it.flight == target }) return
        pagerCoroutineScope.launch {
            val result = snackbarHostState.showLiquidSnackbar(deletedMessage, undoAction, LiquidSnackbarTone.UNDO)
            if (result == SnackbarResult.ActionPerformed) recordsViewModel.undoDelete(target)
            else recordsViewModel.finalizeDeletion(target.id)
        }
    }
    val mainPagerState = remember(pagerState) {
        MainPagerState(pagerState, pagerCoroutineScope)
    }
    val appBackground = backgroundBrush()
    val systemCornerRadius = rememberNavSystemCornerRadius()
    val navigationCornerRadius = if (systemCornerRadius > 0.dp) systemCornerRadius else 24.dp
    val currentAppBackground by rememberUpdatedState(appBackground)
    val backdrop = rememberLayerBackdrop {
        drawRect(brush = currentAppBackground)
        drawContent()
    }
    val mainSceneBackdrop = rememberLayerBackdrop {
        drawRect(brush = currentAppBackground)
        drawContent()
    }
    val liquidDialogHostState = rememberLiquidDialogHostState()
    val updateSheetPresentation = remember { UpdateSheetPresentation() }

    val currentKey = navigator.current()
    val isMain = currentKey is Route.Main
    val navigationKeys = navigator.backStack.map { it as Route }
    val navigationMotion = remember { HyperIslandNavigationMotion(navigationKeys) }
    val navigationTransition = remember(navigationMotion, navigationKeys) {
        navigationMotion.updateStack(navigationKeys)
    }

    val bottomBarProgress = rememberRootBottomBarProgress(navigationMotion, isMain)

    val returningToRoot by remember(navigationMotion, navigationKeys) {
        derivedStateOf { isMain && !navigationMotion.rootPageReady }
    }

    LaunchedEffect(timerNotificationEntry, interactionsBlocked, timerRestorationReady,
        timerSession.sessionId, timerSession.status, timerPersistenceError) {
        val entry = timerNotificationEntry ?: return@LaunchedEffect
        if (interactionsBlocked || !timerRestorationReady) return@LaunchedEffect
        if (timerPersistenceError && timerSession.sessionId != entry.sessionId) {
            // Keep the request while the existing timer page offers recovery retry.
            navigator.push(Route.Timer)
            return@LaunchedEffect
        }
        val route = resolveTimerNotificationRoute(entry, timerSession, locked = false)
        if (route != null) navigator.push(route)
        else android.widget.Toast.makeText(timerNoticeContext, R.string.notification_timer_expired,
            android.widget.Toast.LENGTH_SHORT).show()
        onTimerNotificationConsumed(entry)
    }

    LaunchedEffect(notificationDestination, interactionsBlocked) {
        val destination = notificationDestination ?: return@LaunchedEffect
        if (interactionsBlocked) return@LaunchedEffect
        when (destination) {
            NotificationDestination.RECORDS -> {
                navigator.popUntil { it is Route.Main }
                mainPagerState.animateToPage(1)
            }
            NotificationDestination.LENGTH_HISTORY -> {
                navigator.push(Route.LengthHistory)
            }
        }
        onNotificationDestinationConsumed(destination)
    }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalMainPagerState provides mainPagerState,
        LocalPageEffectsActive provides !interactionsBlocked
    ) {
        ProvidePageBackdrop(backdrop) {
            ProvideLiquidDialogHost(liquidDialogHostState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appBackground)
                        .blockInteractionsAndAccessibility(interactionsBlocked)
                ) {
                    UpdateSheetBackdrop(updateSheetPresentation, interactionsBlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                        ) {
                            StableNavigationBackHost {
                                NavDisplay(
                                    backStack = navigator.backStack,
                                    transition = navigationTransition,
                                    effects = NavDisplayEffects(
                                        cornerClipRadius = navigationCornerRadius,
                                        cornerClipMode = NavCornerClipMode.All,
                                        dimAmount = 0f, // PageTransitionLayer supplies the theme-colored mask.
                                    ),
                                    onBack = {
                                        if (!interactionsBlocked) navigator.pop()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    content = {
                                        pageEntry<Route.Main>(
                                            navigationMotion,
                                            overlay = {
                                                // Keep the bar clear while it sinks, like HyperIsland.
                                                // It stays in Main's entry, beneath secondary pages.
                                                LiquidGlassBottomBar(
                                                    selectedTabIndex = mainPagerState.selectedPage,
                                                    onTabSelected = { index ->
                                                        if (isMain && !returningToRoot && !interactionsBlocked) mainPagerState.animateToPage(index)
                                                    },
                                                    onFlightClick = {
                                                        if (isMain && !returningToRoot && !interactionsBlocked) navigator.push(Route.ModeSelect)
                                                    },
                                                    backdrop = mainSceneBackdrop,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .graphicsLayer {
                                                            val progress = bottomBarProgress()
                                                            translationY = size.height * progress
                                                            alpha = 1f - progress
                                                        }
                                                        .blockInteractionsAndAccessibility(!isMain || returningToRoot || interactionsBlocked)
                                                        .navigationBarsPadding()
                                                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                                                )
                                            },
                                        ) {
                                            MainScene(
                                                pagerState = pagerState,
                                                mainPagerState = mainPagerState,
                                                sceneBackdrop = mainSceneBackdrop,
                                                snackbarHostState = snackbarHostState,
                                                snackbarScope = pagerCoroutineScope,
                                                recordsViewModel = recordsViewModel
                                            )
                                        }
                                        pageEntry<Route.ModeSelect>(navigationMotion) { ModeSelectScreen() }
                                        pageEntry<Route.Timer>(navigationMotion) { TimerScreen() }
                                        pageEntry<Route.VideoTimer>(navigationMotion) { route ->
                                            com.risediary.app.ui.video.VideoTimerScreen(route)
                                        }
                                        pageEntry<Route.RecordForm>(navigationMotion) { route ->
                                            RecordFormScreen(
                                                isTimer = route.isTimer,
                                                durationMillis = route.duration,
                                                timerStartTimeMillis = route.startTime,
                                                formSessionId = route.formSessionId,
                                                flightId = null
                                            )
                                        }
                                        pageEntry<Route.RecordVideo>(navigationMotion) { route ->
                                            com.risediary.app.ui.video.VideoPlayerScreen(route)
                                        }
                                        pageEntry<Route.VideoPreview>(navigationMotion) { route ->
                                            com.risediary.app.ui.video.VideoPlayerScreen(route)
                                        }
                                        pageEntry<Route.RecordDetail>(navigationMotion) { route ->
                                            RecordDetailScreen(flightId = route.flightId,
                                                deleteForUndo = recordsViewModel::deleteForUndo,
                                                onRecordDeleted = ::showDetailDeletionUndo)
                                        }
                                        pageEntry<Route.RecordEdit>(navigationMotion) { route ->
                                            RecordFormScreen(
                                                isTimer = false,
                                                durationMillis = 0L,
                                                timerStartTimeMillis = 0L,
                                                flightId = route.flightId
                                            )
                                        }
                                        pageEntry<Route.TagManager>(navigationMotion) { TagManagerScreen() }
                                        pageEntry<Route.AchievementWall>(navigationMotion) { AchievementWallScreen() }
                                        pageEntry<Route.About>(navigationMotion) { AboutScreen(updateViewModel = updateViewModel) }
                                        pageEntry<Route.ThirdPartyLibs>(navigationMotion) { ThirdPartyLibsScreen() }
                                        pageEntry<Route.CardOrder>(navigationMotion) { CardOrderScreen() }
                                        pageEntry<Route.BackupRestore>(navigationMotion) {
                                            BackupRestoreScreen(snackbarHostState = snackbarHostState)
                                        }
                                        pageEntry<Route.LengthHistory>(navigationMotion) { LengthHistoryScreen() }
                                        pageEntry<Route.ReminderSettings>(navigationMotion) { ReminderSettingsScreen() }
                                        pageEntry<Route.AppLockSettings>(navigationMotion) { AppLockSettingsScreen() }
                                        pageEntry<Route.LockSetup>(navigationMotion) {
                                            AppLockScreen(
                                                mode = LockMode.CREATE,
                                                onDone = { navigator.pop() },
                                                onCancel = { navigator.pop() }
                                            )
                                        }
                                        pageEntry<Route.LockChange>(navigationMotion) {
                                            AppLockScreen(
                                                mode = LockMode.CHANGE_OLD,
                                                onDone = { navigator.pop() },
                                                onCancel = { navigator.pop() }
                                            )
                                        }
                                        pageEntry<Route.LockDisable>(navigationMotion) {
                                            AppLockScreen(
                                                mode = LockMode.DISABLE_VERIFY,
                                                onDone = { navigator.pop() },
                                                onCancel = { navigator.pop() }
                                            )
                                        }
                                        pageEntry<Route.OnboardingReview>(navigationMotion) {
                                            OnboardingScreen(
                                                mode = OnboardingMode.REVIEW,
                                                onDone = { navigator.pop() },
                                                onManageAppLock = {
                                                    navigator.push(Route.AppLockSettings)
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        // Keep the current entry and its draft while the lock overlay
                        // is open, including screens with their own save-on-back handler.
                        BackHandler(enabled = interactionsBlocked || returningToRoot) { }
                        LiquidSnackbarHost(
                            hostState = snackbarHostState,
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    bottom = if (isMain) 84.dp else 20.dp
                                )
                        )
                        LiquidDialogHost(
                            state = liquidDialogHostState,
                            backdrop = backdrop
                        )
                    }
                    // miuix popups (OverlayListPopup/OverlayDropdownPreference) register
                    // their state into the popup host; without it the popup never renders
                    // and the triggering row stays stuck in its pressed state.
                    MiuixPopupUtils.MiuixPopupHost()
                    UpdateSheetHost(updateViewModel, interactionsBlocked, updateSheetPresentation)
                }
            }
        }
    }
}

/**
 * 主界面场景：三页 Pager。根布局管理底栏状态，底栏只在 Main 导航层绘制一次。
 */
@Composable
private fun MainScene(
    pagerState: androidx.compose.foundation.pager.PagerState,
    mainPagerState: MainPagerState,
    sceneBackdrop: LayerBackdrop,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    snackbarScope: CoroutineScope,
    recordsViewModel: RecordsViewModel
) {
    val navigator = LocalNavigator.current
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { mainPagerState.syncPage() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sceneBackdrop)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val effectsActive = LocalPageEffectsActive.current && pagerState.currentPage == page
                CompositionLocalProvider(LocalPageEffectsActive provides effectsActive) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (page) {
                            0 -> HomeScreen()
                            1 -> RecordsScreen(
                                viewModel = recordsViewModel,
                                snackbarHostState = snackbarHostState,
                                snackbarScope = snackbarScope
                            )
                            else -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    val isPagerBackEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                navigator.backStackSize() == 1 &&
                mainPagerState.selectedPage != 0
        }
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackEnabled && LocalPageBackEnabled.current,
        onBackCompleted = {
            mainPagerState.animateToPage(0)
        }
    )
}

private fun Modifier.blockInteractionsAndAccessibility(blocked: Boolean): Modifier {
    if (!blocked) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }.clearAndSetSemantics { }
}
