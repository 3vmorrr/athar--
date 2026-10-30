package org.athar.app.nightprayer

import com.google.gson.*
import org.athar.app.quran.QuranIndex
import org.athar.app.quran.QuranRange
import java.time.Instant

/** Range identity is part of the protected scope, not a global Quran reading selection. */
internal data class NightReadingScope(val owner: Long, val namespace: String, val kind: String, val id: String, val start: Int, val end: Int) {
    init {
        require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}")))
        require(kind in setOf("plan", "session"))
        if (kind == "plan") planUuid(id) else nightSessionUuid(id)
        require(start in 1..6236 && end in start..6236)
    }
    val key: String get() = NightReadingPositionCodec.PREFIX + kind + ":" + id
    fun range(index: QuranIndex) = QuranRange(index.position(start), index.position(end))
}

internal fun nightSessionUuid(id: String) { require(id.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"))) }

internal data class NightReadingPosition(val scope: NightReadingScope, val version: Long = 0, val index: Int? = null, val updatedAt: String? = null) {
    init {
        require(version in 0 until Long.MAX_VALUE)
        require(index == null || index in scope.start..scope.end)
        if (version == 0L) require(index == null && updatedAt == null) else Instant.parse(requireNotNull(updatedAt))
    }
    fun save(position: Int, expected: Long, now: Instant): NightReadingPosition {
        require(expected == version && position in scope.start..scope.end)
        return if (position == index) this else copy(version = version + 1, index = position, updatedAt = now.toString())
    }
    fun clear(expected: Long, now: Instant): NightReadingPosition {
        require(expected == version)
        // A tombstone also fences an in-flight first save when the prior marker was absent.
        return copy(version = version + 1, index = null, updatedAt = now.toString())
    }
}

internal object NightReadingPositionCodec {
    const val PREFIX = "private:night-reader:v1:"
    fun encode(value: NightReadingPosition): String = JsonObject().apply {
        val scope = value.scope
        addProperty("schema", 1); addProperty("owner", scope.owner); addProperty("namespace", scope.namespace)
        addProperty("kind", scope.kind); addProperty("id", scope.id); addProperty("start", scope.start); addProperty("end", scope.end)
        addProperty("version", value.version)
        add("position", value.index?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        add("updated_at", value.updatedAt?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    }.toString()
    fun decode(raw: String, owner: Long, namespace: String): NightReadingPosition {
        require(raw.toByteArray(Charsets.UTF_8).size <= 4096)
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.keySet() == setOf("schema", "owner", "namespace", "kind", "id", "start", "end", "version", "position", "updated_at"))
        fun text(key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        fun number(key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("0|[1-9][0-9]{0,18}"))) }.toString().toLong()
        require(number("schema") == 1L && number("owner") == owner && text("namespace") == namespace)
        val start = number("start"); val end = number("end"); require(start in 1L..6236 && end in start..6236)
        val scope = NightReadingScope(owner, namespace, text("kind"), text("id"), start.toInt(), end.toInt())
        val position = requireNotNull(root["position"]).let { if (it.isJsonNull) null else number("position").also { p -> require(p in start..end) }.toInt() }
        val stamp = requireNotNull(root["updated_at"]).let { if (it.isJsonNull) null else text("updated_at") }
        return NightReadingPosition(scope, number("version"), position, stamp)
    }
}

internal data class NightPlanCoverage(val owner: Long, val namespace: String, val planId: String, val sessionId: String, val range: IntRange)

/** Only correlated, explicitly recorded sessions can cover this plan. Gaps never get skipped. */
internal fun firstUncoveredNightIndex(scope: NightReadingScope, records: List<NightPlanCoverage>): Int? {
    require(scope.kind == "plan")
    val ranges = records.filter { it.owner == scope.owner && it.namespace == scope.namespace && it.planId == scope.id }
        .mapNotNull { record ->
            planUuid(record.sessionId)
            val start = maxOf(scope.start, record.range.first); val end = minOf(scope.end, record.range.last)
            if (start <= end) start..end else null
        }.sortedBy { it.first }
    var next = scope.start
    for (range in ranges) {
        if (range.first > next) return next
        if (range.last >= next) next = range.last + 1
        if (next > scope.end) return null
    }
    return next
}
