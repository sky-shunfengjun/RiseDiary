package com.risediary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.ui.theme.StatusSuccess
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

private const val LIQUID_SNACKBAR_TIMEOUT_MILLIS = 5_000L

enum class LiquidSnackbarTone {
    UNDO,
    SUCCESS,
    ERROR
}

private data class LiquidSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite,
    val tone: LiquidSnackbarTone
) : SnackbarVisuals

suspend fun SnackbarHostState.showLiquidSnackbar(
    message: String,
    actionLabel: String? = null,
    tone: LiquidSnackbarTone
): SnackbarResult = showSnackbar(
    LiquidSnackbarVisuals(
        message = message,
        actionLabel = actionLabel,
        tone = tone
    )
)

@Composable
fun LiquidSnackbarHost(
    hostState: SnackbarHostState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val currentSnackbarData = hostState.currentSnackbarData

    LaunchedEffect(currentSnackbarData) {
        if (currentSnackbarData == null) return@LaunchedEffect

        delay(LIQUID_SNACKBAR_TIMEOUT_MILLIS)
        if (hostState.currentSnackbarData === currentSnackbarData) {
            currentSnackbarData.dismiss()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.fillMaxWidth(),
            snackbar = { data ->
                LiquidSnackbar(data = data, backdrop = backdrop)
            }
        )
    }
}

@Composable
private fun LiquidSnackbar(
    data: SnackbarData,
    backdrop: Backdrop
) {
    val visuals = data.visuals as? LiquidSnackbarVisuals
    val tone = visuals?.tone ?: LiquidSnackbarTone.UNDO
    val accent = when (tone) {
        LiquidSnackbarTone.UNDO -> MiuixTheme.colorScheme.primary
        LiquidSnackbarTone.SUCCESS -> StatusSuccess
        LiquidSnackbarTone.ERROR -> MiuixTheme.colorScheme.error
    }
    val surfaceAlpha = if (LocalRiseDarkTheme.current) 0.16f else 0.09f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .heightIn(min = 56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(accent.copy(alpha = surfaceAlpha))
                }
            )
            .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = visuals?.message ?: data.visuals.message,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        data.visuals.actionLabel?.let { actionLabel ->
            TextButton(
                text = actionLabel,
                onClick = data::performAction,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    disabledColor = androidx.compose.ui.graphics.Color.Transparent,
                    textColor = accent,
                    disabledTextColor = accent
                ),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp
                ),
                textStyle = MiuixTheme.textStyles.button.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
