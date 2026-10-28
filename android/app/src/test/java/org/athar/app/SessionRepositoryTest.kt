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
    @Test fun exportLeaseExpiresEvenWhenTheSameOwnerSignsInAgain()=runTest {
        var actor=1
        val repo=SessionRepository(FakeTransport{_,_->reply(id=actor)},MemoryStore())
        repo.login("reader@example.test","Fixture123!")
        val first=repo.lease(1);repo.requireLease(first)
        repo.login("reader@example.test","Fixture123!")
        assertTrue(runCatching{repo.requireLease(first)}.exceptionOrNull() is ApiException)
        val second=repo.lease(1);actor=2;repo.login("other@example.test","Fixture123!")
        assertTrue(runCatching{repo.requireLease(second)}.exceptionOrNull() is ApiException)
    }
    @Test fun pendingActorCanVerifyAndResendButCannotDispatchPrivateApiRequests()=runTest{
        var privateCalls=0;var resendCalls=0
        val repo=SessionRepository(FakeTransport{path,_->when(path){
            "auth/login"->reply(scope="verification",verified=false)
            "auth/resend"->{resendCalls++;JsonParser.parseString("""{"delivery_status":"accepted"}""")}
            "auth/verify-email"->reply()
            else->{privateCalls++;JsonParser.parseString("{}")}
        }},MemoryStore())
        repo.login("reader@example.test","Fixture123!")
        assertTrue(runCatching{repo.request("PUT","me/favorites",expectedUser=1)}.exceptionOrNull() is ApiException)
        assertEquals(0,privateCalls);assertTrue(repo.state.value is AuthState.Pending)
        assertEquals("accepted",repo.resend(1));assertEquals(1,resendCalls)
        repo.verify("123456",1);assertTrue(repo.state.value is AuthState.SignedIn)
        repo.request("GET","home",expectedUser=1);assertEquals(1,privateCalls)
    }
    @Test fun sameActorPendingCredentialsDoNotAuthorizePrivateMutation(){
        runTest{
            var pending=false
            val repo=SessionRepository(FakeTransport{_,_->reply(scope=if(pending)"verification" else"full",verified=!pending)},MemoryStore())
            repo.login("reader@example.test","Fixture123!")
            assertEquals(1L,repo.requireVerifiedOwner(1))
            pending=true;repo.login("reader@example.test","Fixture123!")
            assertTrue(repo.state.value is AuthState.Pending)
            assertTrue(runCatching{repo.requireVerifiedOwner(1)}.exceptionOrNull() is ApiException)
            assertEquals(1L,repo.userId);assertTrue(repo.state.value is AuthState.Pending)
        }
    }
    @Test fun staleAccountActionsAreRejectedBeforeTransportOrCredentialClearing()=runTest {
        var actor=1;var writes=0
        val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply(id=actor)else{writes++;JsonParser.parseString("{}")}},store)
        repo.login("reader@example.test","Fixture123!")
        actor=2;repo.login("other@example.test","Fixture123!")
        val before=store.value
        val results=listOf(
            runCatching{repo.updateProfile("Fixture","fixture",1)},
            runCatching{repo.changePassword("old","new password","new password",1)},
            runCatching{repo.delete("fixture password",1)},
            runCatching{repo.logout(1)},
            runCatching{repo.revokeOtherSessions(1)},
            runCatching{repo.verify("123456",1)},
            runCatching{repo.resend(1)},
            runCatching{repo.request("DELETE","me/favorites",expectedUser=1)}
        )
        assertTrue(results.all{it.exceptionOrNull() is ApiException})
        assertEquals(0,writes);assertEquals(before,store.value);assertEquals(2L,repo.userId)
    }
    @Test fun queuedPrivateWritesCannotUseAnotherAccount()=runTest {
        var writes=0
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply()else{writes++;JsonParser.parseString("{}")}},MemoryStore())
        repo.login("reader@example.test","Fixture123!")
        assertTrue(runCatching{repo.request("PUT","me/lesson-progress",expectedUser=2)}.isFailure)
        assertEquals(0,writes);assertTrue(repo.state.value is AuthState.SignedIn)
        repo.request("PUT","me/lesson-progress",expectedUser=1);assertEquals(1,writes)
    }
    @Test fun revocationKeepsCurrentCredentialsAndRejectsMalformedReceipts()=runTest {
        var response="{\"revoked_count\":2}";val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply()else JsonParser.parseString(response)},store)
        repo.login("reader@example.test","Fixture123!");val before=store.value
        assertEquals(2,repo.revokeOtherSessions(1));assertEquals(before,store.value)
        for(invalid in listOf("{}","{\"revoked_count\":-1}","{\"revoked_count\":true}","{\"revoked_count\":\"2\"}","{\"revoked_count\":1.5}","{\"revoked_count\":2147483648}")){
            response=invalid;assertTrue(runCatching{repo.revokeOtherSessions(1)}.isFailure);assertEquals(before,store.value)
        }
    }
    @Test fun lateRevocationCannotChangeANewAccountsCredentials()=runTest {
        var actor=1;val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login")reply(id=actor)else{started.complete(Unit);finish.await();JsonParser.parseString("{\"revoked_count\":1}")}},store)
        repo.login("reader@example.test","Fixture123!")
        val pending=async{runCatching{repo.revokeOtherSessions(1)}};started.await()
        actor=2;repo.login("other@example.test","Fixture123!");finish.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is ApiException);assertEquals(2L,store.value!!.user.id)
    }
    @Test fun otpIsNormalizedAtTransportBoundaryAndInvalidLengthNeverDispatches()=runTest {
        val codes=mutableListOf<String>();val transport=object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                if(path=="auth/verify-email"||path=="auth/reset-password")codes.add(body["code"] as String)
                return reply()
            }
        }
        val repo=SessionRepository(transport,MemoryStore());repo.login("reader@example.test","Fixture123!")
        repo.verify("٠١۲٣۴٥",1);repo.reset("reader@example.test","۰١۲٣٤۵","NewFixture123!","NewFixture123!")
        assertEquals(listOf("012345","012345"),codes)
        assertTrue(runCatching{repo.verify("12",1)}.isFailure)
        assertTrue(runCatching{repo.reset("reader@example.test","12","NewFixture123!","NewFixture123!")}.isFailure)
        assertEquals(2,codes.size)
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
    private fun reply(token: String = "a", scope: String = "full", verified: Boolean = true,id:Int=1): JsonElement = JsonParser.parseString("""{"user":{"id":$id,"email":"reader@example.test","display_name":"Reader","username":"reader","role":"user","email_verified":$verified},"session":{"id":"s","access_token":"$token","refresh_token":"refresh-$token","expires_in":900,"scope":"$scope"}}""")
    @Test fun formerAccountResponseCannotCompleteAfterAnotherSignIn()=runTest {
        var actor=1;val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->
            if(path=="auth/login")reply(id=actor)else{started.complete(Unit);finish.await();JsonParser.parseString("{\"private\":\"former account\"}")}
        },store)
        repo.login("reader@example.test","Fixture123!")
        val pending=async{runCatching{repo.request("GET","me",expectedUser=1)}}
        started.await();actor=2;repo.login("other@example.test","Fixture123!");finish.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is ApiException);assertEquals(2L,store.value!!.user.id)
    }
    @Test fun lateRefreshSuccessOrRevocationCannotReplaceOrSignOutANewAccount()=runTest {
        for(revoked in listOf(false,true)){
            var actor=1;var now=0L;val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val store=MemoryStore()
            val repo=SessionRepository(FakeTransport{path,_->when(path){
                "auth/login"->reply(token="actor-$actor",id=actor)
                "auth/refresh"->{started.complete(Unit);finish.await();if(revoked)throw ApiException(401,"unauthenticated","");reply("old-refresh",id=1)}
                else->JsonParser.parseString("{}")
            }},store,now={now})
            repo.login("reader@example.test","Fixture123!");now=901_000
            val pending=async{runCatching{repo.request("GET","home",expectedUser=1)}}
            started.await();actor=2;repo.login("other@example.test","Fixture123!");finish.complete(Unit)
            assertTrue(pending.await().isFailure)
            assertEquals(2L,store.value!!.user.id);assertEquals("actor-2",store.value!!.credentials.accessToken)
            assertTrue(repo.state.value is AuthState.SignedIn)
        }
    }
    @Test fun failedSignInDoesNotEraseTheExistingSession()=runTest {
        var reject=false;val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->if(path=="auth/login"){if(reject)throw ApiException(422,"invalid_credentials","");reply()}else JsonParser.parseString("{}")},store)
        repo.login("reader@example.test","Fixture123!");reject=true
        assertTrue(runCatching{repo.login("other@example.test","wrong fixture")}.isFailure)
        repo.request("GET","home",expectedUser=1)
        assertEquals(1L,store.value!!.user.id);assertTrue(repo.state.value is AuthState.SignedIn)
    }
    @Test fun requestsStartedDuringSignInCannotReturnFormerAccountData()=runTest {
        var actor=1;val authStarted=CompletableDeferred<Unit>();val authFinish=CompletableDeferred<Unit>()
        val requestStarted=CompletableDeferred<Unit>();val requestFinish=CompletableDeferred<Unit>();val store=MemoryStore()
        val repo=SessionRepository(FakeTransport{path,_->
            if(path=="auth/login"){
                if(actor==2){authStarted.complete(Unit);authFinish.await()}
                reply(id=actor)
            }else{requestStarted.complete(Unit);requestFinish.await();JsonParser.parseString("{}")}
        },store)
        repo.login("reader@example.test","Fixture123!");actor=2
        val signIn=async{repo.login("other@example.test","Fixture123!")};authStarted.await()
        val pending=async{runCatching{repo.request("GET","me",expectedUser=1)}};requestStarted.await()
        authFinish.complete(Unit);signIn.await();requestFinish.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is ApiException);assertEquals(2L,store.value!!.user.id)
    }
    @Test fun slowCredentialPersistenceCannotInvalidateALaterAuthenticationIntent()=runTest {
        var actor=1;val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val store=object:TokenStore{
            var value:StoredSession?=null
            override suspend fun load()=value
            override suspend fun save(session:StoredSession){if(session.user.id==1L){started.complete(Unit);finish.await()};value=session}
            override suspend fun clear(){value=null}
        }
        val repo=SessionRepository(FakeTransport{_,_->reply(id=actor)},store)
        val first=async{runCatching{repo.login("reader@example.test","Fixture123!")}};started.await()
        actor=2;val later=async{runCatching{repo.login("other@example.test","Fixture123!")}};yield()
        finish.complete(Unit);first.await();assertTrue(later.await().isSuccess)
        assertEquals(2L,store.value!!.user.id)
    }
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
