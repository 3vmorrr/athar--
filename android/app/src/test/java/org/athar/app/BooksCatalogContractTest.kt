package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.books.BookCatalogContract
import org.junit.Assert.*
import org.junit.Test

class BooksCatalogContractTest {
    @Test fun catalogUsesOnlyRealReturnedMetadata() {
        val item=BookCatalogContract.item(
            JsonParser.parseString(
                """{
                  "id":7,
                  "title":"A real title",
                  "author":"A real author",
                  "description":"Returned description",
                  "publication_year":"2024",
                  "language":"ar"
                }""",
            ),
        )
        assertEquals(7,item.id)
        assertEquals("A real title",item.title)
        assertEquals("A real author",item.author)
        assertEquals("2024",item.publicationYear)
        assertEquals("ar",item.language)
    }

    @Test fun optionalEditorialMetadataMayBeAbsentWithoutFabrication() {
        val item=BookCatalogContract.item(
            JsonParser.parseString("""{"id":3,"title":"Only title"}"""),
        )
        assertNull(item.author)
        assertNull(item.description)
        assertNull(item.publicationYear)
        assertNull(item.language)
    }

    @Test fun invalidIdsTitlesAndDuplicatePageRowsFailClosed() {
        assertTrue(runCatching{
            BookCatalogContract.item(JsonParser.parseString("""{"id":0,"title":"Bad"}"""))
        }.isFailure)
        assertTrue(runCatching{
            BookCatalogContract.item(JsonParser.parseString("""{"id":1,"title":""}"""))
        }.isFailure)

        val page=JsonParser.parseString(
            """{
              "items":[{"id":1,"title":"A"},{"id":1,"title":"B"}],
              "pagination":{"has_more":false}
            }""",
        )
        assertTrue(runCatching{BookCatalogContract.page(page)}.isFailure)
    }

    @Test fun paginationDefaultsSafelyWhenMetadataIsMissing() {
        val page=BookCatalogContract.page(
            JsonParser.parseString("""{"items":[{"id":1,"title":"A"}]}"""),
        )
        assertEquals(listOf(1),page.first.map{it.id})
        assertFalse(page.second)
    }
}
