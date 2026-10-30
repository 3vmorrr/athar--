package org.athar.app.nightprayer

import com.google.gson.*
import org.athar.app.quran.QuranIndex
import org.athar.app.quran.QuranRange
import java.time.*
import java.util.UUID

internal data class NightVersePlan(val id: String, val startIndex: Int, val endIndex: Int, val updatedAt: String) {
    init { planUuid(id); require(startIndex in 1..6236 && endIndex in startIndex..6236); Instant.parse(updatedAt) }
    val verseCount: Int get() = endIndex - startIndex + 1
    fun range(index: QuranIndex) = QuranRange(index.position(startIndex), index.position(endIndex))
}

/** Clearing keeps a versioned tombstone, so an older form cannot recreate a cleared plan through ABA. */
internal data class NightPlanState(val owner: Long, val namespace: String, val version: Long = 0, val plan: NightVersePlan? = null) {
    init { planOwner(owner, namespace); require(version in 0 until Long.MAX_VALUE && (plan == null || version > 0)) }
    fun save(start: Int, end: Int, expected: Long, now: Instant): NightPlanState {
        require(version == expected)
        require(start in 1..6236 && end in start..6236)
        if (plan?.startIndex == start && plan.endIndex == end) return this
        return copy(version = version + 1, plan = NightVersePlan(UUID.randomUUID().toString(), start, end, now.toString()))
    }
    fun clear(expected: Long): NightPlanState {
        require(version == expected)
        return if (plan == null) this else copy(version = version + 1, plan = null)
    }
}

internal fun planUuid(value: String) { require(value.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"))) }
private fun planOwner(owner: Long, namespace: String) { require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}"))) }

/** A local receipt binds an explicit recording operation; it never advances Quran/Khatmah positions. */
internal data class NightPlanRecord(val owner: Long, val namespace: String, val id: String, val plan: NightVersePlan, val body: JsonObject) {
    init {
        planOwner(owner, namespace); planUuid(id)
        require(body.keySet() == setOf("night_date", "timezone", "boundary_source", "entry_type", "start_index", "end_index", "verse_count", "note"))
        val date = PlanJson.text(body, "night_date")
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(date).year >= 1900)
        val zone = PlanJson.text(body, "timezone"); require(zone == "UTC" || zone in ZoneId.getAvailableZoneIds())
        require(PlanJson.text(body, "boundary_source") == "manual" && PlanJson.text(body, "entry_type") == "range")
        require(PlanJson.number(body, "start_index") == plan.startIndex.toLong() && PlanJson.number(body, "end_index") == plan.endIndex.toLong())
        require(PlanJson.number(body, "verse_count") == plan.verseCount.toLong())
        val note = PlanJson.text(body, "note"); require(note.codePointCount(0, note.length) <= 1000)
    }
    fun receipt(): NightPlanReceipt {
        val checked = copy(body = body.deepCopy()) // Recheck mutable JSON before fingerprinting.
        return NightPlanReceipt(owner, namespace, id, plan.id,
            java.security.MessageDigest.getInstance("SHA-256").digest(checked.body.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    }
    companion object {
        fun create(owner: Long, namespace: String, id: String, plan: NightVersePlan, date: LocalDate, zone: ZoneId, note: String): NightPlanRecord =
            NightPlanRecord(owner, namespace, id, plan, JsonObject().apply {
                addProperty("night_date", date.toString()); addProperty("timezone", NightRules.serverTimezone(zone))
                addProperty("boundary_source", "manual"); addProperty("entry_type", "range")
                addProperty("start_index", plan.startIndex); addProperty("end_index", plan.endIndex)
                addProperty("verse_count", plan.verseCount); addProperty("note", note)
            })
    }
}

/** Keeps idempotency after a history deletion without retaining another plaintext note/date/range. */
internal data class NightPlanReceipt(val owner: Long, val namespace: String, val id: String, val planId: String, val fingerprint: String) {
    init { planOwner(owner, namespace); planUuid(id); planUuid(planId); require(fingerprint.matches(Regex("[a-f0-9]{64}"))) }
}

private object PlanJson {
    fun text(root: JsonObject, key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
    fun number(root: JsonObject, key: String) = requireNotNull(root[key]).also {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("0|[1-9][0-9]{0,18}")))
    }.toString().toLong()
    fun plan(plan: NightVersePlan): JsonObject = JsonObject().apply {
        addProperty("id", plan.id); addProperty("start_index", plan.startIndex); addProperty("end_index", plan.endIndex); addProperty("updated_at", plan.updatedAt)
    }
    fun plan(value: JsonElement): NightVersePlan = value.asJsonObject.let {
        require(it.keySet() == setOf("id", "start_index", "end_index", "updated_at"))
        val start = number(it, "start_index"); val end = number(it, "end_index"); require(start in 1L..6236 && end in start..6236)
        NightVersePlan(text(it, "id"), start.toInt(), end.toInt(), text(it, "updated_at"))
    }
    fun parse(raw: String, keys: Set<String>, owner: Long, namespace: String): JsonObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= 16384)
        val root = JsonParser.parseString(raw).asJsonObject; require(root.keySet() == keys)
        require(number(root, "schema") == 1L && number(root, "owner") == owner && text(root, "namespace") == namespace)
        return root
    }
    fun root(owner: Long, namespace: String) = JsonObject().apply { addProperty("schema", 1); addProperty("owner", owner); addProperty("namespace", namespace) }
}

internal object NightPlanCodec {
    const val KEY = "private:night-verse-plan:v1"
    const val RECORD_PREFIX = "private:night-plan-record:"
    fun encode(state: NightPlanState): String = PlanJson.root(state.owner, state.namespace).apply {
        addProperty("version", state.version); add("plan", state.plan?.let(PlanJson::plan) ?: JsonNull.INSTANCE)
    }.toString()
    fun decode(raw: String, owner: Long, namespace: String): NightPlanState {
        val root = PlanJson.parse(raw, setOf("schema", "owner", "namespace", "version", "plan"), owner, namespace)
        return NightPlanState(owner, namespace, PlanJson.number(root, "version"), requireNotNull(root["plan"]).let { if (it.isJsonNull) null else PlanJson.plan(it) })
    }
    fun encode(record: NightPlanReceipt): String = PlanJson.root(record.owner, record.namespace).apply {
        addProperty("id", record.id); addProperty("plan_id", record.planId); addProperty("fingerprint", record.fingerprint)
    }.toString()
    fun decodeReceipt(raw: String, owner: Long, namespace: String): NightPlanReceipt {
        val root = PlanJson.parse(raw, setOf("schema", "owner", "namespace", "id", "plan_id", "fingerprint"), owner, namespace)
        return NightPlanReceipt(owner, namespace, PlanJson.text(root, "id"), PlanJson.text(root, "plan_id"), PlanJson.text(root, "fingerprint"))
    }
}
