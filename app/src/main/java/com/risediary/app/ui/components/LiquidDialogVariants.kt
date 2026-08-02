package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.risediary.app.ui.theme.LocalRiseDarkTheme

@Composable
fun LiquidAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified
) {
    LiquidDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 24.dp, end = 28.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) { icon() }
        }
        if (title != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 28.dp,
                        top = if (icon == null) 24.dp else 12.dp,
                        end = 28.dp,
                        bottom = 12.dp
                    )
            ) { title() }
        }
        if (text != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 24.dp)
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 12.dp)
            ) { text() }
        }
        LiquidDialogActions(
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
fun LiquidDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidDialog(
        onDismissRequest = onDismissRequest,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 18.dp, end = 18.dp),
            content = content
        )
        LiquidDialogActions(
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun LiquidDialogActions(
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dark = LocalRiseDarkTheme.current
    val contentColor = if (dark) Color.White else Color.Black
    val accentColor = if (dark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val containerColor = if (dark) {
        Color(0xFF121212).copy(alpha = 0.20f)
    } else {
        Color(0xFFFAFAFA).copy(alpha = 0.20f)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissButton != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(ContinuousCapsule)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(contentColor))
                ) {
                    ForceFillAction(dismissButton)
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(ContinuousCapsule)
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(Color.White))
            ) {
                ForceFillAction(confirmButton)
            }
        }
    }
}

@Composable
private fun ForceFillAction(content: @Composable () -> Unit) {
    Layout(content = { content() }) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(
                minWidth = constraints.maxWidth,
                minHeight = constraints.maxHeight
            )
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

@Composable
fun LiquidBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    LiquidDialog(
        onDismissRequest = onDismissRequest,
        alignment = Alignment.BottomCenter,
        shape = ContinuousRoundedRectangle(40.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}
