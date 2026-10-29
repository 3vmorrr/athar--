package org.athar.app.core.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate

internal data class BookCompletionEvent(val namespace: String, val owner: Long, val book: Int, val eventId: String, val sha256: String, val date: LocalDate) {
    init {
        require(namespace.matches(Regex("[a-f0-9]{12}")) && owner > 0 && book > 0)
        require(eventId.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")))
        require(sha256.matches(Regex("[a-f0-9]{64}")) && date.toString().matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
    }
    override fun toString() = "BookCompletionEvent(redacted)"
    fun json() = JsonObject().apply {
        addProperty("version", 1); addProperty("namespace", namespace); addProperty("owner", owner)
        addProperty("book", book); addProperty("event_id", eventId); addProperty("sha256", sha256); addProperty("activity_date", date.toString())
    }
    companion object {
        private fun number(value: JsonElement?): Long {
            val primitive = requireNotNull(value).asJsonPrimitive
            require(primitive.isNumber || primitive.isString)
            val text = primitive.asString; require(text.matches(Regex("[1-9][0-9]*")))
            return text.toLong()
        }
        fun parse(value: JsonElement, namespace: String, owner: Long, eventId: String): BookCompletionEvent {
            val root = value.asJsonObject
            require(root.keySet() == setOf("version", "namespace", "owner", "book", "event_id", "sha256", "activity_date"))
            require(number(root["version"]) == 1L && number(root["owner"]) == owner)
            fun text(key: String): String { val field = root[key].asJsonPrimitive; require(field.isString); return field.asString }
            require(text("namespace") == namespace && text("event_id") == eventId)
            val book = number(root["book"]); require(book <= Int.MAX_VALUE)
            return BookCompletionEvent(namespace, owner, book.toInt(), eventId, text("sha256"), LocalDate.parse(text("activity_date")))
        }
    }
}
