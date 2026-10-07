package com.risediary.app.ui.onboarding.original

import com.risediary.app.ui.onboarding.IntroBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginalIntroBackendPolicyTest {
    @Test fun android12NeverSelectsRuntimeShader() {
        assertEquals(IntroBackend.CANVAS, selectIntroBackend(31, hardware = true, shaderReady = true))
    }
    @Test fun android12LNeverSelectsRuntimeShader() {
        assertEquals(IntroBackend.CANVAS, selectIntroBackend(32, hardware = true, shaderReady = true))
    }
    @Test fun supportedPublicShaderHasNoBrandRestriction() {
        assertEquals(IntroBackend.ORIGINAL_SHADER, selectIntroBackend(33, hardware = true, shaderReady = true))
    }
    @Test fun shaderFailureUsesCanvasWithoutBlockingIntro() {
        assertEquals(IntroBackend.CANVAS, selectIntroBackend(36, hardware = true, shaderReady = false))
    }
    @Test fun softwareWindowUsesCanvasDespiteShaderAvailability() {
        assertEquals(IntroBackend.CANVAS, selectIntroBackend(36, hardware = false, shaderReady = true))
    }
    @Test fun rendererFailureStillLeavesAStaticUsableScene() {
        assertEquals(IntroBackend.STATIC,
            selectIntroBackend(36, hardware = false, shaderReady = false, canvasReady = false))
    }
    @Test fun lowMemorySkipsEffectsEvenWhenShaderCouldRun() {
        assertEquals(IntroBackend.STATIC,
            selectIntroBackend(36, hardware = true, shaderReady = true, lowMemory = true))
    }
}
