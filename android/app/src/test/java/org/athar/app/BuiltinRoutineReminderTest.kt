package org.athar.app

import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BuiltinRoutineReminderTest {
    private fun rule(
        kind:BuiltinRoutineKind,
        days:Set<Int>,
        timezone:String="Africa/Cairo",
        time:String="10:00",
        enabled:Boolean=true,
        quietStart:String?=null,
        quietEnd:String?=null,
    )=BuiltinRoutineRule(
        kind,enabled,time,days,timezone,1,
        Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(),
        quietStart,quietEnd,
    )

    @Test fun fridayIsStructurallyFridayOnly() {
        assertTrue(runCatching{rule(BuiltinRoutineKind.FRIDAY,setOf(4)).validated()}.isFailure)
        val plan=requireNotNull(BuiltinRoutinePlanner.next(
            rule(BuiltinRoutineKind.FRIDAY,setOf(5)),
            Instant.parse("2026-10-26T06:00:00Z"),"Friday","Body",
        ))
        val local=Instant.ofEpochMilli(plan.scheduledAtMillis).atZone(ZoneId.of("Africa/Cairo"))
        assertEquals(5,local.dayOfWeek.value);assertEquals(10,local.hour)
    }

    @Test fun dstKeepsSelectedWallClockTime() {
        val berlin=rule(BuiltinRoutineKind.SALAWAT,setOf(7),"Europe/Berlin","20:00")
        val before=requireNotNull(BuiltinRoutinePlanner.next(berlin,Instant.parse("2026-10-18T10:00:00Z"),"S","B"))
        val after=requireNotNull(BuiltinRoutinePlanner.next(berlin,Instant.parse("2026-10-25T10:00:00Z"),"S","B"))
        val b=Instant.ofEpochMilli(before.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin"))
        val a=Instant.ofEpochMilli(after.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin"))
        assertEquals(20,b.hour);assertEquals(20,a.hour);assertNotEquals(b.offset,a.offset)
    }

    @Test fun ttlQuietAndDedupeAreStable() {
        val source=rule(BuiltinRoutineKind.SALAWAT,setOf(1),time="20:00",quietStart="22:00",quietEnd="07:00")
        val first=requireNotNull(BuiltinRoutinePlanner.next(source,Instant.parse("2026-10-26T10:00:00Z"),"S","B"))
        val replay=requireNotNull(BuiltinRoutinePlanner.next(source,Instant.parse("2026-10-26T10:01:00Z"),"S","B"))
        assertTrue(first.expiresAtMillis>first.scheduledAtMillis)
        assertEquals("22:00",first.quietStart);assertEquals("07:00",first.quietEnd)
        assertEquals(first.idempotencyKey,replay.idempotencyKey)
    }

    @Test fun disabledRuleCreatesNothing() {
        assertNull(BuiltinRoutinePlanner.next(
            rule(BuiltinRoutineKind.SALAWAT,setOf(1),enabled=false),
            Instant.parse("2026-10-26T06:00:00Z"),"S","B",
        ))
    }
}
