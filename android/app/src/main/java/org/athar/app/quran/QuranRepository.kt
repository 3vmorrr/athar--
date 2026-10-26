package org.athar.app.quran

import android.content.Context
import android.util.Xml
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest

data class AyahText(val position: QuranPosition, val text: String, val bismillah: String?)
class QuranRepository(private val context: Context) {
    private val mutex = Mutex()
    private var loaded: QuranData? = null
    suspend fun data(): QuranData = mutex.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
            val bytes = context.assets.open("quran/tanzil-uthmani.xml").use { it.readBytes() }
            val source = JsonParser.parseString(context.assets.open("quran/SOURCE.json").bufferedReader().use { it.readText() }).asJsonObject
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(hash == source.get("sha256").asString) { "Quran text integrity check failed" }
            val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); setInput(bytes.inputStream(), "UTF-8") }
            val verses = ArrayList<AyahText>(6236)
            var surah = 0
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                    "sura" -> surah = parser.getAttributeValue(null, "index").toInt()
                    "aya" -> { val ayah = parser.getAttributeValue(null, "index").toInt(); verses += AyahText(index.position(index.global(surah, ayah)), parser.getAttributeValue(null, "text"), parser.getAttributeValue(null, "bismillah")) }
                }
                parser.next()
            }
            check(verses.size == 6236 && verses.map { it.position.globalIndex } == (1..6236).toList())
            QuranData(index, verses).also { loaded = it }
        }
    }
}
class QuranData(val index: QuranIndex, val verses: List<AyahText>) {
    private val searchText = verses.map { normalize(it.text) }
    fun surah(number: Int): List<AyahText> { val start = index.global(number, 1); return verses.subList(start - 1, start - 1 + index.surahs[number - 1].ayahCount) }
    fun section(kind: String, number: Int): List<AyahText> { val range = index.section(kind, number); return verses.subList(range.first - 1, range.last) }
    fun search(query: String): List<AyahText> { val normalized = normalize(query); require(normalized.length >= 2); return verses.filterIndexed { offset, _ -> searchText[offset].contains(normalized) } }
    companion object {
        private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        private val alifs = Regex("[أإآٱ]")
        private val whitespace = Regex("\\s+")
        fun normalize(text: String): String = text.replace(marks, "").replace(alifs, "ا").replace('ى', 'ي').replace(whitespace, " ").trim()
    }
}
