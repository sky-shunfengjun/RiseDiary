package com.risediary.app.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TimerFinishIntentTest {
    @Test fun discardDispatchesOnlyTheRequestedIdentityWithoutFinishingARecord() {
        val holder = TimerStateHolder()
        holder.set(TimerSession(status = TimerStatus.RUNNING, sessionId = "s1"))
        var sent: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startService(service: Intent): ComponentName {
                sent = service
                return ComponentName(this, TimerService::class.java)
            }
        }
        ServiceTimerController(context, holder).discard("s1")
        val intent = requireNotNull(sent)
        assertEquals(TimerService.ACTION_DISCARD, intent.action)
        assertEquals("s1", intent.getStringExtra(TimerService.EXTRA_SESSION_ID))
        assertFalse(intent.hasExtra(TimerService.EXTRA_CANDIDATE))
        assertEquals(TimerStatus.RUNNING, holder.state.value.status)
    }

    @Test fun newDispatchFailureReplacesThePreviousCommandError() {
        val holder = TimerStateHolder()
        holder.setCommandError("旧错误")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startService(service: Intent): ComponentName {
                throw IllegalStateException("service unavailable")
            }
        }
        ServiceTimerController(context, holder).pause("s1")
        assertEquals("无法执行计时操作，请重试", holder.commandError.value)
    }

    @Test fun intentKeepsTheClickSnapshotWhenPlaybackPauseAdvancesTheHolder() {
        val holder = TimerStateHolder()
        val atClick = TimerSession(status = TimerStatus.RUNNING, sessionId = "s1",
            startedAtEpochMillis = 100_000L, elapsedMillis = 5_000L, resumedAtElapsedRealtime = 10_000L)
        holder.set(atClick)
        val candidate = TimerFinishPolicy.capture(atClick, 108_000L, 13_000L)
        var sent: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startService(service: Intent): ComponentName {
                sent = service
                return ComponentName(this, TimerService::class.java)
            }
        }
        holder.set(TimerMath.advance(atClick, 15_000L, 110_000L))
        holder.setCommandError("上一次操作失败")
        ServiceTimerController(context, holder).requestFinish("s1", 108_000L, 13_000L, candidate)
        assertNull(holder.commandError.value)
        val intent = requireNotNull(sent)
        val decoded = Json.decodeFromString<TimerFinishCandidate>(requireNotNull(intent.getStringExtra(TimerService.EXTRA_CANDIDATE)))
        assertEquals(8_000L, decoded.elapsedMillis)
        assertEquals(108_000L, decoded.requestedAtEpochMillis)
        assertEquals("s1", decoded.sessionId)
    }
}
