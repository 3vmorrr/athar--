package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test

class NightPreferencesContractTest {
    private fun parse(raw: String) = NightPreferencesEnvelope.parse(JsonParser.parseString(raw))
    private fun complete() = NightPreferenceValues("Cairo", "Africa/Cairo", 30.0444, 31.2357, "EGYPTIAN", "SHAFI", "MIDDLE_OF_THE_NIGHT")
    @Test fun anActuallyUnsetObjectIsDifferentFromMissingOrMalformedData() {
        assertEquals(NightPreferencesEnvelope(0, NightPreferenceValues()), parse("""{"revision":0,"values":{}}"""))
        for (raw in listOf("""{"revision":0}""", """{"revision":0,"values":null}""", """{"revision":0,"values":[]}""", """{"revision":0,"values":{"city":"Cairo"}}"""))
            assertTrue(raw, runCatching { parse(raw) }.isFailure)
    }
    @Test fun currentApiPartialPreferencesRetainTheirRevisionAndCanBeCompleted() {
        val partial = parse("""{"revision":1,"values":{"city":"Riyadh","timezone":"Asia/Riyadh"}}""")
        assertEquals(1L, partial.revision); assertEquals("Riyadh", partial.values.city)
        assertNull(partial.values.latitude); assertNull(partial.values.calculationSettings())
        val repaired = partial.values.copy(latitude = 24.7, longitude = 46.7, calculationMethod = "UMM_AL_QURA", madhab = "SHAFI", highLatitudeRule = "MIDDLE_OF_THE_NIGHT")
        assertEquals(repaired, repaired.validatedForSave()); assertEquals("Asia/Riyadh", repaired.calculationSettings()!!.zone.id)
    }
    @Test fun fullPreferencesRoundTripWithoutChangingCoordinatesOrCalculationChoice() {
        val envelope = NightPreferencesEnvelope(8, complete())
        assertEquals(envelope, NightPreferencesEnvelope.parse(envelope.json()))
        assertFalse(envelope.values.calculationSettings()!!.hanafi)
        assertTrue(envelope.values.copy(madhab = "HANAFI").calculationSettings()!!.hanafi)
    }
    @Test fun unsignedServerRevisionDoesNotOverflowAndCoercedRevisionsAreRejected() {
        assertEquals(4_294_967_295L, parse("""{"revision":4294967295,"values":{}}""").revision)
        for (value in listOf("4294967296", "-1", "1.5", "\"1\"", "true", "1e2"))
            assertTrue(value, runCatching { parse("""{"revision":$value,"values":{}}""") }.isFailure)
    }
    @Test fun unknownButApiPermittedMethodStringsStayVisibleForExplicitRepair() {
        val existing = complete().copy(calculationMethod = "OLD_METHOD", madhab = "OLD_SCHOOL", timezone = "Unavailable/NativeZone")
        val envelope = NightPreferencesEnvelope(3, existing)
        val parsed = NightPreferencesEnvelope.parse(envelope.json())
        assertEquals(existing, parsed.values); assertNull(parsed.values.calculationSettings())
        assertTrue(runCatching { parsed.values.validatedForSave() }.isFailure)
    }
    @Test fun invalidCoordinatesAndQuotedOrNullFieldsNeverBecomeDefaults() {
        for (values in listOf("""{"latitude":"30"}""", """{"latitude":91}""", """{"longitude":181}""", """{"longitude":null}""", """{"city":123}""", """{"timezone":false}"""))
            assertTrue(values, runCatching { parse("""{"revision":1,"values":$values}""") }.isFailure)
    }
    @Test fun emptyCityAndAbsentCityRemainDistinctAndNoLocationIsInvented() {
        val blank = parse("""{"revision":1,"values":{"city":""}}""")
        val absent = parse("""{"revision":1,"values":{}}""")
        assertNotEquals(blank, absent); assertNull(blank.values.latitude); assertNull(absent.values.longitude)
        assertTrue(runCatching { absent.values.validatedForSave() }.isFailure)
    }
    @Test fun unknownFieldsAndExcessivelyLongCityCannotBeDroppedSilently() {
        assertTrue(runCatching { parse("""{"revision":1,"values":{"future_setting":"keep"}}""") }.isFailure)
        assertTrue(runCatching { NightPreferencesEnvelope(1, complete().copy(city = "x".repeat(81))) }.isFailure)
        assertTrue(runCatching { parse("""{"revision":1,"values":{},"extra":true}""") }.isFailure)
    }
    @Test fun confirmedSnapshotBindsOwnerEnvironmentAndPreservesPartialValues() {
        val snapshot = NightPreferencesSnapshot(1, "123456abcdef", parse("""{"revision":1,"values":{"city":"Riyadh"}}"""), "2026-10-30T04:00:00Z")
        val raw = NightPreferencesCodec.encode(snapshot)
        assertEquals(snapshot, NightPreferencesCodec.decode(raw, 1, snapshot.namespace))
        assertTrue(runCatching { NightPreferencesCodec.decode(raw, 2, snapshot.namespace) }.isFailure)
        assertTrue(runCatching { NightPreferencesCodec.decode(raw, 1, "fedcba654321") }.isFailure)
        assertNull(NightPreferencesCodec.decode(raw, 1, snapshot.namespace).account.values.latitude)
    }
    @Test fun unreadableSnapshotCannotBecomeAnEmptyOrCoercedAccountCopy() {
        val raw = NightPreferencesCodec.encode(NightPreferencesSnapshot(1, "123456abcdef", NightPreferencesEnvelope(0, NightPreferenceValues()), "2026-10-30T04:00:00Z"))
        val root = JsonParser.parseString(raw).asJsonObject.apply { addProperty("owner", "1") }
        assertTrue(runCatching { NightPreferencesCodec.decode(root.toString(), 1, "123456abcdef") }.isFailure)
        root.addProperty("owner", 1); root.remove("account")
        assertTrue(runCatching { NightPreferencesCodec.decode(root.toString(), 1, "123456abcdef") }.isFailure)
        assertTrue(runCatching { NightPreferencesCodec.decode("x".repeat(8193), 1, "123456abcdef") }.isFailure)
    }
    private fun defaults(school: String = "standard") = JsonParser.parseString("""{"enabled":true,"revision":2,"defaults":{"calculation_method":"MUSLIM_WORLD_LEAGUE","madhab":"$school","high_latitude_rule":"SEVENTH_OF_THE_NIGHT","target_verses":100},"source":{"name_ar":"حساب محلي","name_en":"Local calculation","url":"https://example.test/source"},"intro":{"ar":"وصف","en":"Description"}}""")
    @Test fun explicitPublicMethodChoiceMapsTheActualApiSchoolWithoutChangingLocation() {
        val public = NightPublicDefaults.parse(defaults())
        val changed = public.applyTo(complete())
        assertEquals("SHAFI", changed.madhab); assertEquals("MUSLIM_WORLD_LEAGUE", changed.calculationMethod)
        assertEquals(complete().latitude, changed.latitude); assertEquals(complete().longitude, changed.longitude)
        assertEquals(complete().city, changed.city); assertEquals(complete().timezone, changed.timezone)
        assertEquals("HANAFI", NightPublicDefaults.parse(defaults("hanafi")).applyTo(complete()).madhab)
        assertNull(public.applyTo(NightPreferenceValues()).latitude)
    }
    @Test fun invalidPublicDefaultsAreUnavailableRatherThanAnInventedFallback() {
        assertTrue(runCatching { NightPublicDefaults.parse(defaults("SHAFI")) }.isFailure)
        val bad = defaults().asJsonObject.apply { addProperty("enabled", "true") }
        assertTrue(runCatching { NightPublicDefaults.parse(bad) }.isFailure)
        val unknown = defaults().asJsonObject.apply { getAsJsonObject("defaults").addProperty("calculation_method", "UNKNOWN") }
        assertTrue(runCatching { NightPublicDefaults.parse(unknown) }.isFailure)
    }
    @Test fun anOlderReplyUsesTheConfirmedNewerCopyWhileUnreadableCopiesStayUnusable() {
        val account = NightPreferencesEnvelope(2, complete())
        val snapshot = NightPreferencesSnapshot(1, "123456abcdef", account, "2026-10-30T04:00:00Z")
        val proof = NightPreferenceRepair(1, snapshot.namespace, "a".repeat(64), account.copy(revision = 1))
        assertNull(NightPreferenceLoad(snapshot, false, proof).calculationSettings())
        assertEquals(complete().calculationSettings(), NightPreferenceLoad(snapshot, false, proof, newerCachedRevision = true).calculationSettings())
        assertEquals(complete().calculationSettings(), NightPreferenceLoad(snapshot, true).calculationSettings())
    }
}
