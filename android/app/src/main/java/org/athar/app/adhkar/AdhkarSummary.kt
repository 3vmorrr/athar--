package org.athar.app.adhkar

import com.google.gson.JsonElement
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import java.time.LocalDate

internal data class AdhkarCategorySummary(val name: String, val items: Long, val completed: Long, val count: Long, val target: Long)
internal data class AdhkarSummary(val date: String, val today: Long, val total: Long, val days: Long, val categories: List<AdhkarCategorySummary>) {
    companion object {
        fun parse(data: JsonElement): AdhkarSummary {
            fun number(row: JsonElement, key: String): Long = row.text(key).let {
                require(Regex("0|[1-9][0-9]*").matches(it))
                it.toLong().also { value -> require(value >= 0) }
            }
            val date = data.text("date")
            require(LocalDate.parse(date).toString() == date)
            val today = number(data, "today_completions")
            val total = number(data, "total_completions")
            val days = number(data, "active_days")
            require(today <= total && days <= total)
            require(data.asJsonObject["categories"]?.isJsonArray == true)
            val categories = data.array("categories").map { row ->
                val name = row.text("category"); require(name.isNotBlank())
                val items = number(row, "item_count"); val completed = number(row, "completed_items")
                val count = number(row, "current_count"); val target = number(row, "target_count")
                require(completed <= items && count <= target)
                AdhkarCategorySummary(name, items, completed, count, target)
            }
            require(categories.map { it.name }.distinct().size == categories.size)
            return AdhkarSummary(date, today, total, days, categories)
        }
    }
}
