package com.risediary.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.State
import com.risediary.app.ui.theme.RiseDiaryTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.risediary.app.ui.about.AboutScreen
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Navigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.update.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils

/** Run on a device; compilation alone does not count as UI acceptance. */
class UpdateSheetHostTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })
    private class Owner(override val navigationEventDispatcher: NavigationEventDispatcher) : NavigationEventDispatcherOwner

    @Test fun popupHostUsesUpdateTypographyAndReleasesUnderlyingTouches() = verifyPopupTypography("light", 1f)

    @Test fun darkLargeTextKeepsUpdateTypographyAndButtonBounds() = verifyPopupTypography("dark", 1.3f)

    private fun verifyPopupTypography(mode: String, fontScale: Float) {
        val vm = UpdateViewModel(ReleaseSource { release }, Prefs(), Downloads())
        var underlyingClicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            RiseDiaryTheme(themeMode = mode) {
                Box(Modifier.fillMaxSize()) {
                    Text("原页面", Modifier.clickable { underlyingClicks++ })
                    UpdateSheetHost(vm, false)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        }
        fun assertFont(text: String, size: Int) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(size.sp, layouts.single().layoutInput.style.fontSize)
        }
        compose.runOnIdle { vm.openAndCheck() }
        compose.waitUntil(10_000) { vm.ui.value.release != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第 1 条日志").fetchSemanticsNodes().isNotEmpty() }
        fun assertHeaderGeometry() {
            val sheet = compose.onNodeWithTag("update_sheet").fetchSemanticsNode().boundsInRoot
            val left = compose.onNodeWithTag("update_start_action").assertWidthIsEqualTo(48.dp).fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("update_end_action").assertWidthIsEqualTo(48.dp).fetchSemanticsNode().boundsInRoot
            val leftIcon = compose.onNodeWithTag("update_start_icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val rightIcon = compose.onNodeWithTag("update_end_icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val pxPerDp = compose.density.density
            assertEquals(12f * pxPerDp, left.left - sheet.left, 1f)
            assertEquals(12f * pxPerDp, sheet.right - right.right, 1f)
            assertEquals(left.center.x, leftIcon.center.x, 1f)
            assertEquals(left.center.y, leftIcon.center.y, 1f)
            assertEquals(right.center.x, rightIcon.center.x, 1f)
            assertEquals(right.center.y, rightIcon.center.y, 1f)
        }
        assertHeaderGeometry()
        assertFont("获取更新", 18)
        assertFont("有可用更新", 24)
        val statusIcon = compose.onNodeWithTag("update_status_icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val statusTitle = compose.onNodeWithTag("update_status_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(4f * compose.density.density, statusTitle.left - statusIcon.right, 1f)
        assertFont("下载更新", 16)
        assertFont("第 1 条日志", 14)
        compose.onNodeWithContentDescription("更新设置").performClick()
        assertHeaderGeometry()
        assertFont("更新设置", 18)
        assertFont("自动检查更新", 17)
        assertFont("官方", 12)
        compose.runOnIdle { vm.dismiss() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("更新设置").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("原页面").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, underlyingClicks) }
    }

    @Test fun unrelatedReleaseUpdatesKeepAlreadyParsedNotesAndNewBodyReparses() {
        var shownRelease by mutableStateOf(release)
        var parsed: MarkdownState? = null
        compose.setContent {
            val state = rememberReleaseNotesState(shownRelease)
            SideEffect { parsed = state }
        }
        compose.waitUntil(10_000) { parsed?.state?.value is State.Success }
        val original = parsed
        val successfulContent = parsed!!.state.value
        // A metadata/download UI refresh must not reset the existing document to Loading.
        compose.runOnIdle { shownRelease = shownRelease.copy(name = "Changed metadata") }
        compose.runOnIdle {
            assertSame(original, parsed)
            assertSame(successfulContent, parsed!!.state.value)
        }
        compose.runOnIdle { shownRelease = shownRelease.copy(body = "# 新日志\n\n新内容") }
        compose.waitUntil(10_000) { parsed !== original && parsed?.state?.value is State.Success }
        compose.runOnIdle {
            assertEquals("# 新日志\n\n新内容", (parsed!!.state.value as State.Success).content)
        }
    }
    @Test fun settingsBackKeepsTheSheetAndLockedAppHidesIt() {
        val owner = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { owner.addInput(it) }
        val blocked = mutableStateOf(false)
        val vm = UpdateViewModel(ReleaseSource { release }, Prefs(), Downloads())
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(owner)) {
                MiuixTheme {
                    Box(Modifier.fillMaxSize()) {
                        UpdateSheetHost(vm, blocked.value)
                        MiuixPopupUtils.MiuixPopupHost()
                    }
                }
            }
        }
        compose.runOnIdle { vm.openAndCheck() }
        compose.waitUntil(10_000) { vm.ui.value.release != null }
        compose.onNodeWithText("获取更新").assertIsDisplayed()
        compose.onNodeWithContentDescription("更新设置").performClick()
        compose.onNodeWithText("自动检查更新").assertIsDisplayed()
        compose.onNodeWithText("下载更新").assertDoesNotExist()
        compose.onNodeWithText("更新检查").assertIsDisplayed()
        compose.onNodeWithText("下载设置").assertIsDisplayed()
        compose.onNodeWithText("GitHub 下载线路").performClick()
        compose.onNodeWithText("7ED 代理").assertIsDisplayed()
        compose.runOnIdle { input.backCompleted() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("7ED 代理").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("返回更新内容").assertIsDisplayed()
        compose.onNodeWithText("GitHub 下载线路").performClick()
        compose.onNodeWithText("7ED 代理").performClick()
        compose.waitUntil(5_000) {
            vm.ui.value.settings.channel == UpdateChannel.PROXY_7ED &&
                compose.onAllNodesWithText("7ED 代理").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("7ED 代理").assertIsDisplayed()
        compose.runOnIdle { input.backCompleted() }
        compose.onNodeWithText("获取更新").assertIsDisplayed()
        compose.runOnIdle { blocked.value = true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("获取更新").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { blocked.value = false }
        compose.onNodeWithText("获取更新").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭更新抽屉").performClick()
        compose.waitUntil(5_000) { !vm.ui.value.visible }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("获取更新").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { owner.dispose() }
    }

    @Test fun scrollingLongNotesLeavesBothFooterButtonsVisible() {
        val vm = UpdateViewModel(ReleaseSource { release }, Prefs(), Downloads())
        compose.setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdateSheetHost(vm, false)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        compose.runOnIdle { vm.openAndCheck() }
        compose.waitUntil(10_000) { vm.ui.value.release != null }
        val notes = compose.onNodeWithTag("update_notes")
        val download = compose.onNodeWithText("下载更新")
        val viewport = notes.fetchSemanticsNode().boundsInRoot
        val button = download.fetchSemanticsNode().boundsInRoot
        assertTrue("正文应延伸到按钮下面", viewport.bottom >= button.bottom)
        assertTrue("正文应从原生标题栏下方开始",
            viewport.top >= compose.onNodeWithText("获取更新").fetchSemanticsNode().boundsInRoot.bottom)
        notes.performScrollToNode(hasText("日志末尾"))
        notes.performTouchInput { swipeUp() }
        compose.onNodeWithText("日志末尾").assertIsDisplayed()
        assertTrue("最后一行应能滚到按钮上方",
            compose.onNodeWithText("日志末尾").fetchSemanticsNode().boundsInRoot.bottom <=
                download.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithContentDescription("更新设置").performClick()
        compose.onNodeWithContentDescription("返回更新内容").performClick()
        compose.onNodeWithText("日志末尾").assertIsDisplayed()
        compose.onNodeWithText("下载更新").assertIsDisplayed()
        compose.onNodeWithContentDescription("打开此版本的 GitHub 发行页面").assertIsDisplayed()
        compose.runOnIdle { vm.dismiss() }
    }

    @Test fun reversingSettingsSlideKeepsNotesAndDoesNotAcceptHorizontalSwipes() {
        val vm = UpdateViewModel(ReleaseSource { release }, Prefs(), Downloads())
        compose.setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdateSheetHost(vm, false)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        compose.runOnIdle { vm.openAndCheck() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第 1 条日志").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("update_notes").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertFalse(vm.ui.value.settingsPage) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("更新设置").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithContentDescription("返回更新内容").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("第 1 条日志").assertIsDisplayed()
        compose.onNodeWithText("下载更新").assertIsDisplayed()
        compose.onNodeWithContentDescription("更新设置").performClick()
        compose.onNodeWithContentDescription("返回更新内容").performClick()
        compose.onNodeWithText("第 1 条日志").assertIsDisplayed()
        compose.runOnIdle { vm.dismiss() }
    }    private val release = GitHubRelease("v99.0.0", "Release",
        "https://github.com/sky-shunfengjun/RiseDiary/releases/tag/v99.0.0",
        "# 更新日志\n" + (1..100).joinToString("\n\n") { "第 $it 条日志" } + "\n\n日志末尾",
        listOf(GitHubAsset(5, "RiseDiary-v99.0.0.apk",
            "https://github.com/sky-shunfengjun/RiseDiary/releases/download/v99.0.0/RiseDiary-v99.0.0.apk", 1024)))

    @Test fun developerPasswordPopupSelectionAndResetPreserveOneNativeSheet() {
        val prefs = Prefs()
        val vm = UpdateViewModel(ReleaseSource { release }, prefs, Downloads())
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdateSheetHost(vm, false)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        compose.runOnIdle { vm.openDeveloper() }
        compose.onNodeWithText("开发人员调试").assertIsDisplayed()
        compose.onNodeWithTag("developer_password").performTextInput("1")
        compose.onNodeWithText("验证并进入").performClick()
        compose.onNodeWithText("密码错误，请重试").assertIsDisplayed()
        compose.onNodeWithTag("developer_password").performTextClearance()
        compose.onNodeWithTag("developer_password").performTextInput("1095102874")
        compose.onNodeWithText("验证并进入").performClick()
        compose.waitUntil(5_000) { vm.ui.value.settings.developerEnabled }
        compose.onNodeWithText("更新调试").assertIsDisplayed()
        compose.onNodeWithText("更新通道").performClick()
        compose.onNodeWithText("测试版").performClick()
        compose.waitUntil(5_000) { vm.ui.value.settings.releaseChannel == ReleaseChannel.PREVIEW }
        compose.onNodeWithText("恢复默认并关闭").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(vm.ui.value.settings.developerEnabled) }
        compose.onNodeWithText("恢复默认并关闭").performClick()
        compose.onNodeWithText("恢复并关闭").performClick()
        compose.waitUntil(5_000) { !vm.ui.value.developer.visible }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开发人员调试").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { vm.openDeveloper() }
        compose.onNodeWithTag("developer_password").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("验证并进入").assertIsDisplayed()
        compose.runOnIdle { vm.dismissDeveloper() }
    }
    @Test fun aboutTwentiethTapUnlocksPersistentEntryAndResetHidesItAgain() {
        val prefs = Prefs()
        val vm = UpdateViewModel(ReleaseSource { release }, prefs, Downloads())
        val navigator = Navigator(Route.Main)
        compose.setContent {
            RiseDiaryTheme {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    Box(Modifier.fillMaxSize()) {
                        AboutScreen(vm)
                        UpdateSheetHost(vm, false)
                        MiuixPopupUtils.MiuixPopupHost()
                    }
                }
            }
        }
        val icon = compose.onNodeWithTag("about_app_icon")
        repeat(19) { icon.performClick() }
        compose.runOnIdle { assertFalse(vm.ui.value.developer.visible) }
        icon.performClick()
        compose.onNodeWithText("开发人员调试").assertIsDisplayed()
        compose.runOnIdle { vm.verifyDeveloperPassword("1095102874") }
        compose.waitUntil(5_000) { vm.ui.value.settings.developerEnabled }
        compose.runOnIdle { vm.dismissDeveloper() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开发人员调试").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("about_list").performScrollToNode(hasText("调试菜单"))
        compose.onNodeWithText("调试菜单").performClick()
        compose.onNodeWithText("更新调试").assertIsDisplayed()
        compose.runOnIdle { vm.requestDeveloperReset(); vm.confirmDeveloperReset() }
        compose.waitUntil(5_000) { !vm.ui.value.settings.developerEnabled }
        compose.onNodeWithText("调试菜单").assertDoesNotExist()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开发人员调试").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("about_list").performScrollToIndex(0)
        repeat(19) { icon.performClick() }
        compose.runOnIdle { assertFalse(vm.ui.value.developer.visible) }
        icon.performClick()
        compose.onNodeWithText("验证并进入").assertIsDisplayed()
        compose.runOnIdle { vm.dismissDeveloper() }
    }

    @Test fun developerClosedWhileLockedDoesNotRetainTheRootSheet() {
        val vm = UpdateViewModel(ReleaseSource { release }, Prefs(), Downloads())
        val blocked = mutableStateOf(false)
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.fillMaxSize()) {
                    UpdateSheetHost(vm, blocked.value)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        compose.runOnIdle { vm.openDeveloper() }
        compose.onNodeWithText("开发人员调试").assertIsDisplayed()
        compose.runOnIdle { blocked.value = true }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开发人员调试").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { vm.dismissDeveloper(); blocked.value = false; vm.openAndCheck() }
        compose.onNodeWithText("获取更新").assertIsDisplayed()
        compose.onNodeWithText("开发人员调试").assertDoesNotExist()
        compose.runOnIdle { vm.dismiss() }
    }

    @Test fun updateSheetIgnoresOutsideTapsAndBodyDragsButHandleStillDismisses() = verifyDismissRoutes(false)

    @Test fun developerSheetIgnoresOutsideTapsAndBodyDragsButHandleStillDismisses() = verifyDismissRoutes(true)

    private fun verifyDismissRoutes(developer: Boolean) {
        val vm = UpdateViewModel(ReleaseSource { release.copy(body = "Short notes") }, Prefs(), Downloads())
        var underlyingClicks = 0
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.fillMaxSize().testTag("test_window")) {
                    Text("原页面", Modifier.clickable { underlyingClicks++ })
                    UpdateSheetHost(vm, false)
                    MiuixPopupUtils.MiuixPopupHost()
                }
            }
        }
        compose.runOnIdle { if (developer) vm.openDeveloper() else vm.openAndCheck() }
        if (!developer) compose.waitUntil(10_000) { vm.ui.value.release != null }
        val sheet = compose.onNodeWithTag("update_sheet")
        val original = sheet.fetchSemanticsNode().boundsInRoot
        val window = compose.onNodeWithTag("test_window")
        window.performTouchInput { click(Offset(original.center.x, original.top / 2f)) }
        compose.runOnIdle {
            assertTrue(if (developer) vm.ui.value.developer.visible else vm.ui.value.visible)
            assertEquals(0, underlyingClicks)
        }
        sheet.performTouchInput {
            swipe(Offset(width / 2f, 110.dp.toPx()), Offset(width / 2f, height * 0.85f), 450)
        }
        compose.runOnIdle {
            assertTrue(if (developer) vm.ui.value.developer.visible else vm.ui.value.visible)
        }
        assertEquals(original.top, sheet.fetchSemanticsNode().boundsInRoot.top, 1f)
        // The native 24dp handle strip remains the only draggable dismissal surface.
        sheet.performTouchInput {
            swipe(Offset(width / 2f, 12.dp.toPx()), Offset(width / 2f, height * 0.85f), 450)
        }
        compose.waitUntil(5_000) {
            if (developer) !vm.ui.value.developer.visible else !vm.ui.value.visible
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("update_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("原页面").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, underlyingClicks) }
    }
    @Test fun darkSheetIsGrayAndBothGlassButtonsChangeWithUnderlyingNotes() {
        fun notes(glyph: String) = (glyph.repeat(60) + "\n\n").repeat(40)
        var body = notes("文")
        val vm = UpdateViewModel(ReleaseSource { release.copy(body = body) }, Prefs(), Downloads())
        compose.setContent {
            RiseDiaryTheme(themeMode = "dark") {
                Box(Modifier.fillMaxSize()) { UpdateSheetHost(vm, false) }
            }
        }
        fun awaitNotes(glyph: String) {
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(glyph.repeat(60), substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
        }
        compose.runOnIdle { vm.openAndCheck() }
        awaitNotes("文")
        val sheetPixels = compose.onNodeWithTag("update_sheet").captureToImage().toPixelMap()
        val background = sheetPixels[sheetPixels.width / 4, (12f * compose.density.density).toInt()]
        assertEquals(0x24 / 255f, background.red, 0.01f)
        assertEquals(background.red, background.green, 0.01f)
        assertEquals(background.red, background.blue, 0.01f)
        val tags = listOf("update_download_action", "update_github_action")
        val before = tags.map { compose.onNodeWithTag(it).captureToImage().toPixelMap() }
        compose.runOnIdle { body = notes("日"); vm.openAndCheck() }
        awaitNotes("日")
        tags.forEachIndexed { index, tag ->
            val after = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            val prior = before[index]
            assertEquals(prior.width, after.width)
            assertEquals(prior.height, after.height)
            // Sample away from each button's unchanged label/icon and capsule edge.
            var changed = 0
            var samples = 0
            for (x in (after.width * 0.12f).toInt()..(after.width * 0.28f).toInt()) {
                for (y in (after.height * 0.35f).toInt()..(after.height * 0.65f).toInt()) {
                    val a = prior[x, y]
                    val b = after[x, y]
                    if (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) +
                        kotlin.math.abs(a.blue - b.blue) > 0.025f) changed++
                    samples++
                }
            }
            assertTrue("$tag must sample the changing document, not just a solid fill", changed > samples / 50)
        }
        compose.runOnIdle { vm.dismiss() }
    }
    private class Prefs : UpdatePreferences {
        override val settings = MutableStateFlow(UpdateSettings(automaticCheck = false))
        override val downloadRecord = MutableStateFlow<DownloadRecord?>(null)
        override suspend fun setAutomaticCheck(enabled: Boolean) { settings.value = settings.value.copy(automaticCheck = enabled) }
        override suspend fun setChannel(channel: UpdateChannel) { settings.value = settings.value.copy(channel = channel) }
        override suspend fun setForceCheck(enabled: Boolean) { settings.value = settings.value.copy(forceCheck = enabled) }
        override suspend fun setReleaseChannel(channel: ReleaseChannel) { settings.value = settings.value.copy(releaseChannel = channel) }
        override suspend fun setDeveloperEnabled(enabled: Boolean) { settings.value = settings.value.copy(developerEnabled = enabled) }
        override suspend fun restoreDeveloperDefaults() { settings.value = settings.value.copy(forceCheck = false, releaseChannel = ReleaseChannel.STABLE, developerEnabled = false) }
        override suspend fun saveDownload(record: DownloadRecord?) { downloadRecord.value = record }
    }
    private class Downloads : UpdateDownloads {
        override suspend fun enqueue(release: GitHubRelease, asset: GitHubAsset, channel: UpdateChannel) = error("not used")
        override suspend fun query(record: DownloadRecord) = DownloadState.Ready(record)
        override suspend fun cancel(record: DownloadRecord) = false
        override suspend fun verifyForInstall(record: DownloadRecord) = error("not used")
    }
}
