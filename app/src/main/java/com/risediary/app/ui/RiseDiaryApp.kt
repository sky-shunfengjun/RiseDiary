package com.risediary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
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
import androidx.compose.ui.platform.LocalUriHandler
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
import com.risediary.app.ui.settings.CardOrderScreen
import com.risediary.app.ui.settings.AppLockSettingsScreen
import com.risediary.app.ui.settings.SettingsScreen
import com.risediary.app.ui.settings.ReminderSettingsScreen
import com.risediary.app.ui.tags.TagManagerScreen
import com.risediary.app.ui.timer.TimerScreen
import com.risediary.app.ui.timer.TimerCoordinatorViewModel
import com.risediary.app.ui.update.UpdateAvailableDialog
import com.risediary.app.update.UpdateCheckState
import com.risediary.app.update.UpdateViewModel
import com.risediary.app.service.TimerStatus
import com.risediary.app.reminder.NotificationDestination
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import com.risediary.app.ui.icons.AppIcons
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

private val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)

private suspend fun androidx.compose.foundation.pager.PagerState.springAnimateToPage(target: Int) {
    if (target !in 0 until pageCount) return
    var shouldSnapToTarget = false
    scroll(MutatePriority.UserInput) {
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distance * pageSize
        if (abs(scrollPixels) <= 0.5f) return@scroll

        var consumedScroll = 0f
        var skipScroll = false
        Animatable(0f).animateTo(
            targetValue = scrollPixels,
            animationSpec = PagerNavigationSpringSpec,
        ) {
            if (skipScroll) return@animateTo

            val delta = value - consumedScroll
            if (abs(delta) > 0.5f) {
                val consumed = scrollBy(delta)
                consumedScroll += consumed
                if (abs(delta - consumed) > 0.1f) {
                    shouldSnapToTarget = true
                    skipScroll = true
                }
            } else {
                consumedScroll = value
            }

            if (abs(velocity) < 0.1f && abs(scrollPixels - consumedScroll) < 1.0f) {
                skipScroll = true
            }
        }

        val remaining = scrollPixels - consumedScroll
        if (abs(remaining) > 0.5f) {
            scrollBy(remaining)
        }
    }

    if (shouldSnapToTarget || currentPage != target) {
        scrollToPage(target)
    }
}

@Composable
fun RiseDiaryApp(
    viewModel: AppGateViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    notificationDestination: StateFlow<NotificationDestination?>,
    onNotificationDestinationConsumed: (NotificationDestination) -> Unit
) {
    val appState by viewModel.state.collectAsStateWithLifecycle()
    val requestedDestination by
        notificationDestination.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdate()
    }

    when {
        keepsMainContentMounted(appState) -> {
            val locked = appState == AppGateState.LOCKED
            Box(modifier = Modifier.fillMaxSize()) {
                MainAppContent(
                    interactionsBlocked = locked,
                    notificationDestination = requestedDestination,
                    onNotificationDestinationConsumed = onNotificationDestinationConsumed,
                    updateViewModel = updateViewModel
                )
                if (locked) {
                    AppLockScreen(
                        mode = LockMode.VERIFY,
                        onDone = viewModel::showMain
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
        else -> {
            OnboardingScreen(
                mode = OnboardingMode.FIRST_RUN,
                onDone = viewModel::showMain
            )
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
    onNotificationDestinationConsumed: (NotificationDestination) -> Unit,
    timerCoordinator: TimerCoordinatorViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel
) {
    val navigator = rememberNavigator(Route.Main)
    val timerSession by timerCoordinator.session.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pagerCoroutineScope = rememberCoroutineScope()
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
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

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

    LaunchedEffect(timerSession.status) {
        if (timerSession.status == TimerStatus.LIMIT_REACHED) {
            navigator.push(
                Route.RecordForm(
                    isTimer = true,
                    duration = timerSession.elapsedMillis,
                    startTime = timerSession.startedAtEpochMillis
                )
            )
        }
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
                                            snackbarScope = pagerCoroutineScope
                                        )
                                    }
                                    pageEntry<Route.ModeSelect>(navigationMotion) { ModeSelectScreen() }
                                    pageEntry<Route.Timer>(navigationMotion) { TimerScreen() }
                                    pageEntry<Route.RecordForm>(navigationMotion) { route ->
                                        RecordFormScreen(
                                            isTimer = route.isTimer,
                                            durationMillis = route.duration,
                                            timerStartTimeMillis = route.startTime,
                                            flightId = null
                                        )
                                    }
                                    pageEntry<Route.RecordDetail>(navigationMotion) { route ->
                                        RecordDetailScreen(flightId = route.flightId)
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
                                    pageEntry<Route.About>(navigationMotion) { AboutScreen() }
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
                    // miuix popups (OverlayListPopup/OverlayDropdownPreference) register
                    // their state into the popup host; without it the popup never renders
                    // and the triggering row stays stuck in its pressed state.
                    MiuixPopupUtils.MiuixPopupHost()
                    val availableRelease = (updateState as? UpdateCheckState.Available)?.release
                    if (!interactionsBlocked && availableRelease != null) {
                        UpdateAvailableDialog(
                            currentVersion = updateViewModel.currentVersion,
                            release = availableRelease,
                            onDismiss = updateViewModel::dismiss,
                            onOpenRelease = { releaseUrl ->
                                updateViewModel.dismiss()
                                runCatching { uriHandler.openUri(releaseUrl) }
                            }
                        )
                    }
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
    snackbarScope: CoroutineScope
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

@Composable
fun ModeSelectScreen() {
    val navigator = LocalNavigator.current
    SecondaryPageScaffold(
        title = stringResource(R.string.mode_select_title),
        onBack = { navigator.pop() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RiseCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    navigator.push(Route.Timer)
                }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AppIcons.Schedule, null,
                        modifier = Modifier.size(40.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.mode_select_timer),
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            color = MiuixTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.mode_select_timer_summary),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Icon(
                        AppIcons.ChevronRight, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            RiseCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    navigator.push(Route.RecordForm(isTimer = false, duration = 0L, startTime = 0L))
                }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AppIcons.Edit, null,
                        modifier = Modifier.size(40.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.mode_select_manual),
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            color = MiuixTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.mode_select_manual_summary),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Icon(
                        AppIcons.ChevronRight, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
