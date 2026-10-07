/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

internal class OnboardingTransitionGate {
    internal val sceneGate = GuideTransitionGate()
    fun begin(from: OnboardingStep, to: OnboardingStep) =
        sceneGate.begin(from.ordinal, to.ordinal).let { OnboardingTransitionTicket(it.id, from, to) }
    fun complete(ticket: OnboardingTransitionTicket) =
        sceneGate.complete(GuideTransitionTicket(ticket.id, ticket.from.ordinal, ticket.to.ordinal))
    fun cancel(ticket: OnboardingTransitionTicket) =
        sceneGate.cancel(GuideTransitionTicket(ticket.id, ticket.from.ordinal, ticket.to.ordinal))
}
