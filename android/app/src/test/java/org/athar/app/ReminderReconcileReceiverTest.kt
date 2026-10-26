package org.athar.app

import android.content.Intent
import org.athar.app.notifications.ReminderReconcileReceiver
import org.junit.Assert.*
import org.junit.Test

class ReminderReconcileReceiverTest {
    @Test fun onlyLifecycleActionsThatRequireScheduleReconciliationAreAccepted() {
        val actions = ReminderReconcileReceiver.ACCEPTED_ACTIONS
        assertTrue(Intent.ACTION_BOOT_COMPLETED in actions)
        assertTrue(Intent.ACTION_TIME_CHANGED in actions)
        assertTrue(Intent.ACTION_TIMEZONE_CHANGED in actions)
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in actions)
        assertFalse(Intent.ACTION_SCREEN_ON in actions)
        assertFalse(Intent.ACTION_USER_PRESENT in actions)
    }
}
