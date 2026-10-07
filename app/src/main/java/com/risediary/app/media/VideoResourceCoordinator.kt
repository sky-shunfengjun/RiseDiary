package com.risediary.app.media

import javax.inject.Inject
import javax.inject.Singleton

/** Main-thread leases. A revoked page keeps its session, but no prepared decoder. */
@Singleton
class VideoResourceCoordinator @Inject constructor() {
    private var owner: Any? = null
    private var revoke: (() -> Unit)? = null
    fun acquire(next: Any, onRevoked: () -> Unit) {
        if (owner === next) return
        val previous = revoke
        owner = null; revoke = null
        previous?.invoke()
        owner = next; revoke = onRevoked
    }
    fun release(current: Any) {
        if (owner === current) { owner = null; revoke = null }
    }
}
