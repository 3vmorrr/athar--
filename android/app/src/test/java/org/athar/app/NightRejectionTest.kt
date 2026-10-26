package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.nightprayer.*
import org.junit.Test
import org.junit.Assert.*

class NightRejectionTest {
    @Test fun outcomesKeepConflictPermanentAndTemporarySeparate(){
        assertEquals(NightOperationOutcome.CONFLICT,nightOperationOutcome(409))
        for(status in listOf(400,403,404,410,422))assertEquals(NightOperationOutcome.REJECTED,nightOperationOutcome(status))
        for(status in listOf(401,408,425,429,500,503))assertEquals(NightOperationOutcome.RETRY,nightOperationOutcome(status))
    }
    private fun body()=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":12,"note":"fixture"}""").asJsonObject
    @Test fun repairRejectsInvalidCountsDatesZonesAndUnsupportedFields(){
        validateNightRepair(body())
        for((key,value) in listOf("verse_count" to "0","verse_count" to "100001","verse_count" to "1.5","verse_count" to "\"12\"","night_date" to "\"2099-01-01\"","timezone" to "\"Invalid/Zone\"","unexpected" to "true","measured_seconds" to "12")){
            val candidate=body().apply{add(key,JsonParser.parseString(value))}
            assertTrue(key,runCatching{validateNightRepair(candidate)}.isFailure)
        }
    }
    @Test fun repairValidatesRangesAndDoesNotSilentlyDiscardMeasuredData(){
        val candidate=body().apply{remove("verse_count");addProperty("entry_type","range");addProperty("start_index",1);addProperty("end_index",7)}
        validateNightRepair(candidate)
        candidate.addProperty("end_index",6237);assertTrue(runCatching{validateNightRepair(candidate)}.isFailure)
        candidate.addProperty("end_index",7);candidate.addProperty("verse_count",8);assertTrue(runCatching{validateNightRepair(candidate)}.isFailure)
        assertTrue(runCatching{validateNightRepair(body().apply{addProperty("started_at","2026-10-24T20:00:00Z")})}.isFailure)
    }
}
