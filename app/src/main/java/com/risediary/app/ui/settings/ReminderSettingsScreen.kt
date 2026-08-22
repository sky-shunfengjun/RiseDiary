package com.risediary.app.ui.settings

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.R
import com.risediary.app.reminder.ReminderType
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.WheelColumn
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay
import com.risediary.app.ui.icons.AppIcons

@Composable
fun ReminderSettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var notificationsAvailable by remember {
        mutableStateOf(reminderNotificationsAvailable(context))
    }
    var backgroundRestricted by remember {
        mutableStateOf(isAppBackgroundRestricted(context))
    }
    var exactAlarmsAllowed by remember { mutableStateOf(vm.exactAlarmsAllowed()) }
    var pendingEnable by remember { mutableStateOf<ReminderType?>(null) }
    var pendingImmediateTest by remember { mutableStateOf(false) }
    var pendingBackgroundTest by remember { mutableStateOf(false) }
    var testSent by remember { mutableStateOf(false) }
    var backgroundTestScheduled by remember { mutableStateOf(false) }
    var scheduleTestAfterExactGrant by remember { mutableStateOf(false) }
    var showNotificationBlockedDialog by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var editingReminderTime by remember { mutableStateOf<ReminderType?>(null) }
    var editingMonthlyDay by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAvailable = reminderNotificationsAvailable(context)
        val target = pendingEnable
        val shouldTest = pendingImmediateTest
        val shouldScheduleBackgroundTest = pendingBackgroundTest
        pendingEnable = null
        pendingImmediateTest = false
        pendingBackgroundTest = false
        if (granted && notificationsAvailable) {
            target?.let {
                vm.setReminderEnabled(it, true)
                if (!exactAlarmsAllowed) showExactAlarmDialog = true
            }
            if (shouldTest) testSent = vm.sendTestNotification()
            if (shouldScheduleBackgroundTest) {
                if (exactAlarmsAllowed) {
                    backgroundTestScheduled = vm.scheduleBackgroundReminderTest()
                } else {
                    scheduleTestAfterExactGrant = true
                    showExactAlarmDialog = true
                }
            }
        } else {
            showNotificationBlockedDialog = true
        }
    }

    LifecycleResumeEffect(Unit) {
        notificationsAvailable = reminderNotificationsAvailable(context)
        backgroundRestricted = isAppBackgroundRestricted(context)
        val exactAccessOnResume = vm.exactAlarmsAllowed()
        val exactAccessWasAllowed = exactAlarmsAllowed
        exactAlarmsAllowed = exactAccessOnResume
        if (exactAccessOnResume) {
            if (!exactAccessWasAllowed) vm.refreshReminderSchedules()
            if (scheduleTestAfterExactGrant) {
                backgroundTestScheduled = vm.scheduleBackgroundReminderTest()
                scheduleTestAfterExactGrant = false
            }
        } else {
            scheduleTestAfterExactGrant = false
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(backgroundTestScheduled) {
        if (backgroundTestScheduled) {
            delay(70_000L)
            backgroundTestScheduled = false
        }
    }

    fun requestPermission(
        type: ReminderType? = null,
        immediateTest: Boolean = false,
        backgroundTest: Boolean = false
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingEnable = type
            pendingImmediateTest = immediateTest
            pendingBackgroundTest = backgroundTest
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showNotificationBlockedDialog = true
        }
    }

    fun requestReminderToggle(type: ReminderType, enabled: Boolean) {
        if (!enabled) {
            vm.setReminderEnabled(type, false)
            return
        }
        notificationsAvailable = reminderNotificationsAvailable(context)
        if (notificationsAvailable) {
            vm.setReminderEnabled(type, true)
            if (!exactAlarmsAllowed) showExactAlarmDialog = true
        } else {
            requestPermission(type = type)
        }
    }

    fun sendTestNotification() {
        notificationsAvailable = reminderNotificationsAvailable(context)
        if (notificationsAvailable) {
            testSent = vm.sendTestNotification()
            if (!testSent) showNotificationBlockedDialog = true
        } else {
            requestPermission(immediateTest = true)
        }
    }

    fun scheduleBackgroundTest() {
        if (backgroundTestScheduled) {
            vm.cancelBackgroundReminderTest()
            backgroundTestScheduled = false
            return
        }
        notificationsAvailable = reminderNotificationsAvailable(context)
        if (!notificationsAvailable) {
            requestPermission(backgroundTest = true)
        } else if (!exactAlarmsAllowed) {
            scheduleTestAfterExactGrant = true
            showExactAlarmDialog = true
        } else {
            backgroundTestScheduled = vm.scheduleBackgroundReminderTest()
            if (!backgroundTestScheduled) {
                exactAlarmsAllowed = vm.exactAlarmsAllowed()
                if (!exactAlarmsAllowed) showExactAlarmDialog = true
            }
        }
    }

    SecondaryPageScaffold(
        title = stringResource(R.string.settings_reminder_settings),
        onBack = { navController.popBackStack() }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val dailyEnabled by vm.dailyReminderEnabled.collectAsStateWithLifecycle()
            val dailyTime by vm.dailyReminderTime.collectAsStateWithLifecycle()
            val inactiveEnabled by vm.inactiveReminderEnabled.collectAsStateWithLifecycle()
            val inactiveDays by vm.inactiveReminderDays.collectAsStateWithLifecycle()
            val inactiveTime by vm.inactiveReminderTime.collectAsStateWithLifecycle()
            val monthlyEnabled by
                vm.monthlyLengthReminderEnabled.collectAsStateWithLifecycle()
            val monthlyDay by vm.monthlyLengthReminderDay.collectAsStateWithLifecycle()
            val monthlyTime by
                vm.monthlyLengthReminderTime.collectAsStateWithLifecycle()

            SettingsGroupHeader(stringResource(R.string.settings_group_reminders))
            SettingsReminderAccuracyNotice()
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                if (!notificationsAvailable) {
                    SettingsNotificationWarning(
                        onClick = { openReminderNotificationSettings(context) }
                    )
                    SettingsDivider()
                }
                SettingsToggleItem(
                    icon = AppIcons.NotificationsActive,
                    title = stringResource(R.string.settings_daily_reminder),
                    subtitle = stringResource(
                        R.string.settings_daily_reminder_summary,
                        dailyTime
                    ),
                    checked = dailyEnabled,
                    onCheckedChange = {
                        requestReminderToggle(ReminderType.DAILY, it)
                    }
                )
                if (dailyEnabled) {
                    SettingsDivider()
                    SettingsNavItem(
                        icon = AppIcons.Schedule,
                        title = stringResource(R.string.settings_reminder_time),
                        subtitle = dailyTime,
                        onClick = { editingReminderTime = ReminderType.DAILY }
                    )
                }
                SettingsDivider()
                SettingsToggleItem(
                    icon = AppIcons.EventRepeat,
                    title = stringResource(R.string.settings_inactive_reminder),
                    subtitle = stringResource(
                        R.string.settings_inactive_reminder_summary,
                        inactiveDays,
                        inactiveTime
                    ),
                    checked = inactiveEnabled,
                    onCheckedChange = {
                        requestReminderToggle(ReminderType.INACTIVE, it)
                    }
                )
                if (inactiveEnabled) {
                    SettingsDivider()
                    SettingsReminderDaysItem(
                        value = inactiveDays,
                        onSelect = vm::setInactiveReminderDays
                    )
                    SettingsDivider()
                    SettingsNavItem(
                        icon = AppIcons.Schedule,
                        title = stringResource(R.string.settings_reminder_time),
                        subtitle = inactiveTime,
                        onClick = { editingReminderTime = ReminderType.INACTIVE }
                    )
                }
                SettingsDivider()
                SettingsToggleItem(
                    icon = AppIcons.CalendarMonth,
                    title = stringResource(R.string.settings_monthly_length_reminder),
                    subtitle = stringResource(
                        R.string.settings_monthly_length_reminder_summary,
                        monthlyDay,
                        monthlyTime
                    ),
                    checked = monthlyEnabled,
                    onCheckedChange = {
                        requestReminderToggle(ReminderType.MONTHLY_LENGTH, it)
                    }
                )
                if (monthlyEnabled) {
                    SettingsDivider()
                    SettingsNavItem(
                        icon = AppIcons.CalendarMonth,
                        title = stringResource(R.string.settings_monthly_reminder_day),
                        subtitle = stringResource(
                            R.string.settings_monthly_reminder_day_value,
                            monthlyDay
                        ),
                        onClick = { editingMonthlyDay = true }
                    )
                    SettingsDivider()
                    SettingsNavItem(
                        icon = AppIcons.Schedule,
                        title = stringResource(R.string.settings_reminder_time),
                        subtitle = monthlyTime,
                        onClick = {
                            editingReminderTime = ReminderType.MONTHLY_LENGTH
                        }
                    )
                }
                SettingsDivider()
                ReminderResourceCleanupInfo()
            }

            SettingsGroupHeader(stringResource(R.string.settings_group_notification_delivery))
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                SettingsNavItem(
                    icon = AppIcons.Schedule,
                    title = stringResource(
                        if (exactAlarmsAllowed) {
                            R.string.settings_exact_alarm_allowed
                        } else {
                            R.string.settings_exact_alarm_not_allowed
                        }
                    ),
                    subtitle = stringResource(
                        when {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                                R.string.settings_exact_alarm_legacy_summary
                            exactAlarmsAllowed ->
                                R.string.settings_exact_alarm_allowed_summary
                            else ->
                                R.string.settings_exact_alarm_not_allowed_summary
                        }
                    ),
                    titleColor = if (exactAlarmsAllowed) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            showExactAlarmDialog = true
                        }
                    }
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = AppIcons.NotificationsActive,
                    title = stringResource(R.string.settings_notification_test),
                    subtitle = stringResource(
                        if (testSent) {
                            R.string.settings_notification_test_sent
                        } else {
                            R.string.settings_notification_test_summary
                        }
                    ),
                    onClick = ::sendTestNotification
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = AppIcons.Timer,
                    title = stringResource(
                        if (backgroundTestScheduled) {
                            R.string.settings_background_test_cancel
                        } else {
                            R.string.settings_background_test
                        }
                    ),
                    subtitle = stringResource(
                        if (backgroundTestScheduled) {
                            R.string.settings_background_test_scheduled
                        } else {
                            R.string.settings_background_test_summary
                        }
                    ),
                    onClick = ::scheduleBackgroundTest
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = AppIcons.VolumeUp,
                    title = stringResource(R.string.settings_reminder_sound_vibration),
                    subtitle =
                        stringResource(R.string.settings_reminder_sound_vibration_summary),
                    onClick = { openReminderNotificationSettings(context) }
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = AppIcons.BatteryAlert,
                    title = stringResource(
                        if (backgroundRestricted) {
                            R.string.settings_background_restricted
                        } else {
                            R.string.settings_background_delivery
                        }
                    ),
                    subtitle = stringResource(
                        if (backgroundRestricted) {
                            R.string.settings_background_restricted_summary
                        } else {
                            R.string.settings_background_delivery_summary
                        }
                    ),
                    titleColor = if (backgroundRestricted) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    onClick = { openApplicationSettings(context) }
                )
            }
        }
    }

    val dailyTime by vm.dailyReminderTime.collectAsStateWithLifecycle()
    val inactiveTime by vm.inactiveReminderTime.collectAsStateWithLifecycle()
    val monthlyTime by vm.monthlyLengthReminderTime.collectAsStateWithLifecycle()
    val currentMonthlyDay by vm.monthlyLengthReminderDay.collectAsStateWithLifecycle()
    ReminderSettingsDialogs(
        editingReminderTime = editingReminderTime,
        dailyTime = dailyTime,
        inactiveTime = inactiveTime,
        monthlyTime = monthlyTime,
        onDismissTime = { editingReminderTime = null },
        onTimeSelected = { type, time ->
            vm.setReminderTime(type, time)
            editingReminderTime = null
        },
        editingMonthlyDay = editingMonthlyDay,
        currentMonthlyDay = currentMonthlyDay,
        onDismissMonthlyDay = { editingMonthlyDay = false },
        onMonthlyDaySelected = {
            vm.setMonthlyLengthReminderDay(it)
            editingMonthlyDay = false
        },
        showNotificationBlockedDialog = showNotificationBlockedDialog,
        onDismissNotificationBlocked = { showNotificationBlockedDialog = false },
        onOpenNotificationSettings = {
            showNotificationBlockedDialog = false
            openReminderNotificationSettings(context)
        },
        showExactAlarmDialog = showExactAlarmDialog,
        onDismissExactAlarm = {
            showExactAlarmDialog = false
            scheduleTestAfterExactGrant = false
        },
        onOpenExactAlarmSettings = {
            showExactAlarmDialog = false
            openExactAlarmSettings(context)
        }
    )
}
