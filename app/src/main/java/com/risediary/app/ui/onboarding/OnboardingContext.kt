package com.risediary.app.ui.onboarding

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

internal fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

/** Keep the user's in-progress theme visible across every onboarding page. */
internal fun resolveOnboardingPreviewTheme(themeDraft: String, persistedTheme: String): String =
    themeDraft.ifBlank { persistedTheme }
