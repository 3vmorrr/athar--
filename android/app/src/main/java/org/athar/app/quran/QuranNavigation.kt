package org.athar.app.quran

object QuranReferenceInput {
    fun canonicalizeDigits(value: String, maxDigits: Int = 3): String? {
        if (value.length > maxDigits) return null
        if (value.isEmpty()) return ""
        val out = StringBuilder(value.length)
        for (char in value) {
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in '\u0660'..'\u0669' -> char.code - '\u0660'.code
                in '\u06F0'..'\u06F9' -> char.code - '\u06F0'.code
                else -> return null
            }
            out.append(('0'.code + digit).toChar())
        }
        return out.toString()
    }

    fun number(value: String, range: IntRange, maxDigits: Int = 3): Int? =
        canonicalizeDigits(value, maxDigits)
            ?.takeIf { it.isNotEmpty() }
            ?.toIntOrNull()
            ?.takeIf { it in range }

    fun position(index: QuranIndex, surah: String, ayah: String): QuranPosition? {
        val surahNumber = number(surah, 1..114) ?: return null
        val ayahNumber = number(
            ayah,
            1..index.surahs[surahNumber - 1].ayahCount,
        ) ?: return null
        return index.position(index.global(surahNumber, ayahNumber))
    }

    fun range(
        index: QuranIndex,
        startSurah: String,
        startAyah: String,
        endSurah: String,
        endAyah: String,
    ): QuranRange? {
        val start = position(index, startSurah, startAyah) ?: return null
        val end = position(index, endSurah, endAyah) ?: return null
        if (end.globalIndex < start.globalIndex) return null
        return QuranRange(start, end)
    }

    fun sectionNumber(index: QuranIndex, kind: String, value: String): Int? =
        runCatching { number(value, 1..index.sectionCount(kind)) }.getOrNull()
}
