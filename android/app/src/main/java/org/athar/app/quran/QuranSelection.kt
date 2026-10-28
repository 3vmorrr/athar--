package org.athar.app.quran

/** View state only. Never substitutes for the protected explicit reading position. */
internal data class QuranSelection(
    val kind: String = "surah",
    val number: Int = 1,
    val ayah: Int = 1,
    val endSurah: Int = 1,
    val endAyah: Int = 1,
) {
    fun arguments() = intArrayOf(number, ayah, endSurah, endAyah)

    fun resolve(data: QuranData, request: Long): QuranState {
        require(request > 0)
        val target = when (kind) {
            "surah" -> data.index.global(number, ayah)
            "range" -> data.index.global(number, ayah)
            else -> data.index.section(kind, number).first
        }
        val range = if (kind == "range") data.index.range(number, ayah, endSurah, endAyah) else null
        val verses = when (kind) {
            "surah" -> data.surah(number)
            "range" -> data.range(requireNotNull(range))
            else -> data.section(kind, number)
        }
        return QuranState(
            loading = false, index = data.index, verses = verses,
            surah = if (kind == "surah") number else 0,
            sectionKind = kind, sectionNumber = if (kind == "range") 0 else number,
            range = range, scrollTarget = target, scrollRequest = request,
        )
    }

    companion object {
        fun restore(kind: String?, arguments: IntArray?): QuranSelection {
            if (kind == null && arguments == null) return QuranSelection()
            require(kind in setOf("surah", "juz", "hizb", "page", "range"))
            val values = requireNotNull(arguments)
            require(values.size == 4)
            return QuranSelection(requireNotNull(kind), values[0], values[1], values[2], values[3])
        }
    }
}
