package com.risediary.app.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.RiseDiaryApp
import com.risediary.app.reminder.ReminderConfiguration
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.components.LiquidSlider
import com.risediary.app.ui.components.LiquidToggle
import com.risediary.app.ui.theme.CardOrange
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 6.dp)
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    )
}

@Composable
internal fun SettingsIcon(
    icon: ImageVector,
    tint: Color = MiuixTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.11f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = MiuixTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 设置编辑行：默认展示标题与摘要，点击右侧铅笔后在行内展开输入框，点勾保存。
 */
@Composable
internal fun SettingsEditItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    inputTransform: (String) -> String = { it }
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (editing) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = text,
                        onValueChange = { text = inputTransform(it) },
                        modifier = Modifier.weight(1f),
                        label = placeholder,
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onValueChange(text)
                                    editing = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "保存"
                                )
                            }
                        }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        if (!editing) {
            IconButton(onClick = { editing = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
internal fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .layerBackdrop(rowBackdrop)
                .padding(start = 16.dp, end = 92.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            backdrop = rowBackdrop,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )
    }
}

@Composable
internal fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .layerBackdrop(rowBackdrop)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
        LiquidSlider(
            value = { value },
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            backdrop = rowBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 64.dp, end = 18.dp, bottom = 6.dp)
        )
    }
}

@Composable
internal fun SettingsReminderDaysItem(
    value: Int,
    onSelect: (Int) -> Unit
) {
    val dayOptions = remember { ReminderConfiguration.INACTIVE_DAY_OPTIONS }
    val labels = dayOptions.map { day ->
        stringResource(R.string.settings_inactive_day_option, day)
    }
    val options = remember(labels) {
        labels.map { label -> LiquidSegmentOption(label, Icons.Default.EventRepeat) }
    }
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .layerBackdrop(rowBackdrop)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Icons.Default.EventRepeat)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_inactive_interval),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(52.dp))
        }
        LiquidSegmentedControl(
            options = options,
            selectedIndex = dayOptions.indexOf(value).coerceAtLeast(0),
            onSelected = { index -> dayOptions.getOrNull(index)?.let(onSelect) },
            backdrop = rowBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 12.sp
        )
    }
}

@Composable
internal fun SettingsNotificationWarning(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = Icons.Default.NotificationsActive,
            tint = MiuixTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_notification_blocked_title),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_notification_blocked_summary),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }
}

@Composable
internal fun SettingsReminderAccuracyNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardOrange.copy(alpha = 0.10f)
    ) {
        Text(
            text = stringResource(R.string.reminder_accuracy_notice),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(12.dp)
        )
    }
}

internal fun reminderNotificationsAvailable(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(RiseDiaryApp.CHANNEL_REMINDER)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
    }
    return true
}

internal fun openReminderNotificationSettings(context: Context) {
    val channelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, RiseDiaryApp.CHANNEL_REMINDER)
        }
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
    runCatching {
        context.startActivity(channelIntent)
    }.recoverCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}
