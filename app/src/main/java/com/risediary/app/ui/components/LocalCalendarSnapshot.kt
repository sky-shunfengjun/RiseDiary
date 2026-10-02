package com.risediary.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.risediary.app.util.LocalCalendarSnapshot
import java.time.LocalDate
import java.time.ZoneId

/** Root provides the observable phone calendar; previews retain a sensible default. */
val LocalCalendarEnvironment = compositionLocalOf {
    val zone = ZoneId.systemDefault()
    LocalCalendarSnapshot(LocalDate.now(zone), zone, 0)
}
