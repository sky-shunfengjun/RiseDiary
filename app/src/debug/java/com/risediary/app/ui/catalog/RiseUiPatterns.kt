/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.catalog

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.data.entity.Flight
import com.risediary.app.ui.components.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseDiaryTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Debug source set only: fake data and local state, no database, routes or ViewModels. */
enum class UiPatternKind(val title: String) { Settings("设置"), Form("表单"), Content("内容"), Statistics("统计") }

@Composable
fun RiseUiPattern(kind: UiPatternKind, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    when (kind) {
        UiPatternKind.Settings -> SettingsPattern(modifier, onBack)
        UiPatternKind.Form -> FormPattern(modifier, onBack)
        UiPatternKind.Content -> ContentPattern(modifier, onBack)
        UiPatternKind.Statistics -> StatisticsPattern(modifier, onBack)
    }
}

@Composable
private fun SettingsPattern(modifier: Modifier, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    RisePatternPage("设置样板", onBack, modifier,
        actions = listOf(RiseTopAction("新增", AppIcons.Add, { dialog = "普通确认" }))) {
        RisePreferenceGroup("个人偏好", Modifier.testTag("pattern-group")) {
            RisePreferenceLink("记录方式", { dialog = "普通确认" }, summary = "按你的习惯选择", icon = AppIcons.Notes, reserveIconSpace = true)
            RisePreferenceLink("其他偏好", { dialog = "普通确认" }, summary = "没有图标时仍保留文字起点", reserveIconSpace = true)
        }
        RisePreferenceGroup("提醒") {
            RiseTogglePreference("每日提醒", "可在之后调整", enabled, { enabled = it }, icon = AppIcons.Alarm)
        }
        RisePreferenceGroup("弹窗示例") {
            listOf("普通确认", "危险确认", "三按钮", "提交中").forEach { name ->
                RisePreferenceLink(name, { dialog = name })
            }
        }
    }
    dialog?.let { mode ->
        LiquidAlertDialog({ dialog = null },
            RiseDialogAction(if (mode == "危险确认") "删除" else "确认", { dialog = null },
                if (mode == "危险确认") DialogActionTone.Destructive else DialogActionTone.Normal, mode != "提交中"),
            title = { Text(mode) }, text = { Text("这是一条虚构的示例，不会修改真实数据。") },
            dismissAction = RiseDialogAction("取消", { dialog = null }, DialogActionTone.Normal),
            neutralAction = if (mode == "三按钮") RiseDialogAction("放弃", { dialog = null }, DialogActionTone.Destructive) else null)
    }
}

@Composable
private fun FormPattern(modifier: Modifier, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf("分类" to "日常记录", "发生时间" to "2026-11-02 16:00", "地点" to "", "标签" to "示例标签") }
    var submitted by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    RisePatternPage("填写样板", onBack, modifier,
        bottomAction = { backdrop -> RisePrimaryAction("保存", { submitted = true; saved = name.isNotBlank() },
            backdrop, Modifier.testTag("pattern-save")) }) {
        RisePreferenceGroup("基本信息") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RiseLabeledField("名称", name, { name = it; saved = false }, error = if (submitted && name.isBlank()) "请填写名称" else null, placeholder = "输入便于识别的名称")
                RiseLabeledField("备注", note, { note = it }, singleLine = false, minLines = 3, maxLines = 6, placeholder = "补充这次记录的细节（可选）")
            }
        }
        if (saved) Text("已保存", Modifier.testTag("pattern-saved"))
        RisePreferenceGroup("记录详情") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("分类", "发生时间", "地点", "标签").forEach { label ->
                    RiseLabeledField(label, fields[label].orEmpty(), { fields[label] = it }, placeholder = "填写$label")
                }
            }
        }
        RisePreferenceGroup("后续提醒", Modifier.testTag("pattern-last-field")) {
            RiseTogglePreference("稍后提醒", "这些示例只保存在当前预览中", reminder, { reminder = it }, icon = AppIcons.Alarm)
        }
    }
}

@Composable
private fun ContentPattern(modifier: Modifier, onBack: () -> Unit) {
    var items by remember { mutableStateOf(listOf("一个很长的示例名称，用于检查单行省略以及完整名称阅读")) }
    var detail by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    RisePatternPage("内容样板", onBack, modifier, actions = listOf(
        RiseTopAction("新增", AppIcons.Add, { items = items + "新条目 ${items.size + 1}" }),
        RiseTopAction("模拟错误", AppIcons.Info, { error = true }),
        RiseTopAction("清空示例", AppIcons.Delete, { items = emptyList() })
    )) {
        if (items.isEmpty()) RiseEmptyState("暂无内容，点右上角加号添加。", Modifier.testTag("pattern-empty"))
        else items.forEach { name ->
            RiseListItem(name, { detail = name }, Modifier.testTag("pattern-list-item"),
                summary = "示例条目", icon = AppIcons.Video, reserveIconSpace = true) {
                Icon(AppIcons.ChevronRight, null, Modifier.size(20.dp))
            }
        }
        if (error) InlineStatusContent(listOf("暂时无法读取，请重试。"), onRetry = { error = false })
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).testTag("pattern-content-viewport"),
            contentAlignment = Alignment.Center) { Text("内容展示区域", color = Color.White) }
        Text("这里直接展示主体内容，相关操作仍使用公共组件。")
    }
    detail?.let { fullName -> LiquidAlertDialog({ detail = null },
        RiseDialogAction("关闭", { detail = null }, DialogActionTone.Normal),
        title = { Text("条目详情") }, text = { Text(fullName) }) }
}

