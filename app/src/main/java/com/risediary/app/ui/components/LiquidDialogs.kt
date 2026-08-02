package com.risediary.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.risediary.app.ui.theme.LocalRiseDarkTheme

/**
 * Backdrop shared by the page and its glass controls.
 *
 * Dialog glass must be drawn as a sibling outside this captured page layer. A platform
 * Dialog owns another window and therefore cannot sample this Backdrop correctly.
 */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun ProvidePageBackdrop(
    backdrop: Backdrop,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalPageBackdrop provides backdrop, content = content)
}

internal data class LiquidDialogEntry(
    val key: Any,
    val onDismissRequest: State<() -> Unit>,
    val content: State<@Composable (Backdrop) -> Unit>
)

class LiquidDialogHostState internal constructor() {
    private var currentEntry by mutableStateOf<LiquidDialogEntry?>(null)

    internal fun show(entry: LiquidDialogEntry) {
        currentEntry = entry
    }

    internal fun dismiss(key: Any) {
        if (currentEntry?.key === key) currentEntry = null
    }

    internal val entry: LiquidDialogEntry?
        get() = currentEntry
}

private val LocalLiquidDialogHostState =
    staticCompositionLocalOf<LiquidDialogHostState?> { null }

@Composable
fun rememberLiquidDialogHostState(): LiquidDialogHostState =
    remember { LiquidDialogHostState() }

@Composable
fun ProvideLiquidDialogHost(
    state: LiquidDialogHostState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLiquidDialogHostState provides state,
        content = content
    )
}

/**
 * Drawn after the page's capture layer, matching the sibling layout used by the
 * AndroidLiquidGlass 1.0.0 Demo.
 */
@Composable
fun LiquidDialogHost(
    state: LiquidDialogHostState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val currentEntry = state.entry
    var retainedEntry by remember { mutableStateOf<LiquidDialogEntry?>(null) }
    val visibilityState = remember { MutableTransitionState(false) }
    LaunchedEffect(currentEntry?.key) {
        if (currentEntry != null) {
            retainedEntry = currentEntry
            visibilityState.targetState = false
            // The host is always composed. Waiting one frame guarantees that a
            // newly attached dialog really transitions from hidden to visible.
            withFrameNanos { }
            visibilityState.targetState = true
        } else {
            visibilityState.targetState = false
        }
    }
    LaunchedEffect(
        currentEntry?.key,
        visibilityState.currentState,
        visibilityState.isIdle
    ) {
        if (
            currentEntry == null &&
            visibilityState.isIdle &&
            !visibilityState.currentState
        ) {
            retainedEntry = null
        }
    }
    val visible = currentEntry != null
    val dark = LocalRiseDarkTheme.current
    val dimColor =
        if (dark) Color(0xFF121212).copy(alpha = 0.56f)
        else Color(0xFF29293A).copy(alpha = 0.23f)
    val interactionSource = remember { MutableInteractionSource() }

    BackHandler(enabled = visible) {
        currentEntry?.onDismissRequest?.value?.invoke()
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        modifier = modifier.fillMaxSize(),
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        val scrimAlpha by transition.animateFloat(
            transitionSpec = {
                tween(if (targetState == EnterExitState.Visible) 180 else 140)
            },
            label = "liquid_dialog_scrim_alpha"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val dialogAlpha by transition.animateFloat(
            transitionSpec = {
                tween(if (targetState == EnterExitState.Visible) 160 else 120)
            },
            label = "liquid_dialog_alpha"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val dialogScale by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    spring(dampingRatio = 0.6f, stiffness = 250f)
                } else {
                    tween(140)
                }
            },
            label = "liquid_dialog_scale"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0.90f }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(dimColor)
                    .clickable(
                        enabled = visible,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            retainedEntry?.onDismissRequest?.value?.invoke()
                        }
                    )
            )

            retainedEntry?.let { entry ->
                key(entry.key) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = dialogAlpha
                                scaleX = dialogScale
                                scaleY = dialogScale
                            }
                    ) {
                        entry.content.value.invoke(backdrop)
                    }
                }
            }
        }
    }
}

/**
 * Official-Demo-style liquid dialog.
 *
 * The 48dp continuous corners, brightness/saturation controls, 16/8dp blur,
 * 24/48dp depth lens and plain highlight mirror the fixed upstream 1.0.0 Demo.
 */
@Composable
fun LiquidDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    shape: Shape = ContinuousRoundedRectangle(48.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    val hostState = LocalLiquidDialogHostState.current
    val key = remember { Any() }
    val currentDismiss = rememberUpdatedState(onDismissRequest)
    val currentContent = rememberUpdatedState<@Composable (Backdrop) -> Unit> { backdrop ->
        LiquidDialogSurface(
            backdrop = backdrop,
            modifier = modifier,
            alignment = alignment,
            shape = shape,
            contentPadding = contentPadding,
            content = content
        )
    }

    if (hostState != null) {
        DisposableEffect(hostState, key) {
            hostState.show(
                LiquidDialogEntry(
                    key = key,
                    onDismissRequest = currentDismiss,
                    content = currentContent
                )
            )
            onDispose { hostState.dismiss(key) }
        }
    } else {
        // A platform Dialog owns another window and cannot capture the page Backdrop.
        // Use an intentionally solid Material-style fallback instead of pretending
        // to render glass from an empty capture layer.
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            val fallbackInteractionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = fallbackInteractionSource,
                        indication = null,
                        onClick = onDismissRequest
                    )
            ) {
                SolidDialogSurface(
                    modifier = modifier,
                    alignment = alignment,
                    shape = shape,
                    contentPadding = contentPadding,
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SolidDialogSurface(
    modifier: Modifier,
    alignment: Alignment,
    shape: Shape,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalRiseDarkTheme.current
    val containerColor = if (dark) Color(0xFF202124) else Color(0xFFF8F9FA)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(shape)
                .background(containerColor)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final)
                                .changes
                                .forEach { it.consume() }
                        }
                    }
                }
                .then(modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}

@Composable
private fun LiquidDialogSurface(
    backdrop: Backdrop,
    modifier: Modifier,
    alignment: Alignment,
    shape: Shape,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalRiseDarkTheme.current
    val containerColor =
        if (dark) Color(0xFF121212).copy(alpha = 0.72f)
        else Color(0xFFFAFAFA).copy(alpha = 0.82f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        colorControls(
                            brightness = if (dark) 0f else 0.2f,
                            saturation = 1.5f
                        )
                        blur(if (dark) 8.dp.toPx() else 16.dp.toPx())
                        lens(
                            24.dp.toPx(),
                            48.dp.toPx(),
                            depthEffect = true
                        )
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) }
                )
                // Consume taps on empty glass space so they never fall through to
                // the dismiss scrim. Child buttons still receive the Main pass.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final)
                                .changes
                                .forEach { it.consume() }
                        }
                    }
                }
                .then(modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}
