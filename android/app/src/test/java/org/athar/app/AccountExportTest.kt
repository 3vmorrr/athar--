package org.athar.app

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.athar.app.core.data.AccountExportPolicy
import org.junit.Assert.*
import org.junit.Test

class AccountExportTest {
    private val namespace = "123456abcdef"
    private fun account() = JsonObject().apply {
        add("profile", JsonObject().apply { addProperty("id", 1); addProperty("display_name", "أثر \"خاص\"\n😀") })
        AccountExportPolicy.accountTables.forEach { add(it, JsonArray()) }
    }
    private fun device() = JsonObject().apply {
        addProperty("owner_id", 1); addProperty("namespace", namespace)
        AccountExportPolicy.deviceSections.forEach { add(it, if (it == "device_reading_position") com.google.gson.JsonNull.INSTANCE else JsonArray()) }
    }
    @Test fun completeOwnedCopyIsIndependentAndPreservesUnicodeEscaping() {
        val account = account(); val device = device()
        val result = AccountExportPolicy.envelope(1, namespace, account, device)
        account.getAsJsonObject("profile").addProperty("display_name", "Changed")
        assertEquals("أثر \"خاص\"\n😀", result.getAsJsonObject("account").getAsJsonObject("profile")["display_name"].asString)
        assertFalse(result.getAsJsonObject("privacy")["local_private_exemptions_included"].asBoolean)
        assertEquals(result, com.google.gson.JsonParser.parseString(result.toString()))
    }
    @Test fun missingSectionsAndCrossOwnerRowsAbortTheWholeExport() {
        for (table in AccountExportPolicy.accountTables) {
            val missing = account().apply { remove(table) }
            assertTrue(runCatching { AccountExportPolicy.envelope(1, namespace, missing, device()) }.isFailure)
            val other = account().apply { getAsJsonArray(table).add(JsonObject().apply { addProperty("user_id", 2) }) }
            assertTrue(runCatching { AccountExportPolicy.envelope(1, namespace, other, device()) }.isFailure)
        }
        val other = account().apply { getAsJsonObject("profile").addProperty("id", 2) }
        assertTrue(runCatching { AccountExportPolicy.envelope(1, namespace, other, device()) }.isFailure)
    }
    @Test fun nestedExemptionsOrCredentialsNeverEnterTheCopy() {
        for (key in listOf("private_exemptions", "local_exemptions", "device_private_exemptions", "password_hash", "access_token", "refresh_token", "payloadCipher", "session_version")) {
            val account = account().apply { getAsJsonObject("profile").add("preferences", JsonObject().apply { add("nested", JsonArray().apply { add(JsonObject().apply { addProperty(key, "private canary") }) }) }) }
            assertTrue(key, runCatching { AccountExportPolicy.envelope(1, namespace, account, device()) }.isFailure)
        }
    }
    @Test fun deviceOwnershipNamespaceAndSectionCompletenessAreRequired() {
        val cases = listOf(device().apply { addProperty("owner_id", 2) }, device().apply { addProperty("namespace", "abcdef123456") }, device().apply { remove("device_adhkar_counters") }, device().apply { add("unknown_private_data", JsonArray()) })
        cases.forEach { assertTrue(runCatching { AccountExportPolicy.envelope(1, namespace, account(), it) }.isFailure) }
        for (section in listOf("device_book_positions", "device_book_inventory")) {
            val bad = device().apply { getAsJsonArray(section).add(JsonObject().apply { addProperty("owner", 2); addProperty("namespace", namespace) }) }
            assertTrue(runCatching { AccountExportPolicy.envelope(1, namespace, account(), bad) }.isFailure)
        }
    }
}
