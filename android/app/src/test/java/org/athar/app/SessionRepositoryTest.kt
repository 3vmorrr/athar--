package org.athar.app

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.athar.app.auth.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*

class SessionRepositoryTest {
    @Test fun queuedPrivateWritesCannotUseAnotherAccount()=runTest {
        var writes=0
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply()else{writes++;JsonParser.parseString("{}")}},MemoryStore())
        repo.login("reader@example.test","Fixture123!")
        assertTrue(runCatching{repo.request("PUT","me/lesson-progress",expectedUser=2)}.isFailure)
        assertEquals(0,writes);assertTrue(repo.state.value is AuthState.SignedIn)
        repo.request("PUT","me/lesson-progress",expectedUser=1);assertEquals(1,writes)
    }
    private class MemoryStore : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class FakeTransport(val handler: suspend (String, String?) -> JsonElement) : ApiTransport {
        override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String) = handler(path, bearer)
    }
    private fun reply(token: String = "a", scope: String = "full", verified: Boolean = true): JsonElement = JsonParser.parseString("""{"user":{"id":1,"email":"reader@example.test","display_name":"Reader","username":"reader","role":"user","email_verified":$verified},"session":{"id":"s","access_token":"$token","refresh_token":"refresh-$token","expires_in":900,"scope":"$scope"}}""")
    @Test fun pendingAccountsNeverReachMainState() = runTest {
        val store = MemoryStore(); val repo = SessionRepository(FakeTransport { _, _ -> reply(scope = "verification", verified = false) }, store)
        repo.login("reader@example.test", "example password")
        assertTrue(repo.state.value is AuthState.Pending)
        assertEquals("verification", store.value!!.credentials.scope)
    }
    @Test fun concurrentRequestsRotateRefreshOnlyOnce() = runTest {
        var now = 0L; var rotations = 0; val store = MemoryStore()
        val repo = SessionRepository(FakeTransport { path, _ -> when(path) { "auth/refresh" -> { rotations++; yield(); reply("b") }; "auth/login" -> reply(); else -> JsonParser.parseString("{}") } }, store, now = { now })
        repo.login("reader@example.test", "example password"); now = 901_000
        coroutineScope { (1..8).map { async { repo.request("GET", "home") } }.awaitAll() }
        assertEquals(1, rotations); assertEquals("b", store.value!!.credentials.accessToken)
    }
    @Test fun completingRefreshAfterLogoutCannotRestoreCredentials() = runTest {
        var now = 0L; val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>(); val store = MemoryStore()
        val repo = SessionRepository(FakeTransport { path, _ -> if (path == "auth/refresh") { started.complete(Unit); finish.await(); reply("b") } else reply() }, store, now = { now })
        repo.login("reader@example.test", "example password"); now = 901_000
        val request = async { runCatching { repo.request("GET", "home") } }
        started.await(); repo.logout(); finish.complete(Unit); assertTrue(request.await().isFailure)
        assertTrue(repo.state.value is AuthState.SignedOut); assertNull(store.value)
    }
    @Test fun revokedRefreshRemovesStoredSession() = runTest {
        var now = 0L; val store = MemoryStore()
        val repo = SessionRepository(FakeTransport { path, _ -> if (path == "auth/refresh") throw ApiException(401, "unauthenticated", "") else reply() }, store, now = { now })
        repo.login("reader@example.test", "example password"); now = 901_000
        assertTrue(runCatching { repo.request("GET", "home") }.isFailure)
        assertNull(store.value); assertTrue(repo.state.value is AuthState.SignedOut)
    }
}
