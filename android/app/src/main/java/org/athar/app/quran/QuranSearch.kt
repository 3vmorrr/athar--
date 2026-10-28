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
    // Search-only marker: dagger Alif may be omitted or typed as a normal Alif.
    // It never enters displayed/exported Quran text.
    private const val OPTIONAL_ALIF = '\u0001'
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
            if (char == '\u0670') {
                val previousBase = value.take(index).lastOrNull { !isIgnored(it) }
                if (previousBase != null && previousBase !in charArrayOf('ا', 'ى', 'آ', 'أ', 'إ', 'ٱ')) {
                    text.append(OPTIONAL_ALIF)
                    source += index
                }
                return@forEachIndexed
            }
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

    fun normalize(value: String): String = mapped(value).text.replace(OPTIONAL_ALIF.toString(), "")

    internal fun pattern(rawQuery: String): Regex {
        val query = normalize(rawQuery)
        require(query.length >= 2)
        return Regex(query.map { char ->
            if (char == 'ا') "[ا$OPTIONAL_ALIF]" else Regex.escape(char.toString())
        }.joinToString("$OPTIONAL_ALIF*"))
    }

    internal class SearchDocument internal constructor(
        private val text: String,
        private val source: IntArray,
        private val originalLength: Int,
    ) {
        fun matches(pattern: Regex): Boolean = pattern.containsMatchIn(text)

        fun highlights(pattern: Regex): List<IntRange> = pattern.findAll(text).map { match ->
            val start = source[match.range.first]
            val next = match.range.last + 1
            val end = if (next < source.size) source[next] else originalLength
            start until end
        }.toList()
    }

    internal fun document(value: String): SearchDocument {
        val mapped = mapped(value)
        return SearchDocument(mapped.text, mapped.source, value.length)
    }

    fun highlightRanges(canonicalText: String, rawQuery: String): List<IntRange> {
        if (normalize(rawQuery).length < 2) return emptyList()
        return document(canonicalText).highlights(pattern(rawQuery))
    }
}
