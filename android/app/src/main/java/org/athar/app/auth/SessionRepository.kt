package org.athar.app.auth

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.core.network.ApiException
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.core.data.AccountDeletionIntent
import org.athar.app.core.data.DeletionRejected
import org.athar.app.core.data.DeletionNotDispatched
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

sealed interface AuthState {
    data object Restoring : AuthState
    data object RestoreFailed : AuthState
    data object SignedOut : AuthState
    data class Pending(val user: UserProfile, val deliveryStatus: String? = null) : AuthState
    data class SignedIn(val user: UserProfile, val offline: Boolean = false) : AuthState
}
private data class AuthReply(val user: UserProfile?, val session: Credentials, @SerializedName("delivery_status") val deliveryStatus: String?)
internal data class SessionLease(val owner: Long, val generation: Long)

class SessionRepository(private val transport: ApiTransport, private val store: TokenStore, private val language: () -> String = { "ar" }, private val now: () -> Long = System::currentTimeMillis, private val ownerBlocked: (Long) -> Boolean = { false }, private val beforeRestore: suspend () -> Unit = {}) {
    private val gson = Gson()
    private val refreshMutex = Mutex()
    private val sessionMutex = Mutex()
    private val generation = AtomicLong()
    private val deletedOwners = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    @Volatile private var stored: StoredSession? = null
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state = mutableState.asStateFlow()
    val userId: Long? get() = stored?.user?.id

    internal fun lease(owner: Long): SessionLease {
        val epoch = generation.get()
        requireVerifiedOwner(owner); checkGeneration(epoch)
        return SessionLease(owner, epoch)
    }
    internal fun requireLease(lease: SessionLease) { requireVerifiedOwner(lease.owner); checkGeneration(lease.generation) }

    /** Private data actions need the captured actor and current verified access, including offline. */
    fun requireVerifiedOwner(expectedUser:Long?):Long {
        val owner=requireCredentialsOwner(expectedUser)
        if(ownerBlocked(owner))throw ApiException(409,"account_deletion_pending","")
        return owner
    }
    private fun requireCredentialsOwner(expectedUser:Long?):Long {
        if(expectedUser!=null&&expectedUser in deletedOwners)throw ApiException(401,"account_deleted","")
        val credentials=stored
        val signedIn=mutableState.value as? AuthState.SignedIn
        if(expectedUser==null||credentials?.user?.id!=expectedUser||signedIn?.user?.id!=expectedUser||
            credentials.credentials.scope!="full"||!credentials.user.emailVerified)throw ApiException(401,"session_changed","")
        return expectedUser
    }

    internal fun deletionLease(owner:Long):SessionLease {
        val epoch=generation.get();requireCredentialsOwner(owner);checkGeneration(epoch)
        return SessionLease(owner,epoch)
    }
    internal fun requireDeletionLease(lease:SessionLease){requireCredentialsOwner(lease.owner);checkGeneration(lease.generation)}

    suspend fun restore() {
        val epoch=generation.get()
        val saved=try {
            sessionMutex.withLock{checkGeneration(epoch);mutableState.value=AuthState.Restoring}
            beforeRestore()
            val loaded=store.load()
            if(loaded==null){clear(epoch);return}
            save(loaded,epoch);loaded
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){sessionMutex.withLock{if(generation.get()==epoch)mutableState.value=if(failure is ApiException&&failure.code=="account_deleted")AuthState.SignedOut else AuthState.RestoreFailed};return}
        if(saved.credentials.scope!="full"||!saved.user.emailVerified||ownerBlocked(saved.user.id))return
        try {
            val profile=gson.fromJson(request("GET","me"),UserProfile::class.java)
            if(profile.id!=saved.user.id)throw IOException("Invalid restored profile owner")
            val current=stored?:return
            save(current.copy(user=profile),epoch)
        }catch(failure:CancellationException){throw failure}
        catch(failure:ApiException){if(failure.status in listOf(401,403))clear(epoch)else offline(saved,epoch)}
        catch(failure:IOException){offline(saved,epoch)}
    }

    // Invalidate both former requests and requests started while a new sign-in was pending.
    private suspend fun beginAuthentication():Long=sessionMutex.withLock{generation.incrementAndGet()}
    suspend fun login(email: String, password: String) { val epoch = beginAuthentication(); accept(transport.request("POST", "auth/login", mapOf("email" to email.trim(), "password" to password), language = language()), epoch, true) }
    suspend fun register(name: String, username: String, email: String, password: String, confirmation: String) { val epoch = beginAuthentication(); accept(transport.request("POST", "auth/register", mapOf("display_name" to name.trim(), "username" to username.trim(), "email" to email.trim(), "password" to password, "password_confirmation" to confirmation), language = language()), epoch, true) }
    suspend fun verify(code: String,expectedUser:Long?=userId) { val epoch = generation.get(); accept(request("POST", "auth/verify-email", mapOf("code" to OtpInput.normalize(code).also { require(it.length == 6) }),expectedUser=requireNotNull(expectedUser)), epoch) }
    suspend fun resend(expectedUser:Long?=userId): String = request("POST", "auth/resend",expectedUser=requireNotNull(expectedUser)).asJsonObject.get("delivery_status").asString
    suspend fun forgot(email: String) { transport.request("POST", "auth/forgot-password", mapOf("email" to email.trim()), language = language()) }
    suspend fun reset(email: String, code: String, password: String, confirmation: String) { transport.request("POST", "auth/reset-password", mapOf("email" to email.trim(), "code" to OtpInput.normalize(code).also { require(it.length == 6) }, "password" to password, "password_confirmation" to confirmation), language = language()) }

