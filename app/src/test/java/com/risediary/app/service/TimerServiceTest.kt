package com.risediary.app.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerServiceTest {
    @Test
    fun foregroundServiceTypeUsesManifestTypeFromAndroid14() {
        assertEquals(0, foregroundServiceTypeForSdk(33))
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            foregroundServiceTypeForSdk(34)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            foregroundServiceTypeForSdk(36)
        )
    }
}
