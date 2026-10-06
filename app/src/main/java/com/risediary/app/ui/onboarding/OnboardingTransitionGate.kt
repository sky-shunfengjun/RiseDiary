/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

internal class OnboardingTransitionGate {
    private var sequence = 0L
    private var current: OnboardingTransitionTicket? = null
    fun begin(from: OnboardingStep, to: OnboardingStep): OnboardingTransitionTicket =
        OnboardingTransitionTicket(++sequence, from, to).also { current = it }
    fun complete(ticket: OnboardingTransitionTicket): Boolean = consume(ticket)
    fun cancel(ticket: OnboardingTransitionTicket): Boolean = consume(ticket)
    private fun consume(ticket: OnboardingTransitionTicket): Boolean {
        if (current != ticket) return false
        current = null
        return true
    }
}
