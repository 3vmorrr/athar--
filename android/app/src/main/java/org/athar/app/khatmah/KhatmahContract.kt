package org.athar.app.khatmah

import com.google.gson.JsonElement
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class KhatmahPlan(val start: LocalDate, val target: LocalDate, val completed: Int, val status: String, val revision: Int) {
    init {
        // Zero is reserved for a local, explicitly unacknowledged projection.
        require(!target.isBefore(start) && completed in 0..604 && revision in 0..Int.MAX_VALUE)
        require(status in setOf("active", "paused", "finished") && (status != "finished" || completed == 604))
    }
}

data class KhatmahDraft(val start: String, val target: String, val pages: String) {
    fun values(today: LocalDate, baseline: KhatmahPlan?): Triple<LocalDate, LocalDate, Int> {
        val a = KhatmahContract.date(start); val b = KhatmahContract.date(target)
        require(pages.matches(Regex("[0-9]{1,3}")))
        val n = pages.toInt(); require(n in 0..604 && !b.isBefore(a))
        require(ChronoUnit.DAYS.between(a, b) <= 1830)
        if (a != baseline?.start || b != baseline?.target) {
            require(!a.isBefore(today.minusDays(1830)) && !a.isAfter(today.plusDays(366)) && !b.isBefore(today))
        }
        return Triple(a, b, n)
    }
    companion object {
        fun from(plan: KhatmahPlan?, today: LocalDate) = KhatmahDraft(
            (plan?.start ?: today).toString(), (plan?.target ?: today.plusDays(30)).toString(), (plan?.completed ?: 0).toString(),
        )
    }
}

object KhatmahContract {
    fun fingerprint(owner: Long, plan: KhatmahPlan?): String {
        require(owner > 0)
        val value = if (plan == null) "$owner|absent" else "$owner|${plan.start}|${plan.target}|604|${plan.completed}|${plan.status}|${plan.revision}"
        return java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun date(value: String): LocalDate {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        return LocalDate.parse(value)
    }
    fun numberInput(value: String): String = buildString {
        for (c in value) {
            val digit = when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c }
            // Preserve invalid input visibly;6040/-1/1.5 must never silently become604/1/15.
            append(digit); if (length == 16) break
        }
    }
    fun parse(data: JsonElement): KhatmahPlan? {
        if (data.isJsonNull) return null
        require(data.isJsonObject); val row = data.asJsonObject
        fun text(key: String): String {
            val value = requireNotNull(row[key]); require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
        fun integer(key: String): Int {
            val value = requireNotNull(row[key]); require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            require(value.toString().matches(Regex("[0-9]{1,10}")))
            return value.toString().toLong().also { require(it <= Int.MAX_VALUE) }.toInt()
        }
        require(integer("total_pages") == 604)
        val revision = integer("revision"); require(revision > 0)
        return KhatmahPlan(date(text("start_date")), date(text("target_date")), integer("completed_pages"), text("status"), revision)
    }
    fun metrics(plan: KhatmahPlan, today: LocalDate): Map<String, Int> {
        val total = ChronoUnit.DAYS.between(plan.start, plan.target).toInt() + 1
        val before = today.isBefore(plan.start); val after = today.isAfter(plan.target)
        val elapsed = if (before) 0 else if (after) total else ChronoUnit.DAYS.between(plan.start, today).toInt() + 1
        val left = if (before) total else if (after) 0 else ChronoUnit.DAYS.between(today, plan.target).toInt() + 1
        val remaining = 604 - plan.completed
        val expected = (604L * elapsed / total).toInt()
        return mapOf(
            "remaining_pages" to remaining, "total_days" to total, "elapsed_days" to elapsed, "days_remaining" to left,
            "starts_in_days" to if (before) ChronoUnit.DAYS.between(today, plan.start).toInt() else 0,
            "daily_pages" to if (plan.status == "active" && !before && left > 0) (remaining + left - 1) / left else 0,
            "planned_daily_pages" to (604 + total - 1) / total, "expected_pages_today" to expected,
            "pages_behind" to (expected - plan.completed).coerceAtLeast(0), "pages_ahead" to (plan.completed - expected).coerceAtLeast(0),
            "progress_percent" to (plan.completed * 100L / 604).toInt(),
        )
    }
}
