package com.risediary.app.ui.components


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

@Composable
fun LiquidAlertDialog(
    onDismissRequest: () -> Unit,
    confirmAction: RiseDialogAction,
    modifier: Modifier = Modifier,
    dismissAction: RiseDialogAction? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    neutralAction: RiseDialogAction? = null,
    adaptiveActions: Boolean = true
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
        RiseDialogActions(
            confirmAction = confirmAction,
            dismissAction = dismissAction,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp),
            neutralAction = neutralAction,
            adaptiveActions = adaptiveActions
        )
    }
}

@Composable
fun LiquidDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmAction: RiseDialogAction,
    dismissAction: RiseDialogAction? = null,
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
        RiseDialogActions(
            confirmAction = confirmAction,
            dismissAction = dismissAction,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
        )
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
