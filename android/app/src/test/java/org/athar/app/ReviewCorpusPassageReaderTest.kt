package org.athar.app

import java.io.File
import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewCorpusPassageReaderTest {
    @Test fun streamingReaderKeepsOnlyRequestedAyahsAndTriples() {
        val file = File.createTempFile("review-corpus-", ".json")
        try {
            val filler = (2..114).joinToString(",") { number ->
                """{
                  "n":$number,
                  "name":"S",
                  "nameEn":"S",
                  "ayahs":[
                    {"n":1,"m":"","w":[["x","xx","x"]]}
                  ]
                }"""
            }
            file.writeText(
                """{
                  "v":2,
                  "surahs":[
                    {
                      "n":1,
                      "name":"A",
                      "nameEn":"A",
                      "ayahs":[
                        {"n":1,"m":"m1","w":[["غ1","با","p1"],["غ2","تم","p2"]]},
                        {"n":2,"m":"m2","w":[["غ3","نو","p3"]]},
                        {"n":3,"m":"m3","w":[["غ4","سي","p4"]]}
                      ]
                    },
                    $filler
                  ]
                }""",
            )
            val passage = ReviewCorpusPassageReader.read(
                file,
                surah = 1,
                ayahStart = 2,
                ayahEnd = 3,
            )
            assertEquals(listOf(2, 3), passage.ayahs.map { it.ayah })
            assertEquals(listOf("نو", "سي"), passage.words.map { it.phonemes })
            assertEquals(listOf("غ3", "غ4"), passage.words.map { it.glyph })
        } finally {
            file.delete()
        }
    }

    @Test fun missingOrWrongVersionCorpusFailsClosed() {
        val file = File.createTempFile("review-corpus-", ".json")
        try {
            file.writeText("""{"v":1,"surahs":[]}""")
            assertTrue(
                runCatching {
                    ReviewCorpusPassageReader.read(file, 1, 1, 1)
                }.isFailure,
            )
        } finally {
            file.delete()
        }
    }
}
