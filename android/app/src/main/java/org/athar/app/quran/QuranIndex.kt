package org.athar.app.quran

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class SurahReference(
    val number: Int,
    val name: String,
    @SerializedName("english_name") val englishName: String,
    @SerializedName("ayah_count") val ayahCount: Int,
    @SerializedName("start_index") val startIndex: Int,
)
data class QuranPosition(val surah: Int, val ayah: Int, val globalIndex: Int)
data class QuranRange(val start: QuranPosition, val end: QuranPosition) {
    init { require(end.globalIndex >= start.globalIndex) }
    val verseCount: Int get() = end.globalIndex - start.globalIndex + 1
}
data class SectionReference(val number: Int, val surah: Int, val ayah: Int)
private data class IndexFile(
    val surahs: List<SurahReference>,
    @SerializedName("total_ayahs") val total: Int,
    val sections: Map<String, List<SectionReference>>,
)

class QuranIndex(
    val surahs: List<SurahReference>,
    val sections: Map<String, List<SectionReference>>,
) {
    init {
        require(surahs.size == 114 && surahs.sumOf { it.ayahCount } == TOTAL_AYAHS)
        var next = 1
        surahs.forEachIndexed { index, surah ->
            require(
                surah.number == index + 1 &&
                    surah.startIndex == next &&
                    surah.ayahCount > 0,
            )
            next += surah.ayahCount
        }
        validateSection("page", 604)
        validateSection("juz", 30)
        validateSection("hizb_quarter", 240)
    }

    private fun validateSection(key: String, expected: Int) {
        val refs = requireNotNull(sections[key])
        require(refs.size == expected)
        var previous = 0
        refs.forEach { ref ->
            val current = global(ref.surah, ref.ayah)
            require(current > previous)
            previous = current
        }
    }

    fun global(surah: Int, ayah: Int): Int {
        require(surah in 1..114)
        val reference = surahs[surah - 1]
        require(ayah in 1..reference.ayahCount)
        return reference.startIndex + ayah - 1
    }

    fun position(global: Int): QuranPosition {
        require(global in 1..TOTAL_AYAHS)
        var low = 0
        var high = surahs.lastIndex
        while (low <= high) {
            val middle = (low + high) / 2
            val surah = surahs[middle]
            when {
                global < surah.startIndex -> high = middle - 1
                global >= surah.startIndex + surah.ayahCount -> low = middle + 1
                else -> return QuranPosition(
                    surah.number,
                    global - surah.startIndex + 1,
                    global,
                )
            }
        }
        error("Invalid canonical index")
    }

    fun range(
        startSurah: Int,
        startAyah: Int,
        endSurah: Int,
        endAyah: Int,
    ): QuranRange {
        val start = global(startSurah, startAyah)
        val end = global(endSurah, endAyah)
        require(end >= start)
        return QuranRange(position(start), position(end))
    }

    fun target(surah: Int, ayah: Int, count: Int): QuranRange {
        val start = global(surah, ayah)
        require(count in 1..(TOTAL_AYAHS + 1 - start))
        return QuranRange(position(start), position(start + count - 1))
    }

    fun sectionCount(kind: String): Int = when (kind) {
        "surah" -> surahs.size
        "page" -> requireNotNull(sections["page"]).size
        "juz" -> requireNotNull(sections["juz"]).size
        "hizb" -> requireNotNull(sections["hizb_quarter"]).size / 4
        else -> throw IllegalArgumentException("Invalid Quran section")
    }

    fun section(kind: String, number: Int): IntRange {
        if (kind == "surah") {
            require(number in 1..surahs.size)
            val start = global(number, 1)
            return start..(start + surahs[number - 1].ayahCount - 1)
        }
        val (key, step) = when (kind) {
            "page" -> "page" to 1
            "juz" -> "juz" to 1
            "hizb" -> "hizb_quarter" to 4
            else -> throw IllegalArgumentException("Invalid Quran section")
        }
        val refs = requireNotNull(sections[key])
        require(refs.size % step == 0)
        require(number in 1..(refs.size / step))
        val offset = (number - 1) * step
        val start = global(refs[offset].surah, refs[offset].ayah)
        val end = refs.getOrNull(offset + step)
            ?.let { global(it.surah, it.ayah) - 1 }
            ?: TOTAL_AYAHS
        require(end >= start)
        return start..end
    }

    companion object {
        const val TOTAL_AYAHS = 6236
        fun fromJson(json: String): QuranIndex {
            val data = Gson().fromJson(json, IndexFile::class.java)
            require(data.total == TOTAL_AYAHS)
            return QuranIndex(data.surahs, data.sections)
        }
    }
}
