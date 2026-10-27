package org.athar.app

import org.athar.app.core.sync.BackgroundWorkBudgets
import org.athar.app.core.sync.BackgroundWorkKind
import org.junit.Assert.*
import org.junit.Test

class BackgroundWorkBudgetTest {
    @Test fun periodicBudgetsAreBoundedAndFlexed() {
        val privateSync = BackgroundWorkBudgets.PRIVATE_SYNC
        assertEquals(6L, privateSync.periodicHours)
        assertEquals(2L, privateSync.flexHours)
        assertEquals(4, privateSync.maximumPeriodicRunsPerDay)
        assertTrue(privateSync.requiresNetwork)
        assertTrue(privateSync.requiresBatteryNotLow)
        assertTrue(privateSync.requiresStorageNotLow)

        val routine = BackgroundWorkBudgets.ROUTINE_MAINTENANCE
        assertEquals(24L, routine.periodicHours)
        assertEquals(6L, routine.flexHours)
        assertEquals(1, routine.maximumPeriodicRunsPerDay)
        assertFalse(routine.requiresNetwork)
        assertTrue(routine.requiresBatteryNotLow)
        assertTrue(routine.requiresStorageNotLow)
    }

    @Test fun networkMutationWorkersWaitForNetworkBatteryAndStorage() {
        for (budget in listOf(
            BackgroundWorkBudgets.PUSH_DEVICE_SYNC,
            BackgroundWorkBudgets.INBOX_SYNC,
        )) {
            assertEquals(BackgroundWorkKind.NETWORK_SYNC, budget.kind)
            assertTrue(budget.requiresNetwork)
            assertTrue(budget.requiresBatteryNotLow)
            assertTrue(budget.requiresStorageNotLow)
            assertEquals(5, budget.maxRetryAttempts)
            assertTrue(budget.uniqueCoalesced)
        }
    }

    @Test fun visibleReminderCorrectnessIsNeverNetworkGated() {
        for (budget in listOf(
            BackgroundWorkBudgets.REMINDER_DELIVERY,
            BackgroundWorkBudgets.REMINDER_RECONCILE,
        )) {
            assertEquals(BackgroundWorkKind.USER_VISIBLE_LOCAL, budget.kind)
            assertFalse(budget.requiresNetwork)
            assertFalse(budget.requiresBatteryNotLow)
            assertFalse(budget.requiresStorageNotLow)
            assertTrue(budget.uniqueCoalesced)
        }
    }

    @Test fun playbackIsUserDrivenNotPeriodicWork() {
        val playback = BackgroundWorkBudgets.MEDIA_PLAYBACK
        assertEquals(BackgroundWorkKind.MEDIA_PLAYBACK, playback.kind)
        assertNull(playback.periodicHours)
        assertNull(playback.flexHours)
        assertFalse(playback.uniqueCoalesced)
    }

    @Test fun everyBudgetHasUniqueStableNameAndValidShape() {
        val all = BackgroundWorkBudgets.all
        assertEquals(all.size, all.map { it.name }.toSet().size)
        all.forEach { assertSame(it, it.validated()) }
    }
}
