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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CachedResult(val data: JsonElement, val offline: Boolean)
class ContentRepository(private val session: SessionRepository, private val dao: AtharDao, private val cipher: PrivateCipher, private val language: () -> String = { "ar" },private val cachePolicy:ContentCachePolicy=ContentCachePolicy(),private val now:()->Long=System::currentTimeMillis) {
    private val cacheMutations = Mutex()
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private fun checkOwner(user:Long){session.requireVerifiedOwner(user)}
    private fun cacheKey(path: String, query: Map<String, String>) = language().substringBefore('-').substringBefore(',').let{if(it=="en")"en" else "ar"} + ":" + path + "?" + query.toSortedMap().entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
    suspend fun invalidate(path: String,expectedOwner:Long?=session.userId) {
        val user = session.userId ?: return
        session.requireVerifiedOwner(expectedOwner)
        cacheMutations.withLock {
            checkOwner(user)
            dao.removeContentPath(user, "ar:$path?", "en:$path?")
            changes.value++
        }
    }
    suspend fun get(path: String, query: Map<String, String> = emptyMap(), allowCache: Boolean = true, expectedOwner: Long? = session.userId): CachedResult = withContext(Dispatchers.IO) {
        val user = requireNotNull(expectedOwner)
        val lease = session.lease(user)
        val revision = changes.value
        val key = cacheKey(path, query)
        if(allowCache)dao.trimContentCache(user,now(),cachePolicy)
        try {
            val data = session.request("GET", path, query = query,expectedUser=user)
            if (allowCache) cacheMutations.withLock {
                session.requireLease(lease)
                // An older GET must not repopulate pages invalidated by a mutation.
                if (revision == changes.value) dao.storeContent(CachedDocument(user, key, cipher.seal(data.toString()), now()),now(),cachePolicy)
            }
            session.requireLease(lease)
            CachedResult(data, false)
        } catch (failure: CancellationException) { throw failure }
        catch (failure: IOException) {
            session.requireLease(lease)
            if (failure is ApiException && failure.status in listOf(401, 403, 404, 422)) { if (failure.status == 404) dao.removeCache(user, key); throw failure }
            val cached = if (allowCache) dao.cache(user, key) else null
            if (cached == null) throw failure
            val data=try { JsonParser.parseString(cipher.open(cached.payloadCipher)).also{require(it.isJsonObject||it.isJsonArray)} }
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){dao.removeCache(user,key);throw failure}
            session.requireLease(lease)
            CachedResult(data,true)
        }
    }
}
