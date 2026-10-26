package org.athar.app.media

/**
 * Provider timing data is optional. A malformed timing table must never be used for clipping or
 * follow-along because an incorrect boundary would attribute the wrong audio to an ayah.
 */
internal fun validatedAyahTimings(
    rows: List<AyahTiming>,
    verseCount: Int,
): List<AyahTiming> {
    if (verseCount < 1 || rows.isEmpty()) return emptyList()

    val ordered = rows.sortedWith(
        compareBy<AyahTiming> { it.ayah }.thenBy { it.start },
    )
    if (ordered.size != ordered.map { it.ayah }.distinct().size) return emptyList()

    var previous: AyahTiming? = null
    for (row in ordered) {
        if (row.ayah !in 1..verseCount) return emptyList()
        if (row.start < 0L || row.end <= row.start) return emptyList()
        // Quran audio is bounded; reject obviously corrupt millisecond values before Media3 sees them.
        if (row.end > 24L * 60L * 60L * 1000L) return emptyList()

        val prior = previous
        if (prior != null) {
            if (row.ayah <= prior.ayah) return emptyList()
            if (row.start < prior.end) return emptyList()
        }
        previous = row
    }
    return ordered
}

internal fun timingForAyah(
    timings: List<AyahTiming>,
    ayah: Int,
): AyahTiming? = timings.firstOrNull { it.ayah == ayah }
