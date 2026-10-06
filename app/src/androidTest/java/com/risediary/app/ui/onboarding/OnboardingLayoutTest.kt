package com.risediary.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compiled with the device suite; execution requires a phone/emulator. */
class OnboardingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowLargeTextStatementCanScrollToConsentAndReadBothDocuments() {
        var reads = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                RiseDiaryTheme {
                    var accepted by remember { mutableStateOf(false) }
                    Box(Modifier.size(280.dp, 420.dp)) {
                        StatementOnboardingPage(accepted, true, rememberScrollState(), rememberLayerBackdrop(),
                            { accepted = it }, { reads++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("oobe_agreement").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("使用协议").performScrollTo().performClick()
        compose.onNodeWithText("隐私说明").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, reads) }
    }

    @Test fun exitingStatementDoesNotRespondToConsentOrReaderClicks() {
        var operations = 0
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.size(320.dp, 500.dp)) {
                    StatementOnboardingPage(false, false, rememberScrollState(), rememberLayerBackdrop(),
                        { operations++ }, { operations++ })
                }
            }
        }
        compose.onNodeWithTag("oobe_agreement").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("使用协议").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test fun largeTextProfileKeepsTheSingleInputReachable() {
        var name = "机长"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RiseDiaryTheme {
                    Box(Modifier.size(280.dp, 320.dp)) {
                        ProfileOnboardingPage(name, true, rememberScrollState(), { name = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("oobe_username").performScrollTo().assertIsDisplayed().performTextReplacement("新的称呼")
        compose.runOnIdle { assertEquals("新的称呼", name) }
    }
}
