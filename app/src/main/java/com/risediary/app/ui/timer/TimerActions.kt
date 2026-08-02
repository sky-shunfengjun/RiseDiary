package com.risediary.app.ui.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.ui.components.LiquidGlassButton

@Composable
internal fun PrimaryTimerButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    backdrop: Backdrop,
    width: Dp = 204.dp
) {
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.width(width),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f),
        height = 58.dp,
        highlightIntensity = 0.38f,
        highlightRadiusMultiplier = 1f,
        pressExpansion = 2.dp
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
