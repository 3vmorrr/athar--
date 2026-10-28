package org.athar.app.quran

import com.google.gson.JsonElement
import java.security.MessageDigest
import java.time.LocalDate

/** Same dated selection as the existing QuranText::daily API; no activity mutation. */
object DailyAyah {
    fun globalIndex(date: LocalDate): Int {
        require(date.year in 1..9999)
        val hash = MessageDigest.getInstance("SHA-256").digest(date.toString().toByteArray(Charsets.UTF_8))
        val prefix = hash.take(4).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
        return (prefix % QuranIndex.TOTAL_AYAHS).toInt() + 1
    }

    fun local(data: QuranData, date: LocalDate): AyahText = data.verses[globalIndex(date) - 1]

    fun verified(data: QuranData, date: LocalDate, response: JsonElement): AyahText {
        val root = response.asJsonObject
        require(root["date"].asString == date.toString())
        val source = root["source"].asJsonObject
        require(source["url"].asString == QuranAyahShareContract.SOURCE_URL)
        require(source["version"].asString == "1.1")
        val expected = local(data, date)
        val ayah = root["ayah"].asJsonObject
        require(ayah["global_index"].asInt == expected.position.globalIndex)
        require(ayah["surah"].asInt == expected.position.surah && ayah["ayah"].asInt == expected.position.ayah)
        require(ayah["text"].asString == expected.text)
        // Render only the verified bundle, including its own separate Bismillah field.
        return expected
    }
}
