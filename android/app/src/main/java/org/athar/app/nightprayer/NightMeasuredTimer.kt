package org.athar.app.nightprayer

import com.google.gson.*
import java.time.*
import java.util.UUID

/** Only verified foreground elapsed time is measured; paused/recovery wall time is never added. */
internal data class NightMeasuredTimer(
    val id: String,
    val date: String,
    val zone: String,
    val method: String,
    val maghrib: String,
    val fajr: String,
    val started: String,
    val activeAt: String,
    val millis: Long = 0,
    val phase: String = "running",
    val reason: String? = null,
    val ended: String? = null,
) {
    init {
        planUuid(id)
        require(zone == "UTC" || zone in ZoneId.getAvailableZoneIds())
        require(method in PrayerCalculator.methods)
        require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && LocalDate.parse(date).year >= 1900)
        val start = Instant.parse(started); val active = Instant.parse(activeAt)
        val context = NightContext(LocalDate.parse(date), ZoneId.of(zone), Instant.parse(maghrib), Instant.parse(fajr))
        require(context.contains(start) && active >= start && active <= context.fajr)
        require(millis in 0..86_400_000 && millis <= Duration.between(start, active).toMillis())
        require(phase in setOf("running", "paused", "finished"))
        require(reason == null || reason in setOf("user", "background", "recovered", "boundary", "clock"))
        if (phase == "running") require(reason == null && ended == null)
        if (phase == "paused") require(reason != null && ended == null)
        if (phase == "finished") require(millis >= 1000 && ended == activeAt)
    }
    fun checkpoint(total: Long, active: Instant, pause: String? = null): NightMeasuredTimer {
        require(phase == "running" && total >= millis && active >= Instant.parse(activeAt))
        return copy(millis = total, activeAt = active.toString(), phase = if (pause == null) "running" else "paused", reason = pause)
    }
    fun recover(): NightMeasuredTimer = if (phase == "running") copy(phase = "paused", reason = "recovered") else this
    fun resume(now: Instant): NightMeasuredTimer {
        require(phase == "paused" && now >= Instant.parse(activeAt) && now < Instant.parse(fajr))
        return copy(phase = "running", reason = null, activeAt = now.toString())
    }
    fun finish(): NightMeasuredTimer {
        require(phase != "finished" && millis >= 1000)
        return copy(phase = "finished", ended = activeAt)
    }
    fun body(entry: JsonObject): JsonObject {
        require(phase == "finished")
        require(entry.keySet() == if (entry["entry_type"]?.asString == "range") setOf("entry_type", "start_index", "end_index", "verse_count", "note") else setOf("entry_type", "verse_count", "note"))
        fun number(key: String): Int = TimerJson.number(entry, key).also { require(it <= Int.MAX_VALUE) }.toInt()
        val count = number("verse_count")
        when (TimerJson.text(entry, "entry_type")) {
            "range" -> { val start = number("start_index"); val end = number("end_index"); require(start in 1..6236 && end in start..6236 && count == end - start + 1) }
            "manual" -> require(count in 1..100000)
            else -> error("invalid_timer_entry")
        }
        require(TimerJson.text(entry, "note").codePointCount(0, entry["note"].asString.length) <= 1000)
        return JsonObject().apply {
            addProperty("night_date", date); addProperty("timezone", zone); addProperty("boundary_source", "calculated")
            addProperty("calculation_method", method); addProperty("maghrib_at", maghrib); addProperty("fajr_at", fajr)
            addProperty("started_at", started); addProperty("ended_at", ended); addProperty("measured_seconds", millis / 1000)
            addProperty("entry_type", TimerJson.text(entry, "entry_type"))
            if (entry["entry_type"].asString == "range") {
                addProperty("start_index", number("start_index")); addProperty("end_index", number("end_index"))
            }
            addProperty("verse_count", count); addProperty("note", TimerJson.text(entry, "note"))
        }
    }
    companion object {
        fun start(identity: NightIdentity, method: String, now: Instant, id: String = UUID.randomUUID().toString()): NightMeasuredTimer {
            require(identity.inProgress && identity.nightDate == identity.context.date && identity.context.contains(now))
            return NightMeasuredTimer(id, identity.nightDate.toString(), NightRules.serverTimezone(identity.context.zone), method,
                identity.context.maghrib.toString(), identity.context.fajr.toString(), now.toString(), now.toString())
        }
    }
}

internal data class NightTimerFrame(val owner: Long, val namespace: String, val version: Long = 0, val timer: NightMeasuredTimer? = null) {
    init { timerOwner(owner, namespace); require(version in 0 until Long.MAX_VALUE && (version > 0 || timer == null)) }
    fun replace(value: NightMeasuredTimer?) = copy(version = version + 1, timer = value)
}

