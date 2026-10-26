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

    suspend fun login(email: String, password: String) { val epoch = generation.get(); accept(transport.request("POST", "auth/login", mapOf("email" to email.trim(), "password" to password), language = language()), epoch) }
    suspend fun register(name: String, username: String, email: String, password: String, confirmation: String) { val epoch = generation.get(); accept(transport.request("POST", "auth/register", mapOf("display_name" to name.trim(), "username" to username.trim(), "email" to email.trim(), "password" to password, "password_confirmation" to confirmation), language = language()), epoch) }
    suspend fun verify(code: String) { val epoch = generation.get(); accept(request("POST", "auth/verify-email", mapOf("code" to code)), epoch) }
    suspend fun resend(): String = request("POST", "auth/resend").asJsonObject.get("delivery_status").asString
    suspend fun forgot(email: String) { transport.request("POST", "auth/forgot-password", mapOf("email" to email.trim()), language = language()) }
    suspend fun reset(email: String, code: String, password: String, confirmation: String) { transport.request("POST", "auth/reset-password", mapOf("email" to email.trim(), "code" to code, "password" to password, "password_confirmation" to confirmation), language = language()) }

    suspend fun request(method: String, path: String, body: Map<String, Any?> = emptyMap(), query: Map<String, String> = emptyMap(), expectedUser: Long? = null): JsonElement {
        val epoch = generation.get()
        var current = stored ?: throw ApiException(401, "unauthenticated", "")
        checkActor(current,expectedUser)
        if (now() >= current.expiresAt - 30_000) { refresh(current.credentials.accessToken); current = stored ?: throw ApiException(401, "unauthenticated", "") }
        checkGeneration(epoch)
        checkActor(current,expectedUser)
        return try { transport.request(method, path, body, query, current.credentials.accessToken, language()).also { checkGeneration(epoch) } }
        catch (failure: ApiException) {
            if (failure.status == 403 && failure.code in listOf("email_verification_required", "account_suspended")) clear(epoch)
            if (failure.status != 401) throw failure
            checkGeneration(epoch)
            refresh(current.credentials.accessToken)
            checkGeneration(epoch)
            val updated = stored ?: throw ApiException(401, "unauthenticated", "")
            checkActor(updated,expectedUser)
            transport.request(method, path, body, query, updated.credentials.accessToken, language()).also { checkGeneration(epoch) }
        }
    }
    private fun checkActor(session:StoredSession,expectedUser:Long?){if(expectedUser!=null&&session.user.id!=expectedUser)throw ApiException(401,"session_changed","")}

    private suspend fun refresh(previousAccess: String) = refreshMutex.withLock {
        val epoch = generation.get()
        val current = stored ?: throw ApiException(401, "unauthenticated", "")
        if (current.credentials.accessToken != previousAccess) return@withLock
        try { accept(transport.request("POST", "auth/refresh", mapOf("refresh_token" to current.credentials.refreshToken), language = language()), epoch) }
        catch (failure: ApiException) { if (failure.status in listOf(401, 403)) clear(epoch); throw failure }
    }

    suspend fun updateProfile(name: String, username: String) {
        val epoch = generation.get()
        val user = gson.fromJson(request("PATCH", "me", mapOf("display_name" to name, "username" to username)), UserProfile::class.java)
        stored?.let { save(it.copy(user = user), epoch) }
    }
    suspend fun changePassword(current: String, password: String, confirmation: String) { val epoch = generation.get(); accept(request("PUT", "me/password", mapOf("current_password" to current, "password" to password, "password_confirmation" to confirmation)), epoch) }
    suspend fun delete(password: String) { val epoch = generation.get(); request("DELETE", "me", mapOf("password" to password)); clear(epoch) }
    suspend fun logout() {
        val token = stored?.credentials?.accessToken
        clear(generation.get())
        try { if (token != null) transport.request("POST", "auth/logout", bearer = token, language = language()) }
        catch (failure: CancellationException) { throw failure }
        catch (_: IOException) { /* Local credentials are still removed when offline. */ }
    }

    private suspend fun accept(data: JsonElement, epoch: Long) {
        val reply = gson.fromJson(data, AuthReply::class.java)
        val user = reply.user ?: stored?.user ?: throw IOException("Invalid authentication response")
        if (reply.session.accessToken.isBlank() || reply.session.refreshToken.isBlank()) throw IOException("Invalid authentication response")
        val session = StoredSession(user, reply.session, now() + reply.session.expiresIn * 1000)
        save(session, epoch, reply.deliveryStatus)
    }
    private fun checkGeneration(epoch: Long) { if (generation.get() != epoch) throw ApiException(401, "unauthenticated", "") }
    private suspend fun save(session: StoredSession, epoch: Long, delivery: String? = null) = sessionMutex.withLock {
        checkGeneration(epoch); store.save(session); stored = session; publish(session, delivery)
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
