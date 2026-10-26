package org.athar.app

import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class ReminderCenterModelTest {
    @Test fun emptyCenterHasZeroRulesAndZeroActiveTypes() {
        val counts=ReminderCenterCounts(0,0,0,0,0)
        assertEquals(0,counts.total)
        assertEquals(0,counts.activeTypes)
    }

    @Test fun countsAggregateAcrossEveryImplementedReminderFamily() {
        val counts=ReminderCenterCounts(
            lessons=2,
            quran=1,
            adhkar=3,
            builtin=2,
            custom=4,
            khatmah=1,
        )
        assertEquals(13,counts.total)
        assertEquals(6,counts.activeTypes)
    }

    @Test fun masterPauseBlocksEveryCategoryWithoutDeletingCategoryChoices() {
        val categories=NotificationPreferenceContract.categories.associateWith {
            it in setOf("lesson_reminders","quran_routine","custom_routines")
        }
        val active=NotificationPreferences(
            schemaVersion=1,
            revision=5,
            masterEnabled=true,
            consent=NotificationConsent(1,1,true,"2026-10-26 20:00:00"),
            categories=categories,
            updatedAt=null,
        )
        assertTrue(active.allows("lesson_reminders"))
        assertTrue(active.allows("quran_routine"))
        assertTrue(active.allows("custom_routines"))
        assertFalse(active.allows("adhkar_routine"))

        val paused=active.copy(masterEnabled=false,revision=6)
        NotificationPreferenceContract.categories.forEach { category ->
            assertFalse(paused.allows(category))
        }
        assertEquals(categories,paused.categories)
    }

    @Test fun categoryChoicesStayIndependentAcrossTypes() {
        val categories=NotificationPreferenceContract.categories.associateWith {
            it=="adhkar_routine"
        }
        val prefs=NotificationPreferences(
            schemaVersion=1,
            revision=2,
            masterEnabled=true,
            consent=NotificationConsent(1,1,true,null),
            categories=categories,
            updatedAt=null,
        )
        assertTrue(prefs.allows("adhkar_routine"))
        assertFalse(prefs.allows("salawat_friday"))
        assertFalse(prefs.allows("custom_routines"))
        assertFalse(prefs.allows("lesson_reminders"))
    }
}
