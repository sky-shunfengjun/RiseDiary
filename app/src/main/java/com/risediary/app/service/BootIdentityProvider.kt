package com.risediary.app.service

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface BootIdentityProvider {
    /** Null is deliberately not treated as evidence that two persisted baselines share a boot. */
    fun currentBootCount(): Int?
}

class SystemBootIdentityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BootIdentityProvider {
    override fun currentBootCount(): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).takeIf { it >= 0 }
    }.getOrNull()
}