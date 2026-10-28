package org.athar.app.books

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AccountExportPolicy
import org.athar.app.core.data.AtharDao
import org.athar.app.core.security.PrivateCipher

/** Read only protected metadata; PDF bytes/paths and unpartitioned legacy preferences stay excluded. */
internal suspend fun retainedBookData(namespace: String, owner: Long, dao: AtharDao, cipher: PrivateCipher, session: SessionRepository): JsonObject {
    session.requireVerifiedOwner(owner)
    val scope = BookScope(namespace, owner)
    val positions = JsonArray()
    for (row in dao.caches(owner, "book-page:%")) {
        require(row.userId == owner)
        val match = requireNotNull(Regex("book-page:([1-9][0-9]*):([a-f0-9]{64})").matchEntire(row.cacheKey))
        val text = cipher.open(row.payloadCipher)
        BookReadingPayload.decode(text, scope, match.groupValues[1].toInt(), match.groupValues[2], Int.MAX_VALUE)
        positions.add(JsonParser.parseString(text))
    }
    val inventory = JsonArray()
    for (row in dao.caches(owner, "book-info:%")) {
        require(row.userId == owner)
        val match = requireNotNull(Regex("book-info:([1-9][0-9]*)").matchEntire(row.cacheKey))
        val body = JsonParser.parseString(cipher.open(row.payloadCipher)).asJsonObject
        require(body["namespace"].asString == namespace && AccountExportPolicy.integer(body["owner"]) == owner)
        require(AccountExportPolicy.integer(body["book"]) == match.groupValues[1].toLong())
        val title = body["title"]
        require(title.isJsonPrimitive && title.asJsonPrimitive.isString && title.asString.isNotBlank() && title.asString.length <= 500)
        inventory.add(body)
    }
    session.requireVerifiedOwner(owner)
    return JsonObject().apply { add("device_book_positions", positions); add("device_book_inventory", inventory) }
}