/** Runtime-only monotonic anchor. It is never inferred from persisted uptime after process/reboot. */
internal data class NightTimerAnchor(val base: NightMeasuredTimer, val elapsed: Long, val wall: Instant) {
    init { require(base.phase == "running" && elapsed >= 0 && wall >= Instant.parse(base.activeAt)) }
    fun sample(currentElapsed: Long, now: Instant): NightMeasuredTimer {
        require(currentElapsed >= 0)
        val delta = currentElapsed - elapsed
        val wallDelta = Duration.between(wall, now).toMillis()
        if (delta < 0 || wallDelta < 0 || kotlin.math.abs(wallDelta - delta) > 2000)
            return base.copy(phase = "paused", reason = if (delta < 0) "recovered" else "clock")
        val boundary = Instant.parse(base.fajr)
        val active = minOf(now, boundary)
        val available = Duration.between(wall, boundary).toMillis().coerceAtLeast(0)
        val total = (base.millis + minOf(delta, available)).coerceAtMost(86_400_000)
            .coerceAtMost(Duration.between(Instant.parse(base.started), active).toMillis())
        return base.checkpoint(total, active, if (now >= boundary) "boundary" else null)
    }
}

internal data class NightTimerReceipt(val owner: Long, val namespace: String, val id: String, val fingerprint: String) {
    init { timerOwner(owner, namespace); planUuid(id); require(fingerprint.matches(Regex("[a-f0-9]{64}"))) }
}

private object TimerJson {
    fun text(root: JsonObject, key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
    fun number(root: JsonObject, key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("0|[1-9][0-9]{0,18}"))) }.toString().toLong()
    fun optional(root: JsonObject, key: String) = requireNotNull(root[key]).let { if (it.isJsonNull) null else text(root, key) }
    fun root(owner: Long, namespace: String) = JsonObject().apply { addProperty("schema", 1); addProperty("owner", owner); addProperty("namespace", namespace) }
    fun parse(raw: String, owner: Long, namespace: String, keys: Set<String>): JsonObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= 16384)
        return JsonParser.parseString(raw).asJsonObject.also {
            require(it.keySet() == keys && number(it, "schema") == 1L && number(it, "owner") == owner && text(it, "namespace") == namespace)
        }
    }
}

internal object NightTimerCodec {
    const val KEY = "private:night-timer:v1"
    const val RECORD_PREFIX = "private:night-timer-record:"
    private val timerKeys = setOf("id", "date", "zone", "method", "maghrib", "fajr", "started", "active_at", "millis", "phase", "reason", "ended")
    fun encode(frame: NightTimerFrame): String = TimerJson.root(frame.owner, frame.namespace).apply {
        addProperty("version", frame.version)
        add("timer", frame.timer?.let { t -> JsonObject().apply {
            addProperty("id", t.id); addProperty("date", t.date); addProperty("zone", t.zone); addProperty("method", t.method)
            addProperty("maghrib", t.maghrib); addProperty("fajr", t.fajr); addProperty("started", t.started); addProperty("active_at", t.activeAt)
            addProperty("millis", t.millis); addProperty("phase", t.phase); add("reason", t.reason?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            add("ended", t.ended?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        } } ?: JsonNull.INSTANCE)
    }.toString()
    fun decode(raw: String, owner: Long, namespace: String): NightTimerFrame {
        val root = TimerJson.parse(raw, owner, namespace, setOf("schema", "owner", "namespace", "version", "timer"))
        val timer = requireNotNull(root["timer"]).let { if (it.isJsonNull) null else it.asJsonObject.let { t ->
            require(t.keySet() == timerKeys)
            NightMeasuredTimer(TimerJson.text(t, "id"), TimerJson.text(t, "date"), TimerJson.text(t, "zone"), TimerJson.text(t, "method"),
                TimerJson.text(t, "maghrib"), TimerJson.text(t, "fajr"), TimerJson.text(t, "started"), TimerJson.text(t, "active_at"),
                TimerJson.number(t, "millis"), TimerJson.text(t, "phase"), TimerJson.optional(t, "reason"), TimerJson.optional(t, "ended"))
        } }
        return NightTimerFrame(owner, namespace, TimerJson.number(root, "version"), timer)
    }
    fun receipt(owner: Long, namespace: String, timer: NightMeasuredTimer, body: JsonObject) = NightTimerReceipt(owner, namespace, timer.id,
        java.security.MessageDigest.getInstance("SHA-256").digest(body.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    fun encode(receipt: NightTimerReceipt): String = TimerJson.root(receipt.owner, receipt.namespace).apply { addProperty("id", receipt.id); addProperty("fingerprint", receipt.fingerprint) }.toString()
    fun decodeReceipt(raw: String, owner: Long, namespace: String): NightTimerReceipt {
        val root = TimerJson.parse(raw, owner, namespace, setOf("schema", "owner", "namespace", "id", "fingerprint"))
        return NightTimerReceipt(owner, namespace, TimerJson.text(root, "id"), TimerJson.text(root, "fingerprint"))
    }
}

private fun timerOwner(owner: Long, namespace: String) { require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}"))) }
