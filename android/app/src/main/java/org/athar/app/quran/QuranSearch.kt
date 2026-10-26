package org.athar.app.quran

data class QuranSearchHit(
    val ayah: AyahText,
    val highlightRanges: List<IntRange>,
)

data class QuranSearchPage(
    val query: String,
    val normalizedQuery: String,
    val hits: List<QuranSearchHit>,
    val total: Int,
    val offset: Int,
    val limit: Int,
) {
    init {
        require(offset >= 0)
        require(limit > 0)
        require(hits.size <= limit)
    }
    val pageNumber: Int get() = if (total == 0) 0 else offset / limit + 1
    val pageCount: Int get() = if (total == 0) 0 else (total + limit - 1) / limit
    val hasPrevious: Boolean get() = offset > 0
    val hasNext: Boolean get() = offset + hits.size < total
}

object QuranSearchNormalizer {
    private fun isIgnored(char: Char): Boolean =
        char == '\u0640' ||
            char in '\u0610'..'\u061A' ||
            char in '\u064B'..'\u065F' ||
            char == '\u0670' ||
            char in '\u06D6'..'\u06ED'

    private fun canonical(char: Char): Char? = when {
        isIgnored(char) -> null
        char in charArrayOf('أ', 'إ', 'آ', 'ٱ') -> 'ا'
        char == 'ى' || char == 'ئ' -> 'ي'
        char == 'ؤ' -> 'و'
        char == 'ة' -> 'ه'
        else -> char
    }

    private data class Mapped(val text: String, val source: IntArray)

    private fun mapped(value: String): Mapped {
        val text = StringBuilder(value.length)
        val source = ArrayList<Int>(value.length)
        value.forEachIndexed { index, char ->
            val normalized = canonical(char) ?: return@forEachIndexed
            if (normalized.isWhitespace()) {
                if (text.isNotEmpty() && text.last() != ' ') {
                    text.append(' ')
                    source += index
                }
            } else {
                text.append(normalized)
                source += index
            }
        }
        while (text.isNotEmpty() && text.last() == ' ') {
            text.deleteCharAt(text.lastIndex)
            source.removeAt(source.lastIndex)
        }
        return Mapped(text.toString(), source.toIntArray())
    }

    fun normalize(value: String): String = mapped(value).text

    fun highlightRanges(canonicalText: String, rawQuery: String): List<IntRange> {
        val haystack = mapped(canonicalText)
        val needle = normalize(rawQuery)
        if (needle.length < 2 || haystack.text.isEmpty()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var from = 0
        while (from <= haystack.text.length - needle.length) {
            val found = haystack.text.indexOf(needle, from)
            if (found < 0) break
            val endNormalizedExclusive = found + needle.length
            val startOriginal = haystack.source[found]
            val endOriginalExclusive = if (endNormalizedExclusive < haystack.source.size) {
                haystack.source[endNormalizedExclusive]
            } else {
                canonicalText.length
            }
            if (endOriginalExclusive > startOriginal) {
                ranges += startOriginal until endOriginalExclusive
            }
            from = endNormalizedExclusive.coerceAtLeast(found + 1)
        }
        return ranges
    }
}
