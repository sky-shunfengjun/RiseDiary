package com.risediary.app.ui.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import com.risediary.app.ui.components.StableNavigationBackHost
import com.risediary.app.ui.components.PageBackScope
import com.risediary.app.ui.components.PageBackHandler
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.components.HyperIslandNavigationMotion
import com.risediary.app.ui.components.PageTransitionLayer
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.risediary.app.ui.AppGateState
import com.risediary.app.ui.keepsMainContentMounted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Device tests for miuix-nav's entry state scopes, beyond the stack unit tests. */
class NavigationStateTest {
    @get:Rule val compose = createComposeRule()

    class DraftViewModel : ViewModel()

    @Test
    fun repeated_navigation_recreates_real_page_back_handlers_without_crashing() {
        lateinit var navigator: Navigator
        compose.setContent {
            MiuixTheme {
                navigator = rememberNavigator(Route.Main)
                MotionNavigationDisplay(navigator) { motion ->
                    motionEntry<Route.Main>(motion) {
                        PageBackHandler(enabled = false) {}
                        Text("main")
                    }
                    motionEntry<Route.About>(motion) {
                        PageBackHandler(onBack = navigator::pop)
                        Text("about")
                    }
                    motionEntry<Route.ThirdPartyLibs>(motion) {
                        PageBackHandler(onBack = navigator::pop)
                        Text("libraries")
                    }
                }
            }
        }
        repeat(25) {
            compose.runOnIdle { navigator.push(Route.About) }
            compose.runOnIdle { navigator.push(Route.ThirdPartyLibs) }
            compose.runOnIdle { navigator.pop() }
            compose.runOnIdle { navigator.pop() }
        }
        compose.runOnIdle { assertEquals(listOf(Route.Main), navigator.backStack.toList()) }
    }
    @Test
    fun draft_scroll_and_entry_viewmodel_survive_deeper_pages_and_lock_overlay() {
        lateinit var navigator: Navigator
        var originalViewModel: DraftViewModel? = null
        var restoredViewModel: DraftViewModel? = null
        lateinit var formScroll: ScrollState
        var originalScrollOffset = 0
        val gate = mutableStateOf(AppGateState.MAIN)
        val form = Route.RecordForm(true, 600_000L, 1234L)
        compose.setContent {
            MiuixTheme {
                navigator = rememberNavigator(Route.Main)
                if (keepsMainContentMounted(gate.value)) {
                    MotionNavigationDisplay(navigator) { motion ->
                        motionEntry<Route.Main>(motion) { Text("main") }
                        motionEntry<Route.RecordForm>(motion) { route ->
                            val vm: DraftViewModel = viewModel()
                            if (originalViewModel == null) originalViewModel = vm
                            restoredViewModel = vm
                            var draft by rememberSaveable { mutableStateOf("") }
                            val scroll = rememberScrollState()
                            formScroll = scroll
                            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                                Text("${route.duration}:${route.startTime}", Modifier.testTag("parameters"))
                                BasicTextField(draft, { draft = it }, Modifier.testTag("draft"))
                                repeat(100) { Text("row $it", Modifier.testTag("row$it")) }
                            }
                        }
                        motionEntry<Route.About>(motion) { Text("about") }
                        motionEntry<Route.ThirdPartyLibs>(motion) { Text("libraries") }
                    }
                    if (gate.value == AppGateState.LOCKED) Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.runOnIdle { navigator.push(form) }
        compose.onNodeWithTag("draft").performTextInput("unfinished draft")
        compose.runOnIdle { gate.value = AppGateState.LOCKED }
        compose.runOnIdle { gate.value = AppGateState.MAIN }
        compose.onNodeWithTag("draft").assertTextEquals("unfinished draft")
        compose.onNodeWithTag("row80").performScrollTo()
        compose.runOnIdle {
            originalScrollOffset = formScroll.value
            assertTrue(originalScrollOffset > 0)
        }
        compose.runOnIdle { navigator.push(Route.About) }
        compose.runOnIdle { navigator.push(Route.ThirdPartyLibs) }
        compose.runOnIdle { navigator.push(form) }
        compose.onNodeWithTag("draft").assertTextEquals("unfinished draft")
        compose.onNodeWithTag("parameters").assertTextEquals("600000:1234")
        compose.runOnIdle {
            assertSame(originalViewModel, restoredViewModel)
            assertEquals(originalScrollOffset, formScroll.value)
            assertEquals(listOf(Route.Main, form), navigator.backStack.toList())
        }
    }

    @Test
    fun saved_instance_state_restores_route_parameters_and_form_text() {
        val restoration = StateRestorationTester(compose)
        lateinit var navigator: Navigator
        restoration.setContent {
            MiuixTheme {
                navigator = rememberNavigator(Route.Main)
                MotionNavigationDisplay(navigator) { motion ->
                    motionEntry<Route.Main>(motion) { Text("main") }
                    motionEntry<Route.RecordForm>(motion) { route ->
                        var draft by rememberSaveable { mutableStateOf("") }
                        Column {
                            Text("${route.duration}:${route.startTime}", Modifier.testTag("parameters"))
                            BasicTextField(draft, { draft = it }, Modifier.testTag("draft"))
                        }
                    }
                }
            }
        }
        compose.runOnIdle { navigator.push(Route.RecordForm(true, 600_000L, 1234L)) }
        compose.onNodeWithTag("draft").performTextInput("restored draft")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("parameters").assertTextEquals("600000:1234")
        compose.onNodeWithTag("draft").assertTextEquals("restored draft")
    }
}

/** Exercise entry-state preservation through the renderer used in the application. */
@Composable
private fun MotionNavigationDisplay(
    navigator: Navigator,
    content: NavEntryBuilder.(HyperIslandNavigationMotion) -> Unit,
) {
    val keys = navigator.backStack.map { it as Route }
    val motion = remember { HyperIslandNavigationMotion(keys) }
    val transition = remember(motion, keys) { motion.updateStack(keys) }
    StableNavigationBackHost {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            NavDisplay(
                navigator.backStack,
                onBack = navigator::pop,
                transition = transition,
                effects = NavDisplayEffects(
                    cornerClipRadius = 24.dp,
                    cornerClipMode = NavCornerClipMode.All,
                    dimAmount = 0f,
                ),
            ) {
                content(motion)
            }
        }
    }
}

private inline fun <reified T : Route> NavEntryBuilder.motionEntry(
    motion: HyperIslandNavigationMotion,
    noinline content: @Composable (T) -> Unit,
) {
    entry<T> { route ->
        val scope = LocalNavTransitionScope.current
        val navigator = LocalNavigator.current
        val atFront by remember(scope) { derivedStateOf { scope.relativeDepth <= 0f } }
        PageBackScope(enabled = route == navigator.current() && atFront) {
            PageTransitionLayer(route, motion) { content(route) }
        }
    }
}
