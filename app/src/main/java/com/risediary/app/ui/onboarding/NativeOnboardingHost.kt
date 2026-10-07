package com.risediary.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.*

/** Compatibility adapter: first-run business and callers retain their eight-step API. */
@Composable
internal fun NativeOnboardingHost(
    ui: OnboardingUiState,
    frame: OnboardingVisualFrame,
    active: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onTransitionSettled: (Long, OnboardingStep) -> Unit,
    content: @Composable (OnboardingStep, Boolean) -> Unit,
    onGeometry: (Offset, Rect, Size) -> Unit = { _, _, _ -> },
    primaryEnabled: Boolean = true,
    introCenter: Offset = Offset.Unspecified,
) = NativeGuideHost(
    GuideSceneUiState(ui.saving, ui.transitioning, ui.modalOpen, ui.canContinue),
    frame.toGuideFrame(), active, onNext, onBack,
    { id, page -> onTransitionSettled(id, OnboardingStep.entries[page]) },
    { page, enabled -> content(OnboardingStep.entries[page], enabled) },
    onGeometry, primaryEnabled, introCenter,
)