    suspend fun request(method: String, path: String, body: Map<String, Any?> = emptyMap(), query: Map<String, String> = emptyMap(), expectedUser: Long? = null): JsonElement = requestCaptured(generation.get(), method, path, body, query, expectedUser)
    internal suspend fun requestLeased(lease: SessionLease, method: String, path: String, body: Map<String, Any?> = emptyMap()): JsonElement = requestCaptured(lease.generation, method, path, body, emptyMap(), lease.owner)
    private suspend fun requestCaptured(epoch: Long, method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, expectedUser: Long?): JsonElement {
        checkGeneration(epoch)
        var current = stored ?: throw ApiException(401, "unauthenticated", "")
        checkAccess(path,current,expectedUser)
        if (now() >= current.expiresAt - 30_000) { refresh(current.credentials.accessToken, epoch); current = stored ?: throw ApiException(401, "unauthenticated", "") }
        checkGeneration(epoch)
        checkAccess(path,current,expectedUser)
        return try { transport.request(method, path, body, query, current.credentials.accessToken, language()).also { checkGeneration(epoch);checkAccess(path,current,expectedUser) } }
        catch (failure: ApiException) {
            if (failure.status == 403 && failure.code in listOf("email_verification_required", "account_suspended")) clear(epoch)
            if (failure.status != 401) throw failure
            checkGeneration(epoch)
            refresh(current.credentials.accessToken, epoch)
            checkGeneration(epoch)
            val updated = stored ?: throw ApiException(401, "unauthenticated", "")
            checkAccess(path,updated,expectedUser)
            transport.request(method, path, body, query, updated.credentials.accessToken, language()).also { checkGeneration(epoch);checkAccess(path,updated,expectedUser) }
        }
    }
    private fun checkActor(session:StoredSession,expectedUser:Long?){if(expectedUser!=null&&session.user.id!=expectedUser)throw ApiException(401,"session_changed","")}
    private fun checkAccess(path:String,current:StoredSession,expectedUser:Long?){
        checkActor(current,expectedUser)
        // These are the only authenticated pending-account requests in the native API contract.
        if(path!="auth/verify-email"&&path!="auth/resend")requireVerifiedOwner(current.user.id)
    }

    private suspend fun refresh(previousAccess: String, expectedEpoch: Long? = null) = refreshMutex.withLock {
        val epoch = expectedEpoch ?: generation.get()
        checkGeneration(epoch)
        val current = stored ?: throw ApiException(401, "unauthenticated", "")
        if (current.credentials.accessToken != previousAccess) return@withLock
        try { accept(transport.request("POST", "auth/refresh", mapOf("refresh_token" to current.credentials.refreshToken), language = language()), epoch) }
        catch (failure: ApiException) { if (failure.status in listOf(401, 403)) clear(epoch); throw failure }
    }

