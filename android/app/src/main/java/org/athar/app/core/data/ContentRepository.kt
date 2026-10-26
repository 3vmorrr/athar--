package org.athar.app.core.data

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CachedResult(val data: JsonElement, val offline: Boolean)
class ContentRepository(private val session: SessionRepository, private val dao: AtharDao, private val cipher: PrivateCipher, private val language: () -> String = { "ar" }) {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private fun cacheKey(path: String, query: Map<String, String>) = language() + ":" + path + "?" + query.toSortedMap().entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
    suspend fun invalidate(path: String) {
        val user = session.userId ?: return
        dao.removeCache(user, cacheKey(path, emptyMap()))
        changes.value++
    }
    suspend fun get(path: String, query: Map<String, String> = emptyMap(), allowCache: Boolean = true): CachedResult = withContext(Dispatchers.IO) {
        val user = session.userId ?: throw ApiException(401, "unauthenticated", "")
        val key = cacheKey(path, query)
        try {
            val data = session.request("GET", path, query = query,expectedUser=user)
            if (allowCache) dao.cache(CachedDocument(user, key, cipher.seal(data.toString()), System.currentTimeMillis()))
            CachedResult(data, false)
        } catch (failure: CancellationException) { throw failure }
        catch (failure: IOException) {
            if (failure is ApiException && failure.status in listOf(401, 403, 404, 422)) { if (failure.status == 404) dao.removeCache(user, key); throw failure }
            val cached = if (allowCache) dao.cache(user, key) else null
            if (cached == null) throw failure
            CachedResult(JsonParser.parseString(cipher.open(cached.payloadCipher)), true)
        }
    }
}
