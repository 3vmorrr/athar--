package org.athar.app

import com.google.gson.JsonParser
import java.io.File
import java.time.LocalDate
import org.athar.app.khatmah.*
import org.junit.Assert.*
import org.junit.Test

class KhatmahContractTest {
    @Test fun sharedUtcWorkloadVectorsMatchBackendIncludingLeapDayAnd603Percent() {
        val vectors = JsonParser.parseString(File("../../shared/khatmah-vectors.json").readText()).asJsonArray
        for (item in vectors) {
            val v = item.asJsonObject
            val plan = KhatmahPlan(LocalDate.parse(v["start"].asString), LocalDate.parse(v["target"].asString), v["completed"].asInt, v["status"].asString, 1)
            val today = LocalDate.parse(v["today"].asString)
            val metrics = KhatmahContract.metrics(plan, today)
            assertEquals(v["hash"].asString, KhatmahContract.fingerprint(1, plan))
            assertNotEquals(KhatmahContract.fingerprint(1, plan), KhatmahContract.fingerprint(2, plan))
            for ((key, value) in v["expected"].asJsonObject.entrySet()) {
                if (key == "overdue") assertEquals(value.asBoolean, today.isAfter(plan.target) && plan.completed < 604 && plan.status != "finished")
                else assertEquals(key, value.asInt, metrics.getValue(key))
            }
        }
    }
    @Test fun overdueExceptionRequiresBothDatesUnchangedAndDigitsNormalize() {
        val today = LocalDate.of(2026, 10, 29)
        val plan = KhatmahPlan(today.minusDays(4), today.minusDays(1), 10, "active", 1)
        assertEquals(20, KhatmahDraft(plan.start.toString(), plan.target.toString(), "20").values(today, plan).third)
        assertThrows(IllegalArgumentException::class.java) { KhatmahDraft(plan.start.minusDays(1).toString(), plan.target.toString(), "20").values(today, plan) }
        assertEquals("603", KhatmahContract.numberInput("٦۰٣"))
        for (bad in listOf("6040", "-1", "1.5")) {
            assertEquals(bad, KhatmahContract.numberInput(bad))
            assertThrows(IllegalArgumentException::class.java) { KhatmahDraft(today.toString(), today.toString(), KhatmahContract.numberInput(bad)).values(today, null) }
        }
        assertThrows(IllegalArgumentException::class.java) { KhatmahDraft(today.toString(), today.toString(), "605").values(today, null) }
    }
    @Test fun malformedServerValuesNeverBecomeAnEmptyPlan() {
        val base = """{"start_date":"2026-10-29","target_date":"2026-10-30","total_pages":604,"completed_pages":603,"status":"active","revision":1}"""
        assertEquals(603, KhatmahContract.parse(JsonParser.parseString(base))!!.completed)
        for (bad in listOf(base.replace("\"revision\":1", "\"revision\":0"), base.replace("\"revision\":1", "\"revision\":1.0"), base.replace("\"total_pages\":604", "\"total_pages\":\"604\""), base.replace("2026-10-29", "2026-02-30"), base.replace("active", "finished"))) {
            assertThrows(RuntimeException::class.java) { KhatmahContract.parse(JsonParser.parseString(bad)) }
        }
        assertNull(KhatmahContract.parse(JsonParser.parseString("null")))
    }
}
