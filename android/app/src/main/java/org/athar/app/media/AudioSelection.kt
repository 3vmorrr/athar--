package org.athar.app.media

import com.google.gson.JsonElement
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text

/** Selection is independent of the currently loaded media item. */
data class AudioSelection(
    val surah: Int = 1,
    val ayah: Int = 1,
    val reader: JsonElement? = null,
    val edition: JsonElement? = null,
    val verseMode: Boolean = false,
    val follow: Boolean = false,
) {
    fun withReader(value: JsonElement): AudioSelection = copy(reader = value, edition = supported(value).firstOrNull())
    fun withSurah(number: Int): AudioSelection {
        require(number in 1..114)
        val next = copy(surah = number, ayah = 1)
        val available = next.supported(reader)
        return next.copy(edition = available.firstOrNull { it.text("id") == edition?.text("id") } ?: available.firstOrNull())
    }
    fun supported(value: JsonElement? = reader): List<JsonElement> = value?.array("editions")?.filter { edition ->
        edition.array("surahs").any { it.asInt == surah }
    }.orEmpty()
    val mediaKey: String? get() = reader?.let { r -> edition?.let { e -> "${r.text("id")}:${e.text("id")}:$surah:${if (verseMode) ayah else 0}" } }
}
