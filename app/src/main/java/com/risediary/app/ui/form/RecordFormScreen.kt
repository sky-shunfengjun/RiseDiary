package com.risediary.app.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.ui.LocalMainPagerState
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.achievement.AchievementCatalog
import com.risediary.app.ui.components.DurationPickerBottomSheet
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.ui.components.LiquidSingleDatePickerDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.util.RecordValidation
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
fun RecordFormScreen(
    isTimer: Boolean = false,
    durationMillis: Long = 0L,
    timerStartTimeMillis: Long = 0L,
    flightId: Long? = null,
    vm: FormViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
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
    val mainPagerState = LocalMainPagerState.current
    LaunchedEffect(vm.saved, activeAchievement) {
        if (vm.saved && activeAchievement == null) {
            // Pop exactly one level so a manual draft form pushed below the
            // timer form is never destroyed by a popUntil(Main).
            if (navigator.backStackSize() > 1) {
                navigator.pop()
            }
            if (flightId == null) {
                mainPagerState?.animateToPage(0)
            }
        }
    }

    SecondaryPageScaffold(
        title = if (flightId == null) {
            stringResource(R.string.form_new_record)
        } else {
            stringResource(R.string.form_edit_record)
        },
        onBack = { navigator.pop() },
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
                    if (vm.isSaving) {
                        stringResource(R.string.form_saving)
                    } else {
                        stringResource(R.string.form_save_record)
                    },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { innerPadding ->
        val formContentPadding = recordFormContentPadding(
            innerPadding,
            imeVisible = imeBottom > 0.dp
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(formContentPadding)
                .consumeWindowInsets(formContentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormSectionTitle(
                        icon = AppIcons.Tune,
                        title = stringResource(R.string.form_time_section),
                        subtitle = stringResource(R.string.form_time_section_subtitle)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactValueButton(
                            icon = AppIcons.CalendarMonth,
                            text = formatDateOnly(context, vm.startTime),
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
                        title = stringResource(R.string.form_data_section),
                        subtitle = stringResource(R.string.form_data_subtitle)
                    )
                    Text(
                        text = stringResource(R.string.form_quick_input_note),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.form_volume_label),
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
                                label = stringResource(R.string.form_spurt_count_label),
                                leadingIcon = {
                                    Icon(
                                        AppIcons.Numbers,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 10.dp)
                                            .size(20.dp)
                                    )
                                },
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
                                suffix = stringResource(R.string.form_spurt_suffix),
                                selected = vm.quickSpurtSelection,
                                onClick = vm::quickSpurt
                            )
                        } else {
                            TextField(
                                value = vm.volumeMl,
                                onValueChange = vm::setVolumeInput,
                                label = stringResource(R.string.form_volume_ml_label),
                                leadingIcon = {
                                    Icon(
                                        AppIcons.WaterDrop,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(start = 16.dp, end = 10.dp)
                                            .size(20.dp)
                                    )
                                },
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
                                selected = vm.quickVolumeSelection,
                                onClick = vm::quickVolume
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.form_max_distance_label),
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextField(
                            value = vm.distanceCm,
                            onValueChange = vm::setDistanceInput,
                            label = stringResource(R.string.form_distance_label),
                            leadingIcon = {
                                Icon(
                                    AppIcons.Straighten,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 16.dp, end = 10.dp)
                                        .size(20.dp)
                                )
                            },
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
                            values = listOf(10, 30, 50, 80),
                            suffix = "cm",
                            selected = vm.quickDistanceSelection,
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
                        title = stringResource(R.string.form_extra_section),
                        subtitle = stringResource(R.string.form_extra_section_subtitle)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.form_method_tags_label),
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (tags.isEmpty()) {
                            Text(
                                stringResource(R.string.form_no_tags),
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
                        label = stringResource(R.string.form_note_label),
                        leadingIcon = {
                            Icon(
                                AppIcons.Notes,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 10.dp)
                                    .size(20.dp)
                            )
                        },
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
        val hourFormat = stringResource(R.string.form_hour_format)
        val minuteFormat = stringResource(R.string.form_minute_format)

        LiquidAlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.action_confirm),
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
                    text = stringResource(R.string.action_cancel),
                    onClick = { showTimePicker = false },
                    colors = liquidDialogCancelButtonColors()
                )
            },
            title = {
                Text(
                    stringResource(R.string.form_pick_start_time),
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
                    NumberPicker(
                        value = pickedHour,
                        onValueChange = { pickedHour = it },
                        range = 0..23,
                        label = { hourFormat.format(it) },
                        visibleItemCount = 3,
                        wrapAround = false,
                        textStyle = MiuixTheme.textStyles.title2,
                        modifier = Modifier.weight(1f)
                    )
                    NumberPicker(
                        value = pickedMinute,
                        onValueChange = { pickedMinute = it },
                        range = 0..59,
                        label = { minuteFormat.format(it) },
                        visibleItemCount = 3,
                        wrapAround = false,
                        textStyle = MiuixTheme.textStyles.title2,
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
        val name = definition?.let { stringResource(it.nameRes) } ?: key
        LaunchedEffect(activeAchievement) {
            kotlinx.coroutines.delay(3_000)
            vm.consumeAchievement(key)
        }
        LiquidAlertDialog(
            onDismissRequest = { vm.consumeAchievement(key) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.form_got_it),
                    onClick = { vm.consumeAchievement(key) },
                    colors = liquidDialogConfirmButtonColors()
                )
            },
            title = {
                Text(
                    stringResource(R.string.form_achievement_unlocked),
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

internal fun recordFormContentPadding(
    innerPadding: PaddingValues,
    imeVisible: Boolean
): PaddingValues = PaddingValues(
    start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
    top = innerPadding.calculateTopPadding(),
    end = innerPadding.calculateRightPadding(LayoutDirection.Ltr),
    bottom = if (imeVisible) 0.dp else innerPadding.calculateBottomPadding()
)

internal fun recordFormBottomSpacerDp(imeVisible: Boolean): Int =
    if (imeVisible) 0 else 68
