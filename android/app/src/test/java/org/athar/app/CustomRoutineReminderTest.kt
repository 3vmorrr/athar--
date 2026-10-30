package org.athar.app

import java.time.Instant
import java.time.ZoneId
import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class CustomRoutineReminderTest {
    private fun rule(
        id:String="0123456789abcdef",
        label:String="Read",
        enabled:Boolean=true,
        time:String="18:00",
        days: Set<Int> = setOf(1,3,5),
    )=CustomRoutineRule(
        id,label,enabled,time,days,"Africa/Cairo",1,
        Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(),
    )

    @Test fun validationIsBoundedAndExplicit() {
        assertTrue(runCatching{rule(label="").validated()}.isFailure)
        assertTrue(runCatching{rule(label="x".repeat(61)).validated()}.isFailure)
        assertTrue(runCatching{rule(days=emptySet()).validated()}.isFailure)
        assertTrue(runCatching{rule(id="bad").validated()}.isFailure)
        assertTrue(rule().validated().enabled)
    }

    @Test fun multipleSchedulesRemainIndependent() {
        val a=rule("0123456789abcdef","A",time="10:00")
        val b=rule("fedcba9876543210","B",time="20:00")
        val now=Instant.parse("2026-10-26T05:00:00Z")
        val pa=requireNotNull(CustomRoutinePlanner.next(a,now,"Custom"))
        val pb=requireNotNull(CustomRoutinePlanner.next(b,now,"Custom"))
        assertNotEquals(pa.id,pb.id)
        assertNotEquals(pa.idempotencyKey,pb.idempotencyKey)
    }

    @Test fun editKeepsStableIdentityAndRestartRoundTrips() {
        val original=rule()
        val edited=original.copy(label="Edited",time="21:30",revision=2)
        val state=CustomRoutineState(emptyList()).upsert(original).upsert(edited)
        assertEquals(1,state.rules.size)
        assertEquals("Edited",state.rules.single().label)
        val restored=CustomRoutineCodec.decode(CustomRoutineCodec.encode(state))
        assertEquals(state,restored)
    }

    @Test fun pauseAndDeleteAreDeterministic() {
        val paused=rule(enabled=false)
        assertNull(CustomRoutinePlanner.next(paused,Instant.parse("2026-10-26T05:00:00Z"),"Custom"))
        val state=CustomRoutineState(listOf(rule())).validated()
        assertTrue(state.remove("0123456789abcdef").rules.isEmpty())
    }

    @Test fun plannerUsesLocalDayAndExpiresBeforeAnotherDay() {
        val plan=requireNotNull(CustomRoutinePlanner.next(
            rule(time="18:00",days=setOf(1)),
            Instant.parse("2026-10-26T05:00:00Z"),
            "Custom",
        ))
        val zone=ZoneId.of("Africa/Cairo")
        val due=Instant.ofEpochMilli(plan.scheduledAtMillis).atZone(zone)
        val expiry=Instant.ofEpochMilli(plan.expiresAtMillis).atZone(zone)
        assertEquals(18,due.hour)
        assertEquals(due.toLocalDate().plusDays(1),expiry.toLocalDate())
        assertEquals(0,expiry.hour)
    }
}