@Composable
private fun StatisticsPattern(modifier: Modifier, onBack: () -> Unit) {
    val flights = remember {
        listOf(1f, 2.5f, 1.8f, 3f).mapIndexed { index, volume ->
            val start = 1_795_737_600_000L + index * 86_400_000L
            Flight(id = index.toLong() + 1, startTime = start, endTime = start + 300_000, durationSeconds = 300,
                spurtCount = null, semenVolumeMl = volume, ejaculationDistanceCm = null, methodTags = "[]", moodNote = "",
                createdAt = start, updatedAt = start, globalId = "sample-$index")
        }
    }
    RisePatternPage("统计样板", onBack, modifier) {
        RisePreferenceGroup("概览") {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RiseMetric("4", "次", "示例次数")
                RiseMetric("2.1", "ml", "示例均值")
            }
        }
        RisePreferenceGroup("趋势") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("最近四次", maxLines = 1, overflow = TextOverflow.Ellipsis)
                TrendColumnChart(flights, true, Modifier.fillMaxWidth().height(200.dp).testTag("pattern-chart"))
            }
        }
    }
}

/** Called inside the existing authorized developer menu. Release provides an empty counterpart. */
@Composable
fun UiPatternMenuEntry(enabled: Boolean) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) show = false }
    RisePreferenceGroup("界面开发") {
        RisePreferenceLink("UI 样板", { show = true }, summary = "四类样板、主题与字号预览", icon = AppIcons.Palette, enabled = enabled)
    }
    if (show) Dialog({ show = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        UiPatternCatalog { show = false }
    }
}

@Composable
fun UiPatternCatalog(onClose: () -> Unit) {
    var kind by remember { mutableStateOf(UiPatternKind.Settings) }
    var dark by remember { mutableStateOf(false) }
    var fontStep by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    val multiplier = listOf(1f, 1.6f, 2f)[fontStep]
    val originalDensity = LocalDensity.current
    RiseDiaryTheme(if (dark) "dark" else "light") {
        val host = rememberLiquidDialogHostState()
        val surface by rememberUpdatedState(MiuixTheme.colorScheme.surface)
        val backdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
        ProvideLiquidDialogHost(host) {
            Box(Modifier.fillMaxSize().background(surface)) {
                Column(Modifier.fillMaxSize().layerBackdrop(backdrop).statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("UI 样板 · ${kind.title}", Modifier.weight(1f), style = MiuixTheme.textStyles.title4)
                        IconButton({ options = true }, Modifier.size(48.dp).testTag("pattern-preview-options")) {
                            Icon(AppIcons.Palette, "预览设置", Modifier.size(22.dp))
                        }
                        IconButton(onClose, Modifier.size(48.dp)) { Icon(AppIcons.Close, "关闭样板", Modifier.size(22.dp)) }
                    }
                    CompositionLocalProvider(
                        LocalDensity provides Density(originalDensity.density, originalDensity.fontScale * multiplier),
                        LocalRisePageInsets provides WindowInsets(0, 0, 0, 0)
                    ) {
                        key(kind) { RiseUiPattern(kind, Modifier.weight(1f), onBack = onClose) }
                    }
                }
                if (options) LiquidAlertDialog(
                    { options = false }, RiseDialogAction("完成", { options = false }, DialogActionTone.Normal),
                    title = { Text("预览设置") }, text = {
                        Column(Modifier.heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiPatternKind.entries.forEach { item ->
                                RisePreferenceLink(item.title, { kind = item }, summary = if (item == kind) "当前样板" else null)
                            }
                            RisePreferenceLink("主题", { dark = !dark }, summary = if (dark) "深色" else "浅色")
                            RisePreferenceLink("字号", { fontStep = (fontStep + 1) % 3 }, summary = "×$multiplier")
                        }
                    })
                LiquidDialogHost(host, backdrop)
            }
        }
    }
}

@Preview(name = "设置-浅色", widthDp = 360, heightDp = 800)
@Composable private fun SettingsPreview() = RiseDiaryTheme("light") { RiseUiPattern(UiPatternKind.Settings) }
@Preview(name = "填写-深色", widthDp = 360, heightDp = 800)
@Composable private fun FormPreview() = RiseDiaryTheme("dark") { RiseUiPattern(UiPatternKind.Form) }
@Preview(name = "内容-窄屏", widthDp = 320, heightDp = 640)
@Composable private fun ContentPreview() = RiseDiaryTheme("light") { RiseUiPattern(UiPatternKind.Content) }
@Preview(name = "统计-横屏", widthDp = 640, heightDp = 360)
@Composable private fun StatisticsPreview() = RiseDiaryTheme("dark") { RiseUiPattern(UiPatternKind.Statistics) }
