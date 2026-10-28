package org.athar.app.books

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.core.data.CachedDocument

/** The inventory keeps the last authorized title even when the public catalog changes. */
internal class BookInventoryStore(private val context: Context, private val graph: AppGraph) {
    private fun authorize(scope: BookScope) { require(scope.namespace == graph.partition); graph.session.requireVerifiedOwner(scope.owner) }
    private fun key(book: Int): String { require(book > 0); return "book-info:$book" }
    suspend fun remember(scope: BookScope, book: Int, title: String) = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && title.length <= 500)
        graph.database.withTransaction {
            authorize(scope); check(!bookFiles(context, scope).ownerRevoked())
            val payload = JsonObject().apply { addProperty("namespace", scope.namespace); addProperty("owner", scope.owner); addProperty("book", book); addProperty("title", title) }
            graph.database.dao().cache(CachedDocument(scope.owner, key(book), graph.cipher.seal(payload.toString()), System.currentTimeMillis()))
        }
        authorize(scope)
    }
    suspend fun title(scope: BookScope, book: Int): String? = withContext(Dispatchers.IO) {
        authorize(scope)
        val row = graph.database.dao().cache(scope.owner, key(book))
        authorize(scope)
        val title = try {
            row?.let {
                require(it.userId == scope.owner && it.cacheKey == key(book))
                val data = JsonParser.parseString(graph.cipher.open(it.payloadCipher)).asJsonObject
                require(data["namespace"].asString == scope.namespace && data["owner"].asLong == scope.owner && data["book"].asInt == book)
                data["title"].asString.takeIf { value -> value.isNotBlank() && value.length <= 500 }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // Public metadata may be unavailable; the owned file ID remains explicit.
        authorize(scope); title
    }
}
