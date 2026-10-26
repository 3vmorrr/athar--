package org.athar.app.notifications

import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.security.PrivateCipher

@Keep
data class DevicePushTokenState(
    val provider: String = "fcm_v1",
    val token: String,
    val updatedAtMillis: Long,
) {
    fun validated(): DevicePushTokenState {
        require(provider == "fcm_v1")
        require(token.matches(Regex("[A-Za-z0-9:_-]{16,4096}")))
        require(updatedAtMillis > 0)
        return this
    }
}

class PushDeviceTokenStore(
    private val dao: AtharDao,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val gson=Gson()
    private val key="private:push-device-token:v1"

    suspend fun read(owner: Long): ProtectedRead<DevicePushTokenState?> {
        session.requireVerifiedOwner(owner)
        val row=dao.cache(owner,key) ?: return ProtectedRead.Ready(null)
        return try {
            val value=requireNotNull(gson.fromJson(cipher.open(row.payloadCipher),DevicePushTokenState::class.java)).validated()
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(value)
        } catch (failure:CancellationException) {
            throw failure
        } catch (_:Exception) {
            ProtectedRead.Unreadable
        }
    }

    suspend fun save(owner: Long, token: String): DevicePushTokenState {
        session.requireVerifiedOwner(owner)
        val value=DevicePushTokenState(token=token,updatedAtMillis=now()).validated()
        dao.cache(CachedDocument(owner,key,cipher.seal(gson.toJson(value)),now()))
        session.requireVerifiedOwner(owner)
        return value
    }

    suspend fun clear(owner: Long) {
        dao.removeCache(owner,key)
    }
}
