package org.athar.app.core.data

import com.google.gson.JsonElement
import java.time.LocalDate

/** Absence is explicit JSON null or an empty array, never an unreadable document. */
object AccountPayloads {
    private val forbiddenExportKeys = setOf(
        "private_exemptions",
        "local_exemptions",
        "device_private_exemptions",
    )

    fun validExport(owner:Long,data:JsonElement):Boolean=runCatching {
        val root=data.asJsonObject
        require(root.entrySet().none { it.key in forbiddenExportKeys })
        val profile=requireNotNull(root["profile"]).asJsonObject
        require(profile["id"].asLong==owner)
        listOf(
            "quran_bookmarks",
            "quran_positions",
            "khatmah_plans",
            "user_favorites",
            "video_progress",
            "account_daily_stats",
            "adhkar_progress",
        ).forEach { key ->
            require(root[key]?.isJsonArray==true)
        }
        root["night_prayer_sessions"]?.let { require(it.isJsonArray) }
        root["night_prayer_preferences"]?.let {
            require(it.isJsonNull||it.isJsonObject||it.isJsonArray)
        }
        true
    }.getOrDefault(false)

    fun validActivitySummary(data:JsonElement):Boolean=runCatching {
        val root=data.asJsonObject
        LocalDate.parse(requireNotNull(root["generated_date"]).asString)
        val availability=requireNotNull(root["availability"]).asJsonObject
        listOf(
            "reading_sessions",
            "listening_history",
            "adhkar_completions",
            "lesson_progress",
            "book_downloads",
        ).forEach { key ->
            require(availability[key]?.asJsonPrimitive?.isBoolean==true)
        }
        val recorded=requireNotNull(root["recorded"]).asJsonObject
        listOf(
            "adhkar_completions",
            "lesson_completions",
            "book_downloads",
            "legacy_quran_sessions",
            "active_days",
            "lessons_started",
            "lessons_complete",
            "watched_seconds",
        ).forEach { key ->
            val value=requireNotNull(recorded[key]).asLong
            require(value>=0)
        }
        require(root["recent"]?.isJsonArray==true)
        root["recent"].asJsonArray.forEach { element ->
            val row=element.asJsonObject
            LocalDate.parse(requireNotNull(row["date"]).asString)
            listOf("lesson_completions","adhkar_completions","book_downloads").forEach { key ->
                require(requireNotNull(row[key]).asInt>=0)
            }
        }
        true
    }.getOrDefault(false)

    fun validCollectionPage(tool:String,data:JsonElement):Boolean=runCatching {
        require(tool in setOf("bookmarks","favorites"))
        val root=data.asJsonObject
        val items=requireNotNull(root["items"]).asJsonArray
        require(valid(tool,items))
        val pagination=requireNotNull(root["pagination"]).asJsonObject
        fun positive(key:String):Int {
            val value=requireNotNull(pagination[key]).asInt
            require(value>0)
            return value
        }
        positive("page")
        positive("per_page")
        require(requireNotNull(pagination["total"]).asInt>=0)
        require(requireNotNull(pagination["has_more"]).isJsonPrimitive)
        true
    }.getOrDefault(false)

    fun valid(tool:String,data:JsonElement):Boolean=runCatching {
        fun number(value:JsonElement?,range:IntRange):Int {
            val text=requireNotNull(value).asString
            require(text.matches(Regex("[0-9]+")))
            return text.toInt().also{require(it in range)}
        }
        when(tool){
            "khatmah"->if(!data.isJsonNull){
                val row=data.asJsonObject
                LocalDate.parse(row["start_date"].asString);LocalDate.parse(row["target_date"].asString)
                val total=number(row["total_pages"],1..604)
                number(row["completed_pages"],0..total)
                require(row["status"].asString in setOf("active","paused","finished"))
            }
            "bookmarks"->data.asJsonArray.forEach{
                val row=it.asJsonObject;number(row["id"],1..Int.MAX_VALUE)
                number(row["surah"],1..114);number(row["ayah"],1..286)
                require(row["label"]?.isJsonPrimitive==true)
            }
            "favorites"->data.asJsonArray.forEach{
                val row=it.asJsonObject;number(row["target_id"],1..Int.MAX_VALUE)
                require(row["target_type"].asString in setOf("lesson","series","book"))
                require(row["title"]?.isJsonPrimitive==true)
            }
            else->error("Unsupported account payload")
        }
        true
    }.getOrDefault(false)
}
