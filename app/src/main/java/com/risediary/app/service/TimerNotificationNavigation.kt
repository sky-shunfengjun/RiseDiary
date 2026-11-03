package com.risediary.app.service

import com.risediary.app.ui.navigation3.Route

internal fun resolveTimerNotificationRoute(entry: TimerNotificationEntry, session: TimerSession,
                                          locked: Boolean): Route? {
    if (locked || !TimerSessionPolicy.matches(session, entry.sessionId) ||
        (!session.isActive && !session.isTerminal)) return null
    return timerDestination(session)
}

internal fun timerDestination(session: TimerSession): Route? {
    val id = session.sessionId ?: return null
    return when (session.kind) {
        TimerKind.NORMAL -> Route.Timer
        TimerKind.VIDEO -> when (val source = session.videoSource) {
            is com.risediary.app.media.VideoSourceRef.LibraryVideo -> Route.LibraryViewer(source.materialId, id)
            is com.risediary.app.media.VideoSourceRef.DeviceVideo -> Route.VideoTimer(id)
            null -> if (session.video != null) Route.VideoTimer(id) else null
        }
    }
}
