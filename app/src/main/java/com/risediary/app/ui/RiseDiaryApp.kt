package com.risediary.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.about.AboutScreen
import com.risediary.app.ui.achievement.AchievementWallScreen
import com.risediary.app.ui.backup.BackupRestoreScreen
import com.risediary.app.ui.components.LiquidGlassBottomBar
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
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "首页")
    data object Records : Screen("records", "记录")
    data object Settings : Screen("settings", "设置")
    data object ModeSelect : Screen("mode_select", "开始起飞")
    data object Timer : Screen("timer", "计时")
    data object RecordForm :
        Screen("record_form/{isTimer}/{duration}/{startTime}", "记录表单") {
        fun createRoute(
            isTimer: Boolean,
            duration: Long = 0,
            startTime: Long = 0
        ) = "record_form/$isTimer/$duration/$startTime"
    }
    data object RecordDetail : Screen("record_detail/{flightId}", "记录详情") {
        fun createRoute(flightId: Long) = "record_detail/$flightId"
    }
    data object RecordEdit : Screen("record_edit/{flightId}", "编辑记录") {
        fun createRoute(flightId: Long) = "record_edit/$flightId"
    }
    data object TagManager : Screen("tag_manager", "标签管理")
    data object AchievementWall : Screen("achievement_wall", "成就墙")
    data object About : Screen("about", "关于")
    data object CardOrder : Screen("card_order", "首页卡片排序")
    data object BackupRestore : Screen("backup_restore", "备份恢复")
    data object LengthHistory : Screen("length_history", "长度记录")
    data object ReminderSettings : Screen("reminder_settings", "提醒设置")
    data object AppLockSettings : Screen("app_lock_settings", "应用锁")
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

