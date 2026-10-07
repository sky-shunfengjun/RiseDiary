package com.risediary.app.service

import com.risediary.app.ui.navigation3.Route

internal fun resolveTimerNotificationRoute(entry: TimerNotificationEntry, session: TimerSession,
                                          locked: Boolean): Route? {
    if (locked || !TimerSessionPolicy.matches(session, entry.sessionId) ||
        (!session.isActive && !session.isTerminal)) return null
    return when (session.kind) {
        TimerKind.NORMAL -> Route.Timer
        TimerKind.VIDEO -> if (session.video != null) Route.VideoTimer(entry.sessionId) else null
    }
}
