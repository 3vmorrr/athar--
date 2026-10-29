package org.athar.app.core.data

import com.google.gson.JsonElement
import org.athar.app.quran.QuranIndex

data class SavedCollectionPage(val rows: List<JsonElement>, val page: Int, val total: Int, val hasMore: Boolean)

/** Reject unreadable data before it can become an empty list or unstable lazy keys. */
object SavedCollections {
    private fun integer(value: JsonElement?): Int {
        val primitive = requireNotNull(value).asJsonPrimitive
        require(primitive.isNumber || primitive.isString)
        val text = primitive.asString
        require(text.matches(Regex("0|[1-9][0-9]*")))
        return text.toInt()
    }
    fun rows(tool: String, data: JsonElement, index: QuranIndex? = null): List<JsonElement> {
        val keys = mutableSetOf<String>()
        return data.asJsonArray.toList().onEach { value ->
            val row = value.asJsonObject
            val key = when (tool) {
                "bookmarks" -> {
                    require(integer(row["id"]) > 0)
                    val surah = integer(row["surah"]); val ayah = integer(row["ayah"])
                    requireNotNull(index).global(surah, ayah)
                    require(row["label"]?.asJsonPrimitive?.isString == true)
                    "$surah:$ayah"
                }
                "favorites" -> {
                    val kind = row["target_type"].asJsonPrimitive
                    require(kind.isString && kind.asString in setOf("book", "lesson", "series"))
                    val id = integer(row["target_id"]); require(id > 0)
                    require(row["title"]?.asJsonPrimitive?.isString == true)
                    "${kind.asString}:$id"
                }
                else -> error("Unsupported collection")
            }
            require(keys.add(key))
        }.also { values -> if (tool == "bookmarks") require(values.map { integer(it.asJsonObject["id"]) }.toSet().size == values.size) }
    }
    fun page(tool: String, data: JsonElement, expectedPage: Int, size: Int = 20, index: QuranIndex? = null): SavedCollectionPage {
        val root = data.asJsonObject; val paging = root["pagination"].asJsonObject
        val page = integer(paging["page"]); val perPage = integer(paging["per_page"]); val total = integer(paging["total"])
        require(page == expectedPage && page in 1..10000 && perPage == size && size in 1..50 && total >= 0)
        val more = paging["has_more"].asJsonPrimitive; require(more.isBoolean)
        val rows = rows(tool, root["items"], index)
        val offset = (page - 1).toLong() * size
        require(rows.size.toLong() == (total.toLong() - offset).coerceIn(0, size.toLong()))
        require(more.asBoolean == (offset + rows.size < total))
        return SavedCollectionPage(rows, page, total, more.asBoolean)
    }
}
