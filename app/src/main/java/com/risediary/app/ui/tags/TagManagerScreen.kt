package com.risediary.app.ui.tags

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.data.entity.Tag
import com.risediary.app.ui.components.LiquidAddButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.reorderByDragOffset
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TagManagerScreen(
    navController: NavController,
    viewModel: TagManagerViewModel = hiltViewModel()
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Tag?>(null) }
    val displayedTags = remember { mutableStateListOf<Tag>() }
    var draggedTagId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var pendingOrderIds by remember { mutableStateOf<List<Long>?>(null) }
    val dragScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    var itemExtentPx by remember { mutableFloatStateOf(with(density) { 88.dp.toPx() }) }

    LaunchedEffect(tags, draggedTagId) {
        if (draggedTagId == null) {
            val sourceIds = tags.map(Tag::id)
            val pending = pendingOrderIds
            if (pending == null || sourceIds == pending) {
                displayedTags.clear()
                displayedTags.addAll(tags)
                if (sourceIds == pending) pendingOrderIds = null
            }
        }
    }

    fun finishDragging() {
        val finishingId = draggedTagId
        if (finishingId != null) {
            pendingOrderIds = displayedTags.map(Tag::id)
            viewModel.reorder(displayedTags.toList())
        }
        settleJob?.cancel()
        settleJob = dragScope.launch {
            animate(
                initialValue = dragOffsetY,
                targetValue = 0f,
                animationSpec = tween(140, easing = FastOutSlowInEasing)
            ) { value, _ ->
                dragOffsetY = value
            }
            if (draggedTagId == finishingId) {
                draggedTagId = null
                dragOffsetY = 0f
            }
        }
    }

    SecondaryPageScaffold(
        title = "管理方式标签",
        onBack = { navController.navigateUp() },
        floatingActionButton = { backdrop ->
            LiquidAddButton(
                onClick = {
                    editingTag = null
                    showEditor = true
                },
                backdrop = backdrop,
                contentDescription = "添加标签"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "长按左侧拖动柄调整顺序，松手后自动保存。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(displayedTags, key = { _, tag -> tag.id }) { index, tag ->
                    val isDragged = tag.id == draggedTagId
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
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    MiuixTheme.colorScheme.primary.copy(alpha = 0.09f),
                                    CircleShape
                                )
                                .pointerInput(tag.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            settleJob?.cancel()
                                            draggedTagId = tag.id
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetY = reorderByDragOffset(
                                                items = displayedTags,
                                                draggedItem = tag,
                                                dragOffsetY = dragOffsetY + amount.y,
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
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(tag.color.toComposeColor(), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tag.name,
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "排序 ${index + 1}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        IconButton(
                            onClick = {
                                editingTag = tag
                                showEditor = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { deleteTarget = tag }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MiuixTheme.colorScheme.error
                            )
                        }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showEditor) {
        TagEditorDialog(
            tag = editingTag,
            error = error,
            onDismiss = { showEditor = false },
            onSave = { name, color ->
                viewModel.save(editingTag, name, color) { showEditor = false }
            }
        )
    }

    deleteTarget?.let { target ->
        LiquidAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除标签") },
            text = { Text("历史记录会保留“${target.name}”，它只会从以后可选标签中移除。") },
            confirmButton = {
                TextButton(
                    text = "删除",
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledColor = Color.Transparent,
                        disabledTextColor = MiuixTheme.colorScheme.error
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { deleteTarget = null },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun TagEditorDialog(
    tag: Tag?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(tag) { mutableStateOf(tag?.name.orEmpty()) }
    var color by remember(tag) { mutableStateOf(tag?.color ?: COLORS.first()) }

    LiquidAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "添加标签" else "编辑标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = "标签名称",
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    COLORS.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(if (color == option) 34.dp else 28.dp)
                                .background(option.toComposeColor(), CircleShape)
                                .clickable { color = option }
                                .then(
                                    if (color == option) {
                                        Modifier.padding(3.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
                if (error != null) {
                    Text(error, color = MiuixTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                text = "保存",
                onClick = { onSave(name, color) },
                colors = liquidDialogConfirmButtonColors()
            )
        },
        dismissButton = {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                colors = liquidDialogCancelButtonColors()
            )
        }
    )
}

private fun String.toComposeColor(): Color =
    runCatching { Color(toColorInt()) }.getOrDefault(Color.Gray)

private val COLORS = listOf(
    "#FF9800",
    "#9C27B0",
    "#E91E63",
    "#607D8B",
    "#4A90D9",
    "#009688"
)