    suspend fun updateProfile(name: String, username: String,expectedUser:Long?=userId) {
        requireVerifiedOwner(expectedUser)
        require(AccountInput.validProfile(name, username))
        val epoch = generation.get()
        val user = gson.fromJson(request("PATCH", "me", mapOf("display_name" to name.trim(), "username" to username.trim()),expectedUser=requireNotNull(expectedUser)), UserProfile::class.java)
        if(user.id!=expectedUser)throw IOException("Invalid profile owner")
        stored?.let { save(it.copy(user = user), epoch) }
    }
    suspend fun changePassword(current: String, password: String, confirmation: String,expectedUser:Long?=userId) { requireVerifiedOwner(expectedUser);require(current.isNotEmpty()&&AccountInput.validPassword(password)&&password==confirmation);val epoch = generation.get(); accept(request("PUT", "me/password", mapOf("current_password" to current, "password" to password, "password_confirmation" to confirmation),expectedUser=requireNotNull(expectedUser)), epoch) }
    suspend fun revokeOtherSessions(expectedUser:Long?=userId):Int {
        requireVerifiedOwner(expectedUser)
        val data=request("POST","me/sessions/revoke-others",expectedUser=requireNotNull(expectedUser))
        val count=if(data.isJsonObject)data.asJsonObject.get("revoked_count") else null
        if(count==null||!count.isJsonPrimitive||!count.asJsonPrimitive.isNumber||!count.toString().matches(Regex("0|[1-9][0-9]*")))throw IOException("Invalid session revocation response")
        return count.asString.toIntOrNull()?:throw IOException("Invalid session revocation response")
    }
    internal suspend fun submitDeletion(intent:AccountDeletionIntent,password:String) {
        val epoch=generation.get()
        val credentials=try {
            requireCredentialsOwner(intent.owner)
            var current=stored?:throw ApiException(401,"session_changed","")
            if(now()>=current.expiresAt-30_000){refresh(current.credentials.accessToken);current=stored?:throw ApiException(401,"session_changed","")}
            checkGeneration(epoch);requireCredentialsOwner(intent.owner);current
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){throw DeletionNotDispatched(failure)}
        val response=try {
            transport.request("DELETE","me",mapOf("password" to password,"request_id" to intent.requestId,"recovery_secret" to intent.secret),bearer=credentials.credentials.accessToken,language=language())
        }catch(cancelled:CancellationException){throw cancelled}
        catch(failure:ApiException){
            if(failure.status in listOf(401,403))clear(epoch)
            if(failure.status in 400..499)throw DeletionRejected(failure)
            throw failure
        }
        // Preserve a successful irreversible result for its captured owner even after A -> B.
        // The coordinator journals/purges only A and clears credentials only if they still belong to A.
        if(!response.isJsonObject||response.asJsonObject["status"]?.let{it.isJsonPrimitive&&it.asJsonPrimitive.isString&&it.asString=="deleted"}!=true)throw IOException("Invalid deletion response")
    }
    internal suspend fun deletionProof(intent:AccountDeletionIntent):JsonElement = transport.request("POST","auth/deletion-receipt",mapOf("request_id" to intent.requestId,"recovery_secret" to intent.secret),language=language())
    internal suspend fun acknowledgeDeletion(intent:AccountDeletionIntent) {
        val response=transport.request("DELETE","auth/deletion-receipt",mapOf("request_id" to intent.requestId,"recovery_secret" to intent.secret),language=language())
        if(!response.isJsonObject||response.asJsonObject["status"]?.asString!="forgotten")throw IOException("Invalid deletion acknowledgement")
    }
    internal suspend fun forgetDeletedOwner(owner:Long) {
        deletedOwners.add(owner)
        sessionMutex.withLock {
            val current=stored?:store.load()
            // Do not invalidate a newer sign-in intent for B. The owner blacklist also
            // prevents delayed restore/refresh responses for A from persisting again.
            if(current?.user?.id==owner){stored=null;mutableState.value=AuthState.SignedOut;store.clear()}
        }
    }
    suspend fun logout(expectedUser:Long?=userId) {
        if(expectedUser!=userId)throw ApiException(401,"session_changed","")
        val token = stored?.credentials?.accessToken
        clear(generation.get())
        try { if (token != null) transport.request("POST", "auth/logout", bearer = token, language = language()) }
        catch (failure: CancellationException) { throw failure }
        catch (_: IOException) { /* Local credentials are still removed when offline. */ }
    }

    private suspend fun accept(data: JsonElement, epoch: Long, rotateRequests:Boolean=false) {
        val reply = gson.fromJson(data, AuthReply::class.java)
        val user = reply.user ?: stored?.user ?: throw IOException("Invalid authentication response")
        if (reply.session.accessToken.isBlank() || reply.session.refreshToken.isBlank()) throw IOException("Invalid authentication response")
        val session = StoredSession(user, reply.session, now() + reply.session.expiresIn * 1000)
        save(session, epoch, reply.deliveryStatus,rotateRequests)
    }
    private fun checkGeneration(epoch: Long) { if (generation.get() != epoch) throw ApiException(401, "unauthenticated", "") }
    private suspend fun save(session: StoredSession, epoch: Long, delivery: String? = null,rotateRequests:Boolean=false) = sessionMutex.withLock {
        checkGeneration(epoch)
        if(session.user.id in deletedOwners)throw ApiException(401,"account_deleted","")
        store.save(session)
        if(rotateRequests)generation.incrementAndGet()
        stored = session; publish(session, delivery)
    }
    private suspend fun offline(session: StoredSession, epoch: Long) = sessionMutex.withLock {
        if (generation.get() == epoch && stored != null) mutableState.value = AuthState.SignedIn(session.user, offline = true)
    }
    private fun publish(session: StoredSession, delivery: String? = null) {
        mutableState.value = if (session.credentials.scope == "full" && session.user.emailVerified) AuthState.SignedIn(session.user) else AuthState.Pending(session.user, delivery)
    }
    private suspend fun clear(epoch: Long) = sessionMutex.withLock {
        if (generation.get() == epoch) { generation.incrementAndGet(); stored = null; mutableState.value = AuthState.SignedOut; store.clear() }
    }
}
