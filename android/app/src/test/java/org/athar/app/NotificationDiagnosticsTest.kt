package org.athar.app

import java.time.Instant
import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class NotificationDiagnosticsTest {
    private fun prefs(
        master:Boolean=true,
        allowed:Set<String> = setOf("lesson_reminders"),
    )=NotificationPreferences(
        schemaVersion=1,
        revision=1,
        masterEnabled=master,
        consent=NotificationConsent(1,1,true,null),
        categories=NotificationPreferenceContract.categories.associateWith { it in allowed },
        updatedAt=null,
    )

    private fun reminder(
        id:String,
        category:String,
        scheduled:Long,
        expires:Long,
        key:String=id,
    )=LocalReminderSpec(
        id=id,
        category=category,
        destinationToken="home",
        title="Private title must not enter diagnostics",
        body="Private body must not enter diagnostics",
        scheduledAtMillis=scheduled,
        expiresAtMillis=expires,
        idempotencyKey=key,
    ).validated()

    @Test fun diagnosticsAggregatePolicyWithoutPrivateCopyOrDates() {
        val now=Instant.parse("2026-10-27T10:00:00Z")
        val snapshot=NotificationDiagnosticsPolicy.build(
            preferences=prefs(),
            reminders=listOf(
                reminder(
                    "lesson-ready","lesson_reminders",
                    now.minusSeconds(60).toEpochMilli(),
                    now.plusSeconds(600).toEpochMilli(),
                ),
                reminder(
                    "quran-blocked","quran_routine",
                    now.minusSeconds(60).toEpochMilli(),
                    now.plusSeconds(600).toEpochMilli(),
                ),
            ),
            ledger=NotificationPolicyLedger(),
            globalPolicy=ReminderGlobalPolicy(
                quietEnabled=false,
                timezone="UTC",
            ),
            inbox=NotificationInboxLocalState(
                pendingReads=setOf(9),
                updatedAtMillis=1,
            ),
            now=now,
        )
        assertEquals(1,snapshot.outcomes.ready)
        assertEquals(1,snapshot.outcomes.consentBlocked)
        assertEquals(2,snapshot.outcomes.total)
        assertEquals(1,snapshot.pendingByCategory["lesson_reminders"])
        assertEquals(1,snapshot.pendingByCategory["quran_routine"])
        assertEquals(1,snapshot.inboxPendingMutations)
        assertFalse(snapshot.toString().contains("Private title"))
        assertFalse(snapshot.toString().contains("2026-10-27"))
    }

    @Test fun duplicateAndQuietDeferralAreCountedWithoutExposingWhen() {
        val now=Instant.parse("2026-10-27T22:30:00Z")
        val duplicate=reminder(
            "duplicate","lesson_reminders",
            now.minusSeconds(60).toEpochMilli(),
            now.plusSeconds(3600).toEpochMilli(),
            key="delivered-key",
        )
        val deferred=reminder(
            "deferred","lesson_reminders",
            now.minusSeconds(60).toEpochMilli(),
            now.plusSeconds(12*3600).toEpochMilli(),
        )
        val snapshot=NotificationDiagnosticsPolicy.build(
            preferences=prefs(),
            reminders=listOf(duplicate,deferred),
            ledger=NotificationPolicyLedger(
                delivered=mapOf("delivered-key" to now.minusSeconds(120).toEpochMilli()),
                lastByCategory=emptyMap(),
            ),
            globalPolicy=ReminderGlobalPolicy(
                quietEnabled=true,
                quietStart="22:00",
                quietEnd="07:00",
                timezone="UTC",
            ),
            inbox=NotificationInboxLocalState(),
            now=now,
        )
        assertEquals(1,snapshot.outcomes.dropped)
        assertEquals(1,snapshot.outcomes.deferred)
        assertTrue(snapshot.quietHoursEnabled)
        assertTrue(snapshot.hasDeliveryHistory)
    }

    @Test fun masterPauseBlocksEveryPendingCategoryWithoutChangingCounts() {
        val now=Instant.parse("2026-10-27T10:00:00Z")
        val reminders=listOf(
            reminder("a","lesson_reminders",now.toEpochMilli(),now.plusSeconds(600).toEpochMilli()),
            reminder("b","quran_routine",now.toEpochMilli(),now.plusSeconds(600).toEpochMilli()),
        )
        val snapshot=NotificationDiagnosticsPolicy.build(
            preferences=prefs(master=false,allowed=setOf("lesson_reminders","quran_routine")),
            reminders=reminders,
            ledger=NotificationPolicyLedger(),
            globalPolicy=ReminderGlobalPolicy(timezone="UTC"),
            inbox=NotificationInboxLocalState(),
            now=now,
        )
        assertEquals(2,snapshot.outcomes.consentBlocked)
        assertEquals(1,snapshot.pendingByCategory["lesson_reminders"])
        assertEquals(1,snapshot.pendingByCategory["quran_routine"])
        assertFalse(snapshot.categoryAllowed.getValue("lesson_reminders"))
    }
}
