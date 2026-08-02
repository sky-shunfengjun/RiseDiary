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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.reorderByDragOffset
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardOrderScreen(
    navController: NavController,
    vm: CardOrderViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val closeScreen: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                runCatching { vm.save() }
                    .onSuccess { navController.navigateUp() }
                    .onFailure { isClosing = false }
            }
        }
    }
    BackHandler(enabled = !isClosing, onBack = closeScreen)

    SecondaryPageScaffold(
        title = "首页卡片排序",
        onBack = closeScreen
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                "长按左侧拖动柄调整顺序，右侧按钮控制首页显示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            CardOrderList(vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardOrderList(
    vm: CardOrderViewModel,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth()
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
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
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
                            Icons.Default.DragHandle,
                            contentDescription = "按住并上下拖动排序",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            vm.getLabel(cardId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isVisible) "显示在首页" else "已隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { vm.toggleVisible(cardId) }) {
                        Icon(
                            if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isVisible) "显示" else "隐藏",
                            tint = if (isVisible) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
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
