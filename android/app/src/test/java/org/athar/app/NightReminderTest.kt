package org.athar.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test

class NightReminderTest {
    private val cairo = ZoneId.of("Africa/Cairo")
    private fun prefs(zone:ZoneId=cairo)=NightPrayerPreferences(
        city="Test city",
        timezone=zone.id,
        latitude=30.0,
        longitude=31.0,
        calculationMethod="EGYPTIAN",
        madhab="SHAFI",
        highLatitudeRule="MIDDLE_OF_THE_NIGHT",
    )
    private fun rule(
        mode:NightReminderMode=NightReminderMode.LAST_THIRD,
        fixed:String?=null,
        values:NightPrayerPreferences?=prefs(),
        enabled:Boolean=true,
    )=NightReminderRule(
        enabled=enabled,
        mode=mode,
        fixedTime=fixed,
        timezone=values?.timezone ?: cairo.id,
        preferences=values,
        revision=1,
        activatedAtMillis=1,
    )
    private fun context(date:LocalDate,zone:ZoneId=cairo):NightContext =
        NightContext(
            date=date,
            zone=zone,
            maghrib=ZonedDateTime.of(date,LocalTime.of(18,0),zone).toInstant(),
            fajr=ZonedDateTime.of(date.plusDays(1),LocalTime.of(5,0),zone).toInstant(),
        )

    @Test fun lastThirdUsesCalculatedNightAndExpiresAtFajr() {
        val date=LocalDate.parse("2026-10-26")
        val night=context(date)
        val result=NightReminderPlanner.next(
            rule(),
            emptySet(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night",
            "Body",
            1,
        ){requested,_-> assertEquals(date,requested);night }
        val ready=result as NightReminderPlanResult.Ready
        assertEquals(night.lastThird.toEpochMilli(),ready.spec.scheduledAtMillis)
        assertEquals(night.fajr.toEpochMilli(),ready.spec.expiresAtMillis)
        assertEquals("night_prayer",ready.spec.category)
        assertEquals("night",ready.spec.destinationToken)
        assertEquals(date,ready.nightDate)
    }

    @Test fun fixedTimeMustFallInsideCalculatedNight() {
        val date=LocalDate.parse("2026-10-26")
        val night=context(date)
        val ok=NightReminderPlanner.next(
            rule(NightReminderMode.FIXED_TIME,"02:00"),
            emptySet(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night","Body",1,
        ){_,_->night} as NightReminderPlanResult.Ready
        val local=Instant.ofEpochMilli(ok.spec.scheduledAtMillis).atZone(cairo)
        assertEquals(date.plusDays(1),local.toLocalDate())
        assertEquals(2,local.hour)

        val invalid=NightReminderPlanner.next(
            rule(NightReminderMode.FIXED_TIME,"12:00"),
            emptySet(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night","Body",1,
        ){_,_->night}
        assertEquals(
            NightReminderUnavailable.NO_ELIGIBLE_NIGHT,
            (invalid as NightReminderPlanResult.Unavailable).reason,
        )
    }

    @Test fun localExemptionSkipsNightWithoutCreatingWorshipInference() {
        val first=LocalDate.parse("2026-10-26")
        val result=NightReminderPlanner.next(
            rule(),
            setOf(first),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night","Body",2,
        ){date,_->context(date)}
        val ready=result as NightReminderPlanResult.Ready
        assertEquals(first.plusDays(1),ready.nightDate)
    }

    @Test fun missingAndFailedCalculationAreExplicitUnavailableStates() {
        val missing=NightReminderPlanner.next(
            rule(values=null),
            emptySet(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night","Body",1,
        )
        assertEquals(
            NightReminderUnavailable.PREFERENCES_MISSING,
            (missing as NightReminderPlanResult.Unavailable).reason,
        )
        val failed=NightReminderPlanner.next(
            rule(),
            emptySet(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Night","Body",1,
        ){_,_->throw IllegalStateException("polar")}
        assertEquals(
            NightReminderUnavailable.CALCULATION_UNAVAILABLE,
            (failed as NightReminderPlanResult.Unavailable).reason,
        )
    }

    @Test fun fixedLocalTimeSurvivesDstOffsetChange() {
        val berlin=ZoneId.of("Europe/Berlin")
        val source=rule(
            mode=NightReminderMode.FIXED_TIME,
            fixed="02:00",
            values=prefs(berlin),
        )
        fun calculate(date:LocalDate)=NightContext(
            date,
            berlin,
            ZonedDateTime.of(date,LocalTime.of(18,0),berlin).toInstant(),
            ZonedDateTime.of(date.plusDays(1),LocalTime.of(6,0),berlin).toInstant(),
        )
        val before=NightReminderPlanner.next(
            source,emptySet(),Instant.parse("2026-10-18T10:00:00Z"),"N","B",1
        ){date,_->calculate(date)} as NightReminderPlanResult.Ready
        val after=NightReminderPlanner.next(
            source,emptySet(),Instant.parse("2026-10-25T10:00:00Z"),"N","B",1
        ){date,_->calculate(date)} as NightReminderPlanResult.Ready
        val b=Instant.ofEpochMilli(before.spec.scheduledAtMillis).atZone(berlin)
        val a=Instant.ofEpochMilli(after.spec.scheduledAtMillis).atZone(berlin)
        assertEquals(2,b.hour);assertEquals(2,a.hour);assertNotEquals(b.offset,a.offset)
    }

    @Test fun disabledRuleProducesNoSchedule() {
        val value=NightReminderPlanner.next(
            rule(enabled=false),emptySet(),Instant.parse("2026-10-26T10:00:00Z"),"N","B",1
        ){date,_->context(date)}
        assertEquals(
            NightReminderUnavailable.DISABLED,
            (value as NightReminderPlanResult.Unavailable).reason,
        )
    }
}
