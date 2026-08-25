package com.risediary.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.reorderByDragOffset
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.risediary.app.ui.icons.AppIcons

@Composable
fun CardOrderScreen(
    vm: CardOrderViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val closeScreen: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                runCatching { vm.save() }
                    .onSuccess { navigator.pop() }
                    .onFailure { isClosing = false }
            }
        }
    }
    BackHandler(enabled = !isClosing, onBack = closeScreen)

    SecondaryPageScaffold(
        title = stringResource(R.string.card_order_title),
        onBack = closeScreen
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.card_order_instructions),
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(
                    start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr) + 4.dp,
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateRightPadding(LayoutDirection.Ltr) + 4.dp
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            CardOrderList(
                vm = vm,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = innerPadding.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = innerPadding.calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun CardOrderList(
    vm: CardOrderViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    var draggedCardId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    var itemExtentPx by remember { mutableFloatStateOf(with(density) { 88.dp.toPx() }) }

    fun finishDragging() {
        val finishingId = draggedCardId
        settleJob?.cancel()
        settleJob = dragScope.launch {
            animate(
                initialValue = dragOffsetY,
                targetValue = 0f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
            ) { value, _ ->
                dragOffsetY = value
            }
            if (draggedCardId == finishingId) {
                draggedCardId = null
                dragOffsetY = 0f
            }
        }
    }

LazyColumn(
verticalArrangement = Arrangement.spacedBy(12.dp),
modifier = modifier
    .fillMaxWidth(),
contentPadding = contentPadding
) {
        itemsIndexed(
            items = vm.orderedIds,
            key = { _, id -> id }
        ) { index, cardId ->
            val isDragged = cardId == draggedCardId
            val isVisible = vm.visibility[cardId] ?: true
            val placementModifier =
                if (isDragged) Modifier else Modifier.animateItem()

            RiseCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(placementModifier)
                    .onGloballyPositioned { coordinates ->
                        itemExtentPx = coordinates.size.height.toFloat() +
                            with(density) { 12.dp.toPx() }
                    }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isDragged) {
                            translationY = dragOffsetY
                            scaleX = 1.01f
                            scaleY = 1.01f
                            shadowElevation = 6.dp.toPx()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.09f),
                                CircleShape
                            )
                            .pointerInput(cardId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        settleJob?.cancel()
                                        draggedCardId = cardId
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, offset ->
                                        change.consume()
                                        dragOffsetY = reorderByDragOffset(
                                            items = vm.orderedIds,
                                            draggedItem = cardId,
                                            dragOffsetY = dragOffsetY + offset.y,
                                            itemExtentPx = itemExtentPx
                                        )
                                    },
                                    onDragEnd = ::finishDragging,
                                    onDragCancel = ::finishDragging
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.DragHandle,
                            contentDescription = stringResource(R.string.cd_drag_reorder),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.055f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = MiuixTheme.textStyles.headline2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val labelRes = vm.getLabelRes(cardId)
                        Text(
                            if (labelRes != 0) stringResource(labelRes) else cardId,
                            fontSize = MiuixTheme.textStyles.title4.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isVisible) {
                                stringResource(R.string.card_order_visible)
                            } else {
                                stringResource(R.string.card_order_hidden)
                            },
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    IconButton(onClick = { vm.toggleVisible(cardId) }) {
                        Icon(
                            if (isVisible) AppIcons.Visibility else AppIcons.VisibilityOff,
                            contentDescription = if (isVisible) {
                                stringResource(R.string.action_show)
                            } else {
                                stringResource(R.string.action_hide)
                            },
                            tint = if (isVisible) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
