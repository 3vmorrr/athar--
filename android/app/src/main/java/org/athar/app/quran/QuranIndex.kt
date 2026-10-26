package org.athar.app.quran

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SurahReference(val number: Int, val name: String, @SerializedName("english_name") val englishName: String, @SerializedName("ayah_count") val ayahCount: Int, @SerializedName("start_index") val startIndex: Int)
data class QuranPosition(val surah: Int, val ayah: Int, val globalIndex: Int)
data class QuranRange(val start: QuranPosition, val end: QuranPosition) { val verseCount: Int get() = end.globalIndex - start.globalIndex + 1 }
data class SectionReference(val number: Int, val surah: Int, val ayah: Int)
private data class IndexFile(val surahs: List<SurahReference>, @SerializedName("total_ayahs") val total: Int, val sections: Map<String, List<SectionReference>>)

class QuranIndex(val surahs: List<SurahReference>, val sections: Map<String, List<SectionReference>>) {
    init {
        require(surahs.size == 114 && surahs.sumOf { it.ayahCount } == 6236)
        var next = 1
        surahs.forEachIndexed { index, surah -> require(surah.number == index + 1 && surah.startIndex == next && surah.ayahCount > 0); next += surah.ayahCount }
    }
    fun global(surah: Int, ayah: Int): Int {
        require(surah in 1..114)
        val reference = surahs[surah - 1]
        require(ayah in 1..reference.ayahCount)
        return reference.startIndex + ayah - 1
    }
    fun position(global: Int): QuranPosition {
        require(global in 1..6236)
        var low = 0; var high = 113
        while (low <= high) {
            val middle = (low + high) / 2; val surah = surahs[middle]
            when { global < surah.startIndex -> high = middle - 1; global >= surah.startIndex + surah.ayahCount -> low = middle + 1; else -> return QuranPosition(surah.number, global - surah.startIndex + 1, global) }
        }
        error("Invalid canonical index")
    }
    fun range(startSurah: Int, startAyah: Int, endSurah: Int, endAyah: Int): QuranRange {
        val start = global(startSurah, startAyah); val end = global(endSurah, endAyah)
        require(end >= start)
        return QuranRange(position(start), position(end))
    }
    fun target(surah: Int, ayah: Int, count: Int): QuranRange {
        val start = global(surah, ayah)
        require(count in 1..(6237 - start))
        return QuranRange(position(start), position(start + count - 1))
    }
    fun section(kind: String, number: Int): IntRange {
        val key = if (kind == "hizb") "hizb_quarter" else kind
        val refs = sections[key] ?: error("Invalid section")
        val step = if (kind == "hizb") 4 else 1
        require(number in 1..refs.size / step)
        val offset = (number - 1) * step
        val start = global(refs[offset].surah, refs[offset].ayah)
        val end = refs.getOrNull(offset + step)?.let { global(it.surah, it.ayah) - 1 } ?: 6236
        return start..end
    }
    companion object { fun fromJson(json: String): QuranIndex { val data = Gson().fromJson(json, IndexFile::class.java); require(data.total == 6236); return QuranIndex(data.surahs, data.sections) } }
}
