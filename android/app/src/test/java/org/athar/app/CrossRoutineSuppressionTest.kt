package org.athar.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.athar.app.adhkar.*
import org.athar.app.core.data.CounterState
import org.athar.app.khatmah.*
import org.athar.app.notifications.*
import org.athar.app.quran.*
import org.junit.Assert.*
import org.junit.Test

class CrossRoutineSuppressionTest {
    @Test fun genuineCompletionSuppressesOnlyTheRelevantRoutine() {
        val quranRule=QuranWirdRule(
            enabled=true,targetAyahs=10,time="20:00",days=(1..7).toSet(),
            timezone="Africa/Cairo",revision=1,activatedAtMillis=1,
        )
        val quranProgress=QuranWirdDayProgress("2026-10-26",100,110)
        assertTrue(
            QuranWirdPlanner.plan(
                quranRule,quranProgress,Instant.parse("2026-10-26T12:00:00Z"),"Q","B",1
            ).isEmpty()
        )

        val requirements=mapOf(1 to 3)
        val completion=AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to CounterState(item=1,target=3,count=3,revision=1,localVersion=1,day="2026-10-26")),
            "2026-10-26",
        )
        val adhkarRule=AdhkarReminderRule(
            category="Morning",enabled=true,time="20:00",days=(1..7).toSet(),
            timezone="Africa/Cairo",requirements=requirements,revision=1,activatedAtMillis=1,
        )
        assertTrue(
            AdhkarReminderPlanner.plan(
                adhkarRule,completion,Instant.parse("2026-10-26T12:00:00Z"),"A","B",1
            ).isEmpty()
        )
    }

    @Test fun pausedKhatmahSuppressesWithoutDeletingOtherRoutinePolicy() {
        val plan=KhatmahPlanState(
            startDate="2026-10-20",targetDate="2026-11-20",completedPages=100,
            status="paused",revision=1,localVersion=1,
        )
        val rule=KhatmahReminderRule(
            enabled=true,time="20:00",timezone="Africa/Cairo",revision=1,activatedAtMillis=1,
        )
        assertNull(
            KhatmahReminderPlanner.next(
                rule,plan,Instant.parse("2026-10-26T12:00:00Z"),"K",{d,r->"$d/$r"},1
            )
        )
        val global=ReminderGlobalPolicy(true,"22:00","07:00","Africa/Cairo").validated()
        assertNotNull(global.quietHours())
    }

    @Test fun crossCategoryIdempotencyIsGlobalNotActivityDerived() {
        val key="same-logical-delivery"
        val candidate=NotificationDeliveryCandidate(
            idempotencyKey=key,
            category="custom_routines",
            scheduledAt=Instant.parse("2026-10-26T10:00:00Z"),
            expiresAt=Instant.parse("2026-10-26T11:00:00Z"),
        )
        assertEquals(
            NotificationPolicyDecision.Drop(NotificationDropReason.DUPLICATE),
            NotificationPolicy.evaluate(
                candidate,
                Instant.parse("2026-10-26T10:01:00Z"),
                deliveredKeys=setOf(key),
            ),
        )
    }

    @Test fun globalPauseAndCategoryRevocationRemainIndependentOfPrivateProgress() {
        val categories=NotificationPreferenceContract.categories.associateWith { it=="night_prayer" }
        val active=NotificationPreferences(
            schemaVersion=1,revision=1,masterEnabled=true,
            consent=NotificationConsent(1,1,true,null),
            categories=categories,updatedAt=null,
        )
        assertTrue(active.allows("night_prayer"))
        assertFalse(active.allows("quran_routine"))
        val paused=active.copy(masterEnabled=false,revision=2)
        assertFalse(paused.allows("night_prayer"))
        assertEquals(categories,paused.categories)
    }
}
