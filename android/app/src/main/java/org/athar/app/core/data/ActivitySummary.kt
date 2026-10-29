package org.athar.app.core.data

import com.google.gson.JsonElement
import java.time.LocalDate

data class ActivityDay(val date: LocalDate, val lessons: Long, val adhkar: Long)
data class ActivitySummary(val date: LocalDate, val adhkar: Long, val lessons: Long, val activeDays: Long, val lessonsWithProgress: Long, val completedLessons: Long, val recent: List<ActivityDay>) {
    companion object {
        private fun number(value: JsonElement?): Long {
            val primitive = requireNotNull(value).asJsonPrimitive
            require(primitive.isNumber || primitive.isString)
            val raw = primitive.asString; require(raw.matches(Regex("0|[1-9][0-9]*")))
            return raw.toLong()
        }
        fun parse(data: JsonElement, owner: Long): ActivitySummary {
            val root = data.asJsonObject
            require(owner > 0 && number(root["schema_version"]) == 1L && number(root["owner_id"]) == owner)
            val generated = root["generated_date"].asJsonPrimitive; require(generated.isString)
            val date = LocalDate.parse(generated.asString)
            val availability = root["availability"].asJsonObject
            for ((key, expected) in mapOf("reading_sessions" to false, "listening_history" to false, "book_downloads" to false, "adhkar_completions" to true, "lesson_progress" to true)) {
                val value = availability[key].asJsonPrimitive
                require(value.isBoolean && value.asBoolean == expected)
            }
            val recorded = root["recorded"].asJsonObject
            val adhkar = number(recorded["adhkar_completions"]); val lessons = number(recorded["lesson_completions"])
            val active = number(recorded["active_days"]); val started = number(recorded["lessons_with_progress"]); val complete = number(recorded["completed_lessons"])
            number(recorded["legacy_book_download_events"]); number(recorded["legacy_quran_sessions"])
            require(complete <= started)
            val recent = root["recent"].asJsonArray.map {
                val row = it.asJsonObject; val day = row["date"].asJsonPrimitive; require(day.isString)
                val parsed = LocalDate.parse(day.asString); require(parsed in date.minusDays(13)..date)
                ActivityDay(parsed, number(row["lesson_completions"]), number(row["adhkar_completions"]))
            }
            require(recent.size <= 14 && recent.map { it.date }.distinct().size == recent.size && recent.map { it.date } == recent.map { it.date }.sorted())
            require(recent.all { it.lessons > 0 || it.adhkar > 0 })
            // Subtract rather than sum: a malicious large count must not overflow validation.
            var remainingLessons = lessons; var remainingAdhkar = adhkar
            for (day in recent) { require(day.lessons <= remainingLessons && day.adhkar <= remainingAdhkar); remainingLessons -= day.lessons; remainingAdhkar -= day.adhkar }
            require(recent.size.toLong() <= active)
            return ActivitySummary(date, adhkar, lessons, active, started, complete, recent)
        }
    }
}
