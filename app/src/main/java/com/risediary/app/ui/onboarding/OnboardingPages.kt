package com.risediary.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.ui.components.LiquidBackButton
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.settings.SettingsDivider
import com.risediary.app.ui.settings.SettingsSliderItem
import com.risediary.app.ui.settings.SettingsThemeItem
import com.risediary.app.ui.settings.SettingsToggleItem
import com.risediary.app.ui.settings.SettingsVolumeModeItem
import com.risediary.app.ui.settings.SettingsReminderAccuracyNotice
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.CardOrange
import com.risediary.app.ui.theme.CardPurple
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DecimalFormat

@Composable
internal fun CockpitBackdrop(
    topInsetPx: Float = 0f,
    windowHeightPx: Float = Float.NaN,
    modifier: Modifier = Modifier
) {
    val dark = LocalRiseDarkTheme.current
    val transition = rememberInfiniteTransition(label = "cockpit_status")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cockpit_pulse"
    )
    val lineColor = if (dark) Color(0xFF4FB8FF) else Color(0xFF1678C2)
    val gridColor = lineColor.copy(alpha = if (dark) 0.08f else 0.06f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                backgroundBrush(
                    topInsetPx = topInsetPx,
                    windowHeightPx = windowHeightPx
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val grid = 48.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += grid
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                y += grid
            }

            val center = Offset(size.width * 0.78f, size.height * 0.18f)
            drawCircle(
                color = lineColor.copy(alpha = 0.12f),
                radius = 92.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = lineColor.copy(alpha = 0.08f),
                radius = 58.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = lineColor.copy(alpha = 0.22f),
                startAngle = 205f,
                sweepAngle = 118f,
                useCenter = false,
                topLeft = Offset(center.x - 92.dp.toPx(), center.y - 92.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(184.dp.toPx(), 184.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            val route = Path().apply {
                moveTo(size.width * 0.06f, size.height * 0.78f)
                cubicTo(
                    size.width * 0.32f,
                    size.height * 0.70f,
                    size.width * 0.52f,
                    size.height * 0.90f,
                    size.width * 0.93f,
                    size.height * 0.72f
                )
            }
            drawPath(
                route,
                color = lineColor.copy(alpha = 0.13f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(
                color = CardGreen.copy(alpha = pulse),
                radius = 4.dp.toPx(),
                center = Offset(size.width * 0.93f, size.height * 0.72f)
            )
        }
    }
}

@Composable
internal fun SetupProgressHeader(
    step: Int,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            LiquidBackButton(onClick = onBack, backdrop = backdrop)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.onboarding_setup_progress, step, 4),
                    fontSize = MiuixTheme.textStyles.headline2.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.onboarding_setup_label),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                color = if (index < step) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun SetupPage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.title2.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        content()
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
internal fun InfoRow(icon: ImageVector, title: String, subtitle: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
internal fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
internal fun SmallFeature(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                label,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun WelcomeAction(backdrop: Backdrop, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidGlassButton(
            onClick = onClick,
            backdrop = backdrop,
            tint = CardBlue,
            height = 54.dp,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "开始设置"
                }
        ) {
            Icon(
                Icons.Default.FlightTakeoff,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimary
            )
            Text(
                stringResource(R.string.onboarding_begin_setup),
                color = MiuixTheme.colorScheme.onPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_age_notice),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun SetupBottomActions(
    backdrop: Backdrop,
    onLater: () -> Unit,
    onContinue: () -> Unit,
    laterLabel: String = stringResource(R.string.onboarding_later),
    continueLabel: String = stringResource(R.string.onboarding_save_continue)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(0.9f).padding(4.dp)) {
            LiquidGlassButton(
                onClick = onLater,
                backdrop = backdrop,
                surfaceColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(laterLabel, color = MiuixTheme.colorScheme.onSurface)
            }
        }
        Box(modifier = Modifier.weight(1.35f).padding(4.dp)) {
            LiquidGlassButton(
                onClick = onContinue,
                backdrop = backdrop,
                tint = CardBlue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    continueLabel,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
