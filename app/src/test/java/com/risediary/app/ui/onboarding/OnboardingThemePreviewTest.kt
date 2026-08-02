package com.risediary.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingThemePreviewTest {

    @Test
    fun draftTheme_hasPriorityOverPersistedTheme() {
        assertEquals("dark", resolveOnboardingPreviewTheme("dark", "light"))
    }

    @Test
    fun emptyDraft_fallsBackToPersistedTheme() {
        assertEquals("system", resolveOnboardingPreviewTheme("", "system"))
    }
}
