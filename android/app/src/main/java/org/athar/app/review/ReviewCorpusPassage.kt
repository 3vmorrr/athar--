package org.athar.app.review

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.InputStreamReader

data class ReviewExpectedWord(
    val index: Int,
    val glyph: String,
    val phonemes: String,
    val plain: String,
) {
    fun validated(): ReviewExpectedWord {
        require(index >= 0)
        require(glyph.isNotBlank() && glyph.length <= 80)
        require(phonemes.isNotEmpty() && phonemes.length <= 80)
        require(plain.isNotBlank() && plain.length <= 80)
        return this
    }
}

data class ReviewExpectedAyah(
    val surah: Int,
    val ayah: Int,
    val marker: String,
    val words: List<ReviewExpectedWord>,
) {
    fun validated(): ReviewExpectedAyah {
        require(surah in 1..114)
        require(ayah in 1..286)
        require(marker.length <= 24)
        require(words.isNotEmpty() && words.size <= 80)
        words.forEach { it.validated() }
        require(words.map { it.index } == words.indices.toList())
        require(words.sumOf { it.phonemes.length } <= 2_000)
        return this
    }
}

data class ReviewExpectedPassage(
    val surah: Int,
    val ayahStart: Int,
    val ayahEnd: Int,
    val ayahs: List<ReviewExpectedAyah>,
) {
    val words: List<ReviewExpectedWordRef>
        get() = ayahs.flatMap { ayah ->
            ayah.words.map { word ->
                ReviewExpectedWordRef(
                    surah = ayah.surah,
                    ayah = ayah.ayah,
                    word = word.index,
                    glyph = word.glyph,
                    phonemes = word.phonemes,
                    plain = word.plain,
                )
            }
        }

    fun validated(): ReviewExpectedPassage {
        require(surah in 1..114)
        require(ayahStart in 1..286)
        require(ayahEnd >= ayahStart)
        require(ayahEnd - ayahStart + 1 in 1..5)
        require(ayahs.size == ayahEnd - ayahStart + 1)
        require(ayahs.map { it.ayah } == (ayahStart..ayahEnd).toList())
        require(ayahs.all { it.surah == surah })
        ayahs.forEach { it.validated() }
        val flattened = words
        require(flattened.size <= 200)
        require(flattened.sumOf { it.phonemes.length } <= 5_000)
        return this
    }
}

data class ReviewExpectedWordRef(
    val surah: Int,
    val ayah: Int,
    val word: Int,
    val glyph: String,
    val phonemes: String,
    val plain: String,
)

/**
 * Streams the verified v2 corpus and materializes at most one surah object at a time.
 * Only the requested 1-5 ayahs survive the parse.
 */
object ReviewCorpusPassageReader {
    const val MAX_CORPUS_BYTES = 12L * 1024L * 1024L

    fun read(
        file: File,
        surah: Int,
        ayahStart: Int,
        ayahEnd: Int,
    ): ReviewExpectedPassage {
        require(file.isFile && file.length() in 100..MAX_CORPUS_BYTES)
        require(surah in 1..114)
        require(ayahStart >= 1 && ayahEnd >= ayahStart)
        require(ayahEnd - ayahStart + 1 in 1..5)

        val found = ArrayList<ReviewExpectedAyah>()
        file.inputStream().buffered().use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                var version: Int? = null
                var sawSurahs = false
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "v" -> version = reader.nextInt()
                        "surahs" -> {
                            sawSurahs = true
                            reader.beginArray()
                            var seenSurahs = 0
                            while (reader.hasNext()) {
                                require(seenSurahs < 114)
                                val value = JsonParser
                                    .parseReader(reader)
                                    .asJsonObject
                                seenSurahs++
                                val number = value.requiredInt("n")
                                if (number == surah) {
                                    found += selectedAyahs(
                                        value,
                                        surah,
                                        ayahStart,
                                        ayahEnd,
                                    )
                                }
                            }
                            reader.endArray()
                            require(seenSurahs == 114)
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(version == 2)
                require(sawSurahs)
            }
        }

        require(found.map { it.ayah } == (ayahStart..ayahEnd).toList()) {
            "expected_passage_missing"
        }
        return ReviewExpectedPassage(
            surah = surah,
            ayahStart = ayahStart,
            ayahEnd = ayahEnd,
            ayahs = found,
        ).validated()
    }

    private fun selectedAyahs(
        surahValue: JsonObject,
        surah: Int,
        ayahStart: Int,
        ayahEnd: Int,
    ): List<ReviewExpectedAyah> {
        val ayahs = surahValue.getAsJsonArray("ayahs")
        require(ayahs != null && ayahs.size() in 1..286)
        val output = ArrayList<ReviewExpectedAyah>()
        ayahs.forEachIndexed { index, element ->
            require(element.isJsonObject)
            val value = element.asJsonObject
            val number = value.requiredInt("n")
            require(number == index + 1)
            if (number !in ayahStart..ayahEnd) return@forEachIndexed

            val marker = value.requiredString("m")
            require(marker.length <= 24)
            val rawWords = value.getAsJsonArray("w")
            require(rawWords != null && rawWords.size() in 1..80)
            val words = rawWords.mapIndexed { wordIndex, raw ->
                require(raw.isJsonArray)
                val triple = raw.asJsonArray
                require(triple.size() == 3)
                ReviewExpectedWord(
                    index = wordIndex,
                    glyph = triple[0].asString,
                    phonemes = triple[1].asString,
                    plain = triple[2].asString,
                ).validated()
            }
            output += ReviewExpectedAyah(
                surah = surah,
                ayah = number,
                marker = marker,
                words = words,
            ).validated()
        }
        return output
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asInt
    }
}
