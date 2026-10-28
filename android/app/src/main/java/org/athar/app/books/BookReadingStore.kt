package org.athar.app.books

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.AppDatabase
import org.athar.app.auth.SessionRepository
import org.athar.app.core.security.PrivateCipher
import java.io.IOException

internal object BookReadingPayload {
    fun key(book: Int, digest: String): String {
        require(book > 0 && digest.matches(Regex("[a-f0-9]{64}")))
        return "book-page:$book:$digest"
    }
    fun encode(scope: BookScope, book: Int, digest: String, page: Int): String = JsonObject().apply {
        key(book, digest); require(page >= 0)
        addProperty("version", 1); addProperty("namespace", scope.namespace); addProperty("owner", scope.owner)
        addProperty("book", book); addProperty("sha256", digest); addProperty("page", page)
    }.toString()
    fun decode(text: String, scope: BookScope, book: Int, digest: String, pages: Int): Int {
        key(book, digest)
        val row = JsonParser.parseString(text).asJsonObject
        fun number(key: String): Long = requireNotNull(row[key]).asString.let { require(it.matches(Regex("0|[1-9][0-9]*"))); it.toLong() }
        require(number("version") == 1L && row["namespace"].asString == scope.namespace && number("owner") == scope.owner)
        require(number("book") == book.toLong() && row["sha256"].asString == digest)
        val page = number("page")
        require(page in 0 until pages.toLong())
        return page.toInt()
    }
}

internal class BookReadingStore(private val context: Context, private val partition: String, private val database: AppDatabase, private val cipher: PrivateCipher, private val session: SessionRepository) {
    constructor(context: Context, graph: AppGraph) : this(context, graph.partition, graph.database, graph.cipher, graph.session)
    private fun authorize(scope: BookScope) { require(scope.namespace == partition); session.requireVerifiedOwner(scope.owner) }
    suspend fun read(scope: BookScope, book: Int, digest: String, pages: Int): Int = withContext(Dispatchers.IO) {
        authorize(scope)
        val key = BookReadingPayload.key(book, digest)
        val row = database.dao().cache(scope.owner, key)
        authorize(scope)
        if (row == null) {
            // Preserve unpartitioned source-branch preferences; never silently assign them to an API realm.
            if (context.getSharedPreferences(LEGACY_BOOK_READER_PREFERENCES, Context.MODE_PRIVATE).contains("page:${scope.owner}:$book")) throw IOException("book_legacy_progress_requires_review")
            return@withContext 0
        }
        require(row.userId == scope.owner && row.cacheKey == key)
        BookReadingPayload.decode(cipher.open(row.payloadCipher), scope, book, digest, pages).also { authorize(scope) }
    }
    suspend fun save(scope: BookScope, book: Int, digest: String, pages: Int, page: Int) = withContext(Dispatchers.IO) {
        require(page in 0 until pages)
        val key = BookReadingPayload.key(book, digest)
        database.withTransaction {
            authorize(scope)
            check(!bookFiles(context, scope).ownerRevoked())
            database.dao().cache(scope.owner, key)?.let { existing ->
                require(existing.userId == scope.owner && existing.cacheKey == key)
                BookReadingPayload.decode(cipher.open(existing.payloadCipher), scope, book, digest, pages)
            }
            database.dao().cache(CachedDocument(scope.owner, key, cipher.seal(BookReadingPayload.encode(scope, book, digest, page)), System.currentTimeMillis()))
        }
        authorize(scope)
    }
}

internal const val LEGACY_BOOK_READER_PREFERENCES = "athar-book-reader"
