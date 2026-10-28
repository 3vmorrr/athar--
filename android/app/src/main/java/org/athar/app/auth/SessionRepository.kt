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
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class Pending(val user: UserProfile, val deliveryStatus: String? = null) : AuthState
    data class SignedIn(val user: UserProfile, val offline: Boolean = false) : AuthState
}
private data class AuthReply(val user: UserProfile?, val session: Credentials, @SerializedName("delivery_status") val deliveryStatus: String?)

class SessionRepository(private val transport: ApiTransport, private val store: TokenStore, private val language: () -> String = { "ar" }, private val now: () -> Long = System::currentTimeMillis) {
    private val gson = Gson()
    private val refreshMutex = Mutex()
    private val sessionMutex = Mutex()
    private val generation = AtomicLong()
    @Volatile private var stored: StoredSession? = null
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val state = mutableState.asStateFlow()
    val userId: Long? get() = stored?.user?.id

    /** Private data actions need the captured actor and current verified access, including offline. */
    fun requireVerifiedOwner(expectedUser:Long?):Long {
        val credentials=stored
        val signedIn=mutableState.value as? AuthState.SignedIn
        if(expectedUser==null||credentials?.user?.id!=expectedUser||signedIn?.user?.id!=expectedUser||
            credentials.credentials.scope!="full"||!credentials.user.emailVerified)throw ApiException(401,"session_changed","")
        return expectedUser
    }

    suspend fun restore() {
        val epoch = generation.get()
        val saved = store.load()
        if (saved == null) { clear(epoch); return }
        save(saved, epoch)
        if (saved.credentials.scope != "full" || !saved.user.emailVerified) return
        try {
            val profile = gson.fromJson(request("GET", "me"), UserProfile::class.java)
            val current = stored ?: return
            save(current.copy(user = profile), epoch)
        } catch (failure: CancellationException) { throw failure }
        catch (failure: ApiException) { if (failure.status in listOf(401, 403)) clear(epoch) else offline(saved, epoch) }
        catch (failure: IOException) { offline(saved, epoch) }
    }

    // Invalidate both former requests and requests started while a new sign-in was pending.
    private suspend fun beginAuthentication():Long=sessionMutex.withLock{generation.incrementAndGet()}
    suspend fun login(email: String, password: String) { val epoch = beginAuthentication(); accept(transport.request("POST", "auth/login", mapOf("email" to email.trim(), "password" to password), language = language()), epoch, true) }
    suspend fun register(name: String, username: String, email: String, password: String, confirmation: String) { val epoch = beginAuthentication(); accept(transport.request("POST", "auth/register", mapOf("display_name" to name.trim(), "username" to username.trim(), "email" to email.trim(), "password" to password, "password_confirmation" to confirmation), language = language()), epoch, true) }
    suspend fun verify(code: String,expectedUser:Long?=userId) { val epoch = generation.get(); accept(request("POST", "auth/verify-email", mapOf("code" to OtpInput.normalize(code).also { require(it.length == 6) }),expectedUser=requireNotNull(expectedUser)), epoch) }
    suspend fun resend(expectedUser:Long?=userId): String = request("POST", "auth/resend",expectedUser=requireNotNull(expectedUser)).asJsonObject.get("delivery_status").asString
    suspend fun forgot(email: String) { transport.request("POST", "auth/forgot-password", mapOf("email" to email.trim()), language = language()) }
    suspend fun reset(email: String, code: String, password: String, confirmation: String) { transport.request("POST", "auth/reset-password", mapOf("email" to email.trim(), "code" to OtpInput.normalize(code).also { require(it.length == 6) }, "password" to password, "password_confirmation" to confirmation), language = language()) }

    suspend fun request(method: String, path: String, body: Map<String, Any?> = emptyMap(), query: Map<String, String> = emptyMap(), expectedUser: Long? = null): JsonElement {
        val epoch = generation.get()
        var current = stored ?: throw ApiException(401, "unauthenticated", "")
        checkAccess(path,current,expectedUser)
        if (now() >= current.expiresAt - 30_000) { refresh(current.credentials.accessToken); current = stored ?: throw ApiException(401, "unauthenticated", "") }
        checkGeneration(epoch)
        checkAccess(path,current,expectedUser)
        return try { transport.request(method, path, body, query, current.credentials.accessToken, language()).also { checkGeneration(epoch);checkAccess(path,current,expectedUser) } }
        catch (failure: ApiException) {
            if (failure.status == 403 && failure.code in listOf("email_verification_required", "account_suspended")) clear(epoch)
            if (failure.status != 401) throw failure
            checkGeneration(epoch)
            refresh(current.credentials.accessToken)
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

    private suspend fun refresh(previousAccess: String) = refreshMutex.withLock {
        val epoch = generation.get()
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
    suspend fun delete(password: String,expectedUser:Long?=userId) { requireVerifiedOwner(expectedUser);val epoch = generation.get(); request("DELETE", "me", mapOf("password" to password),expectedUser=requireNotNull(expectedUser)); clear(epoch) }
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
        checkGeneration(epoch); store.save(session)
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
