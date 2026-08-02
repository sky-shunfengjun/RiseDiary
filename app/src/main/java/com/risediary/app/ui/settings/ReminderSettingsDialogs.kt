package com.risediary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.reminder.ReminderType
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.WheelColumn
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun ReminderSettingsDialogs(
    editingReminderTime: ReminderType?,
    dailyTime: String,
    inactiveTime: String,
    monthlyTime: String,
    onDismissTime: () -> Unit,
    onTimeSelected: (ReminderType, String) -> Unit,
    editingMonthlyDay: Boolean,
    currentMonthlyDay: Int,
    onDismissMonthlyDay: () -> Unit,
    onMonthlyDaySelected: (Int) -> Unit,
    showNotificationBlockedDialog: Boolean,
    onDismissNotificationBlocked: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    showExactAlarmDialog: Boolean,
    onDismissExactAlarm: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    editingReminderTime?.let { type ->
        val currentValue = when (type) {
            ReminderType.DAILY -> dailyTime
            ReminderType.INACTIVE -> inactiveTime
            ReminderType.MONTHLY_LENGTH -> monthlyTime
        }
        val parsed = remember(type, currentValue) {
            runCatching { LocalTime.parse(currentValue) }.getOrDefault(LocalTime.of(22, 0))
        }
        var pickedHour by remember(type, currentValue) { mutableIntStateOf(parsed.hour) }
        var pickedMinute by remember(type, currentValue) { mutableIntStateOf(parsed.minute) }
        LiquidAlertDialog(
            onDismissRequest = onDismissTime,
            title = {
                Text(
                    stringResource(R.string.settings_choose_reminder_time),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelColumn(
                        stringResource(R.string.time_hour),
                        0..23,
                        pickedHour,
                        { pickedHour = it },
                        Modifier.weight(1f)
                    )
                    WheelColumn(
                        stringResource(R.string.time_minute),
                        0..59,
                        pickedMinute,
                        { pickedMinute = it },
                        Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(
                            type,
                            String.format(Locale.ROOT, "%02d:%02d", pickedHour, pickedMinute)
                        )
                    }
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissTime) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (editingMonthlyDay) {
        var pickedDay by remember(currentMonthlyDay) { mutableIntStateOf(currentMonthlyDay) }
        LiquidAlertDialog(
            onDismissRequest = onDismissMonthlyDay,
            title = {
                Text(
                    stringResource(R.string.settings_choose_monthly_day),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                WheelColumn(
                    stringResource(R.string.time_day),
                    1..28,
                    pickedDay,
                    { pickedDay = it },
                    Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onMonthlyDaySelected(pickedDay) }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissMonthlyDay) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNotificationBlockedDialog) {
        LiquidAlertDialog(
            onDismissRequest = onDismissNotificationBlocked,
            title = { Text(stringResource(R.string.settings_notification_blocked_title)) },
            text = { Text(stringResource(R.string.settings_notification_blocked_message)) },
            confirmButton = {
                TextButton(onClick = onOpenNotificationSettings) {
                    Text(stringResource(R.string.settings_open_system_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNotificationBlocked) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showExactAlarmDialog) {
        LiquidAlertDialog(
            onDismissRequest = onDismissExactAlarm,
            title = { Text(stringResource(R.string.settings_exact_alarm_dialog_title)) },
            text = { Text(stringResource(R.string.settings_exact_alarm_dialog_message)) },
            confirmButton = {
                TextButton(onClick = onOpenExactAlarmSettings) {
                    Text(stringResource(R.string.settings_open_system_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExactAlarm) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
