package com.risediary.app.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.Screen
import com.risediary.app.ui.achievement.AchievementCatalog
import com.risediary.app.ui.components.DurationPickerBottomSheet
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.ui.components.LiquidSingleDatePickerDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.WheelColumn
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.util.RecordValidation
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
fun RecordFormScreen(
    navController: NavController,
    isTimer: Boolean = false,
    durationMillis: Long = 0L,
    timerStartTimeMillis: Long = 0L,
    flightId: Long? = null,
    vm: FormViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }

    val formScope = rememberCoroutineScope()
    val spurtFieldRequester = remember { BringIntoViewRequester() }
    val volumeFieldRequester = remember { BringIntoViewRequester() }
    val distanceFieldRequester = remember { BringIntoViewRequester() }
    val noteFieldRequester = remember { BringIntoViewRequester() }
    var focusedFieldRequester by remember { mutableStateOf<BringIntoViewRequester?>(null) }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0.dp) {
            focusedFieldRequester?.let { formScope.launch { it.bringIntoView() } }
        }
    }

    LaunchedEffect(isTimer, durationMillis, timerStartTimeMillis, flightId) {
        when {
            flightId != null -> vm.initForEdit(flightId)
            isTimer -> vm.initFromTimer(durationMillis, timerStartTimeMillis)
            else -> vm.initDirect()
        }
    }

    val activeAchievement = vm.newAchievementKeys.firstOrNull()
    LaunchedEffect(vm.saved, activeAchievement) {
        if (vm.saved && activeAchievement == null) {
            if (flightId != null) {
                navController.popBackStack()
            } else {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    SecondaryPageScaffold(
        title = if (flightId == null) "新建记录" else "编辑记录",
        onBack = { navController.navigateUp() },
        reserveBottomActionSpace = false,
        bottomAction = { backdrop ->
            LiquidGlassButton(
                onClick = {
                    if (!vm.isSaving && !vm.isLoading) vm.save()
                },
                backdrop = backdrop,
                modifier = Modifier.widthIn(min = 184.dp, max = 224.dp),
                isInteractive = !vm.isSaving && !vm.isLoading,
                enabled = !vm.isSaving && !vm.isLoading,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.075f),
                height = 56.dp,
                highlightIntensity = 0.38f,
                highlightRadiusMultiplier = 1f,
                pressExpansion = 2.dp
            ) {
                Icon(AppIcons.Save, null, Modifier.size(22.dp))
                Text(
                    if (vm.isSaving) "正在保存" else "保存记录",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(
                    start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = if (imeBottom > 0.dp) {
                        0.dp
                    } else {
                        innerPadding.calculateBottomPadding()
                    }
                )
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormSectionTitle(
                        icon = AppIcons.Tune,
                        title = "时间与用时",
                        subtitle = "确认开始时间和本次用时"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactValueButton(
                            icon = AppIcons.CalendarMonth,
                            text = formatDateOnly(vm.startTime),
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactValueButton(
                            icon = AppIcons.Schedule,
                            text = formatTimeOnly(vm.startTime),
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DurationValueButton(
                        durationSeconds = vm.durationSeconds,
                        hasLegacyDuration = vm.hasLegacyDuration,
                        onClick = { showDurationPicker = true }
                    )
                }
            }

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormSectionTitle(
                        icon = AppIcons.WaterDrop,
                        title = "记录数据",
                        subtitle = stringResource(R.string.form_data_subtitle)
                    )
                    Text(
                        text = stringResource(R.string.form_quick_input_note),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "精液量",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        VolumeModeSelector(
                            useSpurtMode = vm.useSpurtMode,
                            onSelectSpurtMode = { shouldUseSpurt ->
                                if (shouldUseSpurt != vm.useSpurtMode) vm.toggleSpurtMode()
                            }
                        )

                        if (vm.useSpurtMode) {
                            TextField(
                                value = vm.spurtCount,
                                onValueChange = vm::setSpurtCountInput,
                                label = "射出股数",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        focusedFieldRequester =
                                            if (focusState.isFocused) spurtFieldRequester else null
                                    }
                                    .bringIntoViewRequester(spurtFieldRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                cornerRadius = 16.dp,
                                singleLine = true
                            )
                            QuickChoices(
                                values = listOf(1, 3, 5, 8, 10),
                                suffix = "股",
                                selected = vm.spurtCount,
                                onClick = vm::quickSpurt
                            )
                        } else {
                            TextField(
                                value = vm.volumeMl,
                                onValueChange = vm::setVolumeInput,
                                label = "精液量（毫升）",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        focusedFieldRequester =
                                            if (focusState.isFocused) volumeFieldRequester else null
                                    }
                                    .bringIntoViewRequester(volumeFieldRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal
                                ),
                                cornerRadius = 16.dp,
                                singleLine = true
                            )
                            QuickChoices(
                                values = listOf(1, 3, 5, 10),
                                suffix = "ml",
                                selected = vm.volumeMl.removeSuffix(".0"),
                                onClick = vm::quickVolume
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "最远射出距离",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextField(
                            value = vm.distanceCm,
                            onValueChange = vm::setDistanceInput,
                            label = "距离（厘米，可不填）",
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    focusedFieldRequester =
                                        if (focusState.isFocused) distanceFieldRequester else null
                                }
                                .bringIntoViewRequester(distanceFieldRequester),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            cornerRadius = 16.dp,
                            singleLine = true
                        )
                        QuickChoices(
                            values = listOf(10, 30, 50, 80, 100),
                            suffix = "cm",
                            selected = vm.distanceCm,
                            onClick = vm::quickDistance
                        )
                    }
                }
            }

            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormSectionTitle(
                        icon = AppIcons.EditNote,
                        title = "补充信息",
                        subtitle = "标签和备注可按需填写"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "方式标签（可选）",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (tags.isEmpty()) {
                            Text(
                                "暂无标签，可在设置中添加",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                tags.forEach { tag ->
                                    val tagSelected = tag.name in vm.selectedTags
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { vm.toggleTag(tag.name) }
                                            .padding(end = 10.dp)
                                    ) {
                                        Checkbox(
                                            state = if (tagSelected) {
                                                ToggleableState.On
                                            } else {
                                                ToggleableState.Off
                                            },
                                            onClick = { vm.toggleTag(tag.name) }
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            tag.name,
                                            style = MiuixTheme.textStyles.body2.copy(
                                                fontWeight = if (tagSelected) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            ),
                                            color = if (tagSelected) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextField(
                        value = vm.moodNote,
                        onValueChange = { vm.moodNote = it },
                        label = "备注与心情",
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                focusedFieldRequester =
                                    if (focusState.isFocused) noteFieldRequester else null
                            }
                            .bringIntoViewRequester(noteFieldRequester)
                            .heightIn(min = 104.dp),
                        maxLines = 5,
                        cornerRadius = 16.dp
                    )
                }
            }

            vm.errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        error,
                        color = MiuixTheme.colorScheme.onErrorContainer,
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }
            Spacer(
                modifier = Modifier.height(
                    recordFormBottomSpacerDp(imeBottom > 0.dp).dp
                )
            )
        }
    }

    if (showDurationPicker) {
        DurationPickerBottomSheet(
            totalSeconds = vm.durationSeconds.coerceAtMost(
                RecordValidation.MAX_DURATION_SECONDS
            ),
            onConfirm = {
                vm.updateDurationSeconds(it)
                showDurationPicker = false
            },
            onDismiss = { showDurationPicker = false }
        )
    }

    if (showDatePicker) {
        val initialDate = Instant.ofEpochMilli(vm.startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        LiquidSingleDatePickerDialog(
            initialDate = initialDate,
            onDismissRequest = { showDatePicker = false },
            onConfirm = { pickedDate ->
                val oldTime = Instant.ofEpochMilli(vm.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                vm.updateStartTime(
                    pickedDate.atTime(oldTime)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        val oldTime = Instant.ofEpochMilli(vm.startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        var pickedHour by remember { mutableStateOf(oldTime.hour) }
        var pickedMinute by remember { mutableStateOf(oldTime.minute) }

        LiquidAlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    text = "确定",
                    onClick = {
                        val currentDate = Instant.ofEpochMilli(vm.startTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        vm.updateStartTime(
                            currentDate.atTime(LocalTime.of(pickedHour, pickedMinute))
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        )
                        showTimePicker = false
                    },
                    colors = liquidDialogConfirmButtonColors()
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { showTimePicker = false },
                    colors = liquidDialogCancelButtonColors()
                )
            },
            title = {
                Text(
                    "选择开始时间",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelColumn(
                        label = "时",
                        range = 0..23,
                        value = pickedHour,
                        onValueChange = { pickedHour = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelColumn(
                        label = "分",
                        range = 0..59,
                        value = pickedMinute,
                        onValueChange = { pickedMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }

    if (activeAchievement != null) {
        val key = activeAchievement
        val definition = AchievementCatalog.find(key)
        val icon = definition?.icon ?: "🏆"
        val name = definition?.name ?: key
        LaunchedEffect(activeAchievement) {
            kotlinx.coroutines.delay(3_000)
            vm.consumeAchievement()
        }
        LiquidAlertDialog(
            onDismissRequest = vm::consumeAchievement,
            confirmButton = {
                TextButton(
                    text = "知道了",
                    onClick = vm::consumeAchievement,
                    colors = liquidDialogConfirmButtonColors()
                )
            },
            title = {
                Text(
                    "成就解锁",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icon, fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        name,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}

internal fun recordFormBottomSpacerDp(imeVisible: Boolean): Int =
    if (imeVisible) 0 else 68
