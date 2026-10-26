package org.athar.app.core.data

import com.google.gson.JsonElement
import java.time.LocalDate

/** Absence is explicit JSON null or an empty array, never an unreadable document. */
object AccountPayloads {
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
