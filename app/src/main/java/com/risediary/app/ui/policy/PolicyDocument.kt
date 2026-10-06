/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.policy

import androidx.annotation.StringRes
import com.risediary.app.R

enum class PolicyDocument(@param:StringRes val title: Int, @param:StringRes val body: Int) {
    TERMS(R.string.policy_terms_title, R.string.policy_terms_body),
    PRIVACY(R.string.policy_privacy_title, R.string.policy_privacy_body);

    val date: String get() = "2026-10-05"
}
