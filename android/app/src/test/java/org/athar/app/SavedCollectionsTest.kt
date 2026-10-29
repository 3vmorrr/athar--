package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.core.data.SavedCollections
import org.junit.Assert.*
import org.junit.Test

class SavedCollectionsTest {
    private fun json(value: String) = JsonParser.parseString(value)
    private val bookmark = """{"id":"7","surah":"114","ayah":"6","label":"saved"}"""
    private val favorite = """{"target_type":"book","target_id":"9","title":"Book"}"""
    private fun page(rows: String, total: String = "1", more: String = "false", page: String = "1") = json("""{"items":[$rows],"pagination":{"page":$page,"per_page":20,"total":$total,"has_more":$more}}""")
    @Test fun canonicalCoordinatesAndStableKeysAreRequired() {
        val index = QuranJvmFixture.data.index
        assertEquals(1, SavedCollections.rows("bookmarks", json("[$bookmark]"), index).size)
        for (rows in listOf("[$bookmark,$bookmark]", "[${bookmark.replace("\"6\"", "\"7\"")}]", "[${bookmark.replace("\"saved\"", "true")}]", "[${bookmark.replace("\"7\"", "1.5")}]"))
            assertTrue(runCatching { SavedCollections.rows("bookmarks", json(rows), index) }.isFailure)
        assertTrue(runCatching { SavedCollections.rows("bookmarks", json("[$bookmark]")) }.isFailure)
    }
    @Test fun pageMetadataMustMatchRequestAndActualRows() {
        assertEquals(1, SavedCollections.page("favorites", page(favorite), 1).total)
        for (bad in listOf(page(favorite, more="\"false\""),page(favorite, more="true"),page(favorite,total="21"),page(favorite,page="2"),page(favorite,total="1.0"),page(favorite,total="true"),page(favorite,total="2147483648")))
            assertTrue(runCatching { SavedCollections.page("favorites",bad,1) }.isFailure)
        assertFalse(SavedCollections.page("favorites",page("",total="0"),1).hasMore)
        assertEquals(0,SavedCollections.page("favorites",page("",total="1",page="2"),2).rows.size)
    }
    @Test fun favoritesRejectDuplicateMalformedAndUnsupportedTargets() {
        for (rows in listOf("[$favorite,$favorite]", "[${favorite.replace("book","user")}]", "[${favorite.replace("\"Book\"","false")}]", "[${favorite.replace("\"9\"","\"0\"")}]"))
            assertTrue(runCatching { SavedCollections.rows("favorites",json(rows)) }.isFailure)
    }
    @Test fun completeLegacyReaderSetDoesNotSilentlyStopAtFiveHundred() {
        val index=QuranJvmFixture.data.index
        val rows=(1..6236).joinToString(",") { global ->
            val position=index.position(global)
            """{"id":$global,"surah":${position.surah},"ayah":${position.ayah},"label":""}"""
        }
        assertEquals(6236,SavedCollections.rows("bookmarks",json("[$rows]"),index).size)
    }
}
