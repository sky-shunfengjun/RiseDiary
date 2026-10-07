package com.risediary.app.ui.settings

import org.junit.Assert.*
import org.junit.Test

class ReminderPermissionDialogsTest {
    @Test fun enablingWithExactAccessDoesNotShowAnAccessWarning() {
        assertEquals(ReminderPermissionDialogs(), reminderEnabledDialogs(ReminderPermissionDialogs(), true))
    }
    @Test fun aSuccessfulNotificationTestDoesNotClaimThatNotificationsAreBlocked() {
        assertEquals(ReminderPermissionDialogs(), reminderTestDialogs(ReminderPermissionDialogs(), true))
    }
    @Test fun missingExactAccessShowsOnlyTheExactWarning() {
        assertEquals(ReminderPermissionDialogs(exactAlarm = true),
            reminderEnabledDialogs(ReminderPermissionDialogs(notificationBlocked = true), false))
    }
    @Test fun aFailedNotificationTestShowsOnlyTheNotificationWarning() {
        assertEquals(ReminderPermissionDialogs(notificationBlocked = true),
            reminderTestDialogs(ReminderPermissionDialogs(exactAlarm = true), false))
    }
}
