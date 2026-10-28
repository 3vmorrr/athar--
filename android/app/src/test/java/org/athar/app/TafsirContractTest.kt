package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.quran.TafsirContract
import org.junit.Assert.*
import org.junit.Test

class TafsirContractTest {
    @Test fun legacyCatalogCacheWithoutPagesRemainsReadable() {
        val catalog = TafsirContract.catalog(JsonParser.parseString("""{
          "items":[{"id":5,"name":"Existing source","author":"Author"}],
          "pagination":{"page":1,"per_page":20,"total":21,"has_more":true},
          "source":{"url":"https://quranpedia.net"}
        }"""))
        assertEquals(2, catalog.pagination.pages)
        assertTrue(catalog.pagination.hasMore)
    }

    @Test fun conflictingTotalsAndDuplicateSourceIdsFailClosed() {
        val payload = """{
          "items":[{"id":5,"name":"Source"},{"id":5,"name":"Other name"}],
          "pagination":{"page":1,"per_page":20,"total":2,"pages":1,"has_more":false},
          "source":{"url":"https://quranpedia.net"}
        }"""
        assertTrue(runCatching { TafsirContract.catalog(JsonParser.parseString(payload)) }.isFailure)
        assertTrue(runCatching { TafsirContract.catalog(JsonParser.parseString(payload.replace("\"pages\":1", "\"pages\":4"))) }.isFailure)
    }
    @Test
    fun catalogAndReadingRequireStablePaginationAndSource() {
        val catalog = TafsirContract.catalog(
            JsonParser.parseString(
                """{
                  "items":[{"id":5,"name":"Reviewed Tafsir","author":"Author"}],
                  "pagination":{"page":1,"per_page":20,"total":1,"pages":1,"has_more":false},
                  "source":{"name":"Quranpedia","url":"https://quranpedia.net"}
                }""",
            ),
        )
        assertEquals(5, catalog.books.single().id)
        assertEquals(1, catalog.pagination.pages)
        assertFalse(catalog.pagination.hasMore)

        val reading = TafsirContract.reading(
            JsonParser.parseString(
                """{
                  "surah":1,
                  "ayah":1,
                  "book":{"id":5,"name":"Reviewed Tafsir","author":"Author"},
                  "items":[{"text":"Safe text","part":1,"page":2,"references":"1:1"}],
                  "pagination":{"page":2,"per_page":8,"total":19,"pages":3,"has_more":true},
                  "source":{"name":"Quranpedia","url":"https://api.quranpedia.net/v1/ayah/1/1/book/5"}
                }""",
            ),
        )
        assertEquals("Safe text", reading.blocks.single().text)
        assertEquals(2, reading.pagination.page)
        assertTrue(reading.pagination.hasMore)
    }

    @Test
    fun malformedOrInconsistentProviderPayloadFailsClosed() {
        val badPaging = JsonParser.parseString(
            """{
              "items":[],
              "pagination":{"page":2,"per_page":20,"total":1,"pages":1,"has_more":true},
              "source":{"url":"https://quranpedia.net"}
            }""",
        )
        assertTrue(runCatching { TafsirContract.catalog(badPaging) }.isFailure)

        val badSource = JsonParser.parseString(
            """{
              "items":[],
              "pagination":{"page":1,"per_page":20,"total":0,"pages":0,"has_more":false},
              "source":{"url":"https://evil.invalid"}
            }""",
        )
        assertTrue(runCatching { TafsirContract.catalog(badSource) }.isFailure)

        val emptyBlock = JsonParser.parseString(
            """{
              "surah":1,
              "ayah":1,
              "book":{"id":5,"name":"Reviewed Tafsir","author":""},
              "items":[{"text":"   ","part":0,"page":0,"references":""}],
              "pagination":{"page":1,"per_page":8,"total":1,"pages":1,"has_more":false},
              "source":{"url":"https://quranpedia.net"}
            }""",
        )
        assertTrue(runCatching { TafsirContract.reading(emptyBlock) }.isFailure)
    }
}
