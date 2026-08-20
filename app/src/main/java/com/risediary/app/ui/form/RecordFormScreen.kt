package com.risediary.app.ui.form

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.R
import com.risediary.app.ui.Screen
import com.risediary.app.ui.achievement.AchievementCatalog
import com.risediary.app.ui.components.DurationPickerBottomSheet
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.LiquidSingleDatePickerDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.WheelColumn
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.util.RecordValidation
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f),
                height = 56.dp,
                highlightIntensity = 0.38f,
                highlightRadiusMultiplier = 1f,
                pressExpansion = 2.dp
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(22.dp))
                Text(
                    if (vm.isSaving) "正在保存" else "保存记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormSectionTitle(
                        icon = Icons.Default.Tune,
                        title = "时间与用时",
                        subtitle = "确认开始时间和本次用时"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactValueButton(
                            icon = Icons.Default.CalendarMonth,
                            text = formatDateOnly(vm.startTime),
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        CompactValueButton(
                            icon = Icons.Default.Schedule,
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
                        icon = Icons.Default.WaterDrop,
                        title = "记录数据",
                        subtitle = stringResource(R.string.form_data_subtitle)
                    )
                    Text(
                        text = stringResource(R.string.form_quick_input_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "精液量",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        VolumeModeSelector(
                            useSpurtMode = vm.useSpurtMode,
                            onSelectSpurtMode = { shouldUseSpurt ->
                                if (shouldUseSpurt != vm.useSpurtMode) vm.toggleSpurtMode()
                            }
                        )

                        if (vm.useSpurtMode) {
                            OutlinedTextField(
                                value = vm.spurtCount,
                                onValueChange = vm::setSpurtCountInput,
                                label = { Text("射出股数") },
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
                                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            QuickChoices(
                                values = listOf(1, 3, 5, 8, 10),
                                suffix = "股",
                                selected = vm.spurtCount,
                                onClick = vm::quickSpurt
                            )
                        } else {
                            OutlinedTextField(
                                value = vm.volumeMl,
                                onValueChange = vm::setVolumeInput,
                                label = { Text("精液量（毫升）") },
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
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null) },
                                shape = RoundedCornerShape(16.dp),
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = vm.distanceCm,
                            onValueChange = vm::setDistanceInput,
                            label = { Text("距离（厘米，可不填）") },
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
                            leadingIcon = { Icon(Icons.Default.Straighten, null) },
                            shape = RoundedCornerShape(16.dp),
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
                        icon = Icons.Default.EditNote,
                        title = "补充信息",
                        subtitle = "标签和备注可按需填写"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "方式标签（可选）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (tags.isEmpty()) {
                            Text(
                                "暂无标签，可在设置中添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                tags.forEach { tag ->
                                    FilterChip(
                                        onClick = { vm.toggleTag(tag.name) },
                                        label = { Text(tag.name) },
                                        selected = tag.name in vm.selectedTags,
                                        leadingIcon = if (tag.name in vm.selectedTags) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null,
                                                    Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = vm.moodNote,
                        onValueChange = { vm.moodNote = it },
                        label = { Text("备注与心情") },
                        placeholder = { Text("例如：状态、感受或需要留意的情况") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                focusedFieldRequester =
                                    if (focusState.isFocused) noteFieldRequester else null
                            }
                            .bringIntoViewRequester(noteFieldRequester)
                            .heightIn(min = 104.dp),
                        leadingIcon = { Icon(Icons.Default.Notes, null) },
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            vm.errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(92.dp))
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
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
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
                TextButton(onClick = vm::consumeAchievement) {
                    Text("知道了")
                }
            },
            title = {
                Text(
                    "成就解锁",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}