@Composable
private fun MainAppContent(
    interactionsBlocked: Boolean,
    notificationDestination: NotificationDestination?,
    onNotificationDestinationConsumed: (NotificationDestination) -> Unit,
    timerCoordinator: TimerCoordinatorViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel
) {
    val navController = rememberNavController()
    val timerSession by timerCoordinator.session.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember(currentRoute) { SnackbarHostState() }
    val mainTabs = remember { listOf(Screen.Home, Screen.Records, Screen.Settings) }
    val mainRoutes = remember { mainTabs.map(Screen::route).toSet() }
    val selectedTabIndex = mainTabs
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)
    val isMainTab = currentRoute in mainTabs.map(Screen::route)
    val appBackground = backgroundBrush()
    val currentAppBackground by rememberUpdatedState(appBackground)
    val backdrop = rememberLayerBackdrop {
        drawRect(brush = currentAppBackground)
        drawContent()
    }
    val liquidDialogHostState = rememberLiquidDialogHostState()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(timerSession.status) {
        if (timerSession.status == TimerStatus.LIMIT_REACHED) {
            val route = Screen.RecordForm.createRoute(
                isTimer = true,
                duration = timerSession.elapsedMillis,
                startTime = timerSession.startedAtEpochMillis
            )
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(notificationDestination, interactionsBlocked) {
        val destination = notificationDestination ?: return@LaunchedEffect
        if (interactionsBlocked) return@LaunchedEffect
        val route = when (destination) {
            NotificationDestination.RECORDS -> Screen.Records.route
            NotificationDestination.LENGTH_HISTORY -> Screen.LengthHistory.route
        }
        if (route in mainRoutes) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        onNotificationDestinationConsumed(destination)
    }

    ProvidePageBackdrop(backdrop) {
        ProvideLiquidDialogHost(liquidDialogHostState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBackground)
                    .blockInteractionsAndAccessibility(interactionsBlocked)
            ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            containerColor = Color.Transparent
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    if (
                        initialState.destination.route in mainRoutes &&
                        targetState.destination.route in mainRoutes
                    ) {
                        fadeIn(tween(180))
                    } else {
                        fadeIn(tween(220)) +
                            slideInHorizontally(tween(300)) { width -> width }
                    }
                },
                exitTransition = {
                    fadeOut(tween(140))
                },
                popEnterTransition = {
                    fadeIn(tween(220)) +
                        slideInHorizontally(tween(280)) { width -> -width / 6 }
                },
                popExitTransition = {
                    fadeOut(tween(220)) +
                        slideOutHorizontally(tween(280)) { width -> width }
                }
            ) {
                composable(Screen.Home.route) { HomeScreen(navController) }
                composable(Screen.Records.route) {
                    RecordsScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState
                    )
                }
                composable(Screen.Settings.route) { SettingsScreen(navController) }
                composable(Screen.ModeSelect.route) { ModeSelectScreen(navController) }
                composable(Screen.Timer.route) { TimerScreen(navController) }
                composable(
                    route = Screen.RecordForm.route,
                    arguments = listOf(
                        navArgument("isTimer") { type = NavType.StringType },
                        navArgument("duration") { type = NavType.StringType },
                        navArgument("startTime") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val isTimer = backStackEntry.arguments?.getString("isTimer")?.toBooleanStrictOrNull() ?: false
                    val dur = backStackEntry.arguments?.getString("duration")?.toLongOrNull() ?: 0L
                    val start = backStackEntry.arguments?.getString("startTime")?.toLongOrNull() ?: 0L
                    RecordFormScreen(
                        navController = navController,
                        isTimer = isTimer,
                        durationMillis = dur,
                        timerStartTimeMillis = start
                    )
                }
                composable(
                    route = Screen.RecordDetail.route,
                    arguments = listOf(navArgument("flightId") { type = NavType.LongType })
                ) { RecordDetailScreen(navController) }
                composable(
                    route = Screen.RecordEdit.route,
                    arguments = listOf(navArgument("flightId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val flightId = backStackEntry.arguments?.getLong("flightId") ?: 0L
                    RecordFormScreen(
                        navController = navController,
                        isTimer = false,
                        durationMillis = 0L,
                        timerStartTimeMillis = 0L,
                        flightId = flightId
                    )
                }
                composable(Screen.TagManager.route) { TagManagerScreen(navController) }
                composable(Screen.AchievementWall.route) { AchievementWallScreen(navController) }
                composable(Screen.About.route) { AboutScreen(navController) }
                composable(Screen.CardOrder.route) { CardOrderScreen(navController) }
                composable(Screen.BackupRestore.route) {
                    BackupRestoreScreen(
                        navController = navController,
                        snackbarHostState = snackbarHostState
                    )
                }
                composable(Screen.LengthHistory.route) { LengthHistoryScreen(navController) }
                composable(Screen.ReminderSettings.route) {
                    ReminderSettingsScreen(navController)
                }
                composable(Screen.AppLockSettings.route) {
                    AppLockSettingsScreen(navController)
                }

                // Iteration 10: Lock & Onboarding routes
                composable("lock_setup") {
                    AppLockScreen(
                        mode = LockMode.CREATE,
                        onDone = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("lock_change") {
                    AppLockScreen(
                        mode = LockMode.CHANGE_OLD,
                        onDone = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("lock_disable") {
                    AppLockScreen(
                        mode = LockMode.DISABLE_VERIFY,
                        onDone = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("onboarding_review") {
                    OnboardingScreen(
                        mode = OnboardingMode.REVIEW,
                        onDone = { navController.popBackStack() },
                        onManageAppLock = {
                            navController.navigate(Screen.AppLockSettings.route)
                        }
                    )
                }
            }
        }
            if (isMainTab) {
                LiquidGlassBottomBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index ->
                    mainTabs.getOrNull(index)?.let { screen ->
                        if (screen.route != currentRoute) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                onFlightClick = {
                    navController.navigate(Screen.ModeSelect.route) {
                        launchSingleTop = true
                    }
                },
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 8.dp
                    )
                )
            }
            LiquidSnackbarHost(
                hostState = snackbarHostState,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = if (isMainTab) 84.dp else 20.dp
                    )
            )
            LiquidDialogHost(
                state = liquidDialogHostState,
                backdrop = backdrop
            )
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
fun PlaceholderScreen(title: String, navController: NavController) {
    SecondaryPageScaffold(
        title = title,
        onBack = { navController.navigateUp() }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(title, fontSize = MiuixTheme.textStyles.title3.fontSize,
                color = MiuixTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ModeSelectScreen(navController: NavController) {
    SecondaryPageScaffold(
        title = "选择起飞方式",
        onBack = { navController.navigateUp() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Option 1: Timer
            RiseCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Screen.Timer.route) {
                            launchSingleTop = true
                        }
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
                        Text("开始计时",
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            color = MiuixTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("实时计时，自动记录用时",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Icon(
                        AppIcons.ChevronRight, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // Option 2: Direct fill
            RiseCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Screen.RecordForm.createRoute(isTimer = false)) {
                            launchSingleTop = true
                        }
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
                        Text("我已起飞",
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            color = MiuixTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("跳过计时，直接填写记录",
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
