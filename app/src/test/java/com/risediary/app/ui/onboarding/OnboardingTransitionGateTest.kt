package com.risediary.app.ui.onboarding

import org.junit.Assert.*
import org.junit.Test

class OnboardingTransitionGateTest {
    @Test fun staleCompletionCannotUnlockCurrentTransition() {
        val gate = OnboardingTransitionGate()
        val first = gate.begin(OnboardingStep.WELCOME, OnboardingStep.STATEMENT)
        val second = gate.begin(OnboardingStep.STATEMENT, OnboardingStep.PROFILE)
        assertFalse(gate.complete(first))
        assertTrue(gate.complete(second))
        assertFalse(gate.complete(second))
    }
    @Test fun cancellationCannotCompleteAReplacementTransition() {
        val gate = OnboardingTransitionGate()
        val first = gate.begin(OnboardingStep.WELCOME, OnboardingStep.STATEMENT)
        assertTrue(gate.cancel(first))
        val next = gate.begin(OnboardingStep.WELCOME, OnboardingStep.STATEMENT)
        assertTrue(next.id > first.id)
        assertFalse(gate.complete(first))
        assertTrue(gate.complete(next))
    }
    @Test fun matchingNumberWithDifferentStepsCannotUnlock() {
        val gate = OnboardingTransitionGate()
        val ticket = gate.begin(OnboardingStep.PROFILE, OnboardingStep.THEME)
        assertFalse(gate.complete(ticket.copy(to = OnboardingStep.PRIVACY)))
        assertTrue(gate.complete(ticket))
    }
}
