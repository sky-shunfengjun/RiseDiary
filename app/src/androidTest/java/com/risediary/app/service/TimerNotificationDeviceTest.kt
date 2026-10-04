package com.risediary.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TimerNotificationDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = TimerNotificationFactory(context, object : ElapsedRealtimeClock {
        override fun millis() = 35000L
    }, Clock.fixed(Instant.ofEpochMilli(135000L), ZoneOffset.UTC))
    private val capable = TimerNotificationCapabilities(36)
    private val running = TimerSession(status = TimerStatus.RUNNING, sessionId = "one",
        elapsedMillis = 5000L, resumedAtElapsedRealtime = 30000L)

    @Test fun ordinaryNotificationKeepsActionsAndOnlyUsesTheActiveTime() {
        val notification = factory.build(running, enabled = false, caps = capable)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(125000L, notification.`when`)
        assertEquals(2, notification.actions.size)
        assertTrue(notification.actions[0].actionIntent.isForegroundService)
        assertTrue(notification.actions[1].actionIntent.isActivity)
        assertTrue(notification.actions.all { it.actionIntent.isImmutable })
    }

    @Test fun eligibleStandardNotificationRequestsPromotionButDismissedOneDoesNot() {
        val enabled = factory.build(running, true, caps = capable)
        assertTrue(enabled.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(enabled.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNull(enabled.contentView)
        assertNull(enabled.bigContentView)
        if (Build.VERSION.SDK_INT >= 36)
            assertTrue(enabled.hasPromotableCharacteristics())
        assertFalse(factory.build(running.copy(liveUpdateDismissed = true), true, caps = capable)
            .extras.getBoolean("android.requestPromotedOngoing"))
    }

    @Test fun androidSixteenReadsTheActualSystemPromotionPermission() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val manager = context.getSystemService(NotificationManager::class.java)
        val capabilities = TimerLiveUpdateSupport.capabilities(context)
        assertTrue(TimerNotificationPolicy.supportsLiveUpdates(capabilities.sdkInt))
        assertEquals(manager.canPostPromotedNotifications(), capabilities.promotionAllowed)
    }

    @Test fun disabledPromotionNeverFallsBackToTheLegacyColorizedFormat() {
        val caps = listOf(capable.copy(promotionAllowed = false),
            capable.copy(notificationsAllowed = false), capable.copy(channelImportance = 1),
            capable.copy(sdkInt = 35))
        val notifications = caps.map { factory.build(running, true, caps = it) } +
            factory.build(running, false, caps = capable) +
            factory.build(running.copy(liveUpdateDismissed = true), true, caps = capable)
        notifications.forEach {
            assertFalse(it.extras.getBoolean("android.requestPromotedOngoing"))
            assertFalse(it.extras.getBoolean(Notification.EXTRA_COLORIZED))
            assertEquals(2, it.actions.size)
        }
    }

    @Test fun pausedNotificationStopsItsClockAndOffersResumeAndFinish() {
        val paused = factory.build(running.copy(status = TimerStatus.PAUSED), true, caps = capable)
        assertFalse(paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(paused.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("00:05"))
        assertEquals(2, paused.actions.size)
        assertTrue(paused.actions[0].actionIntent.isForegroundService)
        assertTrue(paused.actions[1].actionIntent.isActivity)
    }

    @Test fun confirmationFreezesTheClickValueAndHasNoCompetingActions() {
        val waiting = running.copy(finishCandidate = TimerFinishCandidate("one", 108000L, 8000L, TimerStatus.RUNNING))
        val notification = factory.build(waiting, true, caps = capable)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("00:08"))
        assertTrue(notification.actions.isNullOrEmpty())
        assertTrue(notification.contentIntent.isActivity)
    }

    @Test fun videoNamesAndMediaNeverAppearInPublicNotification() {
        val video = running.copy(kind = TimerKind.VIDEO,
            video = VideoPlaybackSnapshot(LocalVideoRef("content://provider/video/secret",
                "private-video-name.mp4", "video/mp4")))
        val notification = factory.build(video, true, caps = capable)
        assertFalse(notification.extras.toString().contains("private-video-name"))
        assertFalse(notification.extras.toString().contains("content://provider"))
        assertNull(notification.extras.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_PICTURE))
    }

    @Test fun pendingIntentsDoNotRetargetOldActionsWhenANewTimerStarts() {
        val oldPause = TimerNotificationIntents.service(context, "one", TimerService.ACTION_PAUSE)
        val newPause = TimerNotificationIntents.service(context, "two", TimerService.ACTION_PAUSE)
        assertNotEquals(oldPause, newPause)
        assertNotEquals(oldPause, TimerNotificationIntents.service(context, "one", TimerService.ACTION_RESUME))
        assertNotEquals(TimerNotificationIntents.activity(context, "one"),
            TimerNotificationIntents.activity(context, "one", finish = true))
        val command = TimerNotificationIntents.serviceIntent(context, "one", TimerService.ACTION_PAUSE)
        assertEquals("one", command.getStringExtra(TimerService.EXTRA_SESSION_ID))
        assertTrue(command.getBooleanExtra(TimerNotificationIntents.EXTRA_NOTIFICATION_COMMAND, false))
        assertEquals(TimerService::class.java.name, command.component?.className)
    }

    @Test fun finishDispatchBecomesAnOpenRequestSoRecreationCannotEndAgain() {
        val intent = TimerNotificationIntents.activityIntent(context, "one", finish = true)
        assertEquals(TimerNotificationIntents.ACTION_FINISH, intent.action)
        val opening = TimerNotificationIntents.activityIntent(context, "one")
        assertNotEquals(opening.data, intent.data)
        TimerNotificationIntents.markFinishDispatched(intent, context.packageName)
        assertEquals(com.risediary.app.MainActivity::class.java.name, intent.component?.className)
        assertEquals(TimerNotificationIntents.ACTION_OPEN, intent.action)
        assertEquals(opening.data, intent.data)
        assertEquals("one", TimerNotificationIntents.read(intent, context.packageName)?.sessionId)
        TimerNotificationIntents.markFinishDispatched(intent, context.packageName)
        assertEquals(TimerNotificationIntents.ACTION_OPEN, intent.action)
        TimerNotificationIntents.consume(intent)
        assertNull(TimerNotificationIntents.read(intent, context.packageName))
    }

    @Test fun onlyFinishRequiresSystemUnlockBeforeItsDirectActivityEntry() {
        val active = factory.build(running, enabled = true, caps = capable)
        assertFalse(active.actions[0].isAuthenticationRequired)
        assertTrue(active.actions[1].isAuthenticationRequired)
        val paused = factory.build(running.copy(status = TimerStatus.PAUSED), true, caps = capable)
        assertFalse(paused.actions[0].isAuthenticationRequired)
        assertTrue(paused.actions[1].isAuthenticationRequired)
    }

    @Test fun legacyFinishNameResolvesToMainWithoutAnIntermediateActivity() {
        val legacy = android.content.ComponentName(context, "com.risediary.app.service.TimerFinishActivity")
        val info = context.packageManager.getActivityInfo(legacy, 0)
        assertEquals(com.risediary.app.MainActivity::class.java.name, info.targetActivity)
        assertFalse(info.exported)
    }
    @Test fun activityRequestValidatesIdentityAndConsumePreventsReplay() {
        val intent = TimerNotificationIntents.activityIntent(context, "one", finish = true)
        assertEquals(com.risediary.app.MainActivity::class.java.name, intent.component?.className)
        assertEquals(com.risediary.app.MainActivity::class.java.name,
            TimerNotificationIntents.activityIntent(context, "one").component?.className)
        assertEquals("one", TimerNotificationIntents.read(intent, context.packageName)?.sessionId)
        intent.putExtra(TimerService.EXTRA_SESSION_ID, "two")
        assertNull(TimerNotificationIntents.read(intent, context.packageName))
        intent.putExtra(TimerService.EXTRA_SESSION_ID, "one")
        TimerNotificationIntents.consume(intent)
        assertNull(TimerNotificationIntents.read(intent, context.packageName))
    }
}
