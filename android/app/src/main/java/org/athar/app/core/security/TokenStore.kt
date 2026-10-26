package org.athar.app.core.security

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UserProfile(val id: Long, val email: String, @SerializedName("display_name") val displayName: String?, val username: String?, val role: String, @SerializedName("email_verified") val emailVerified: Boolean)
data class Credentials(val id: String, @SerializedName("access_token") val accessToken: String, @SerializedName("refresh_token") val refreshToken: String, @SerializedName("expires_in") val expiresIn: Long, val scope: String)
data class StoredSession(val user: UserProfile, val credentials: Credentials, val expiresAt: Long)
interface TokenStore { suspend fun load(): StoredSession?; suspend fun save(session: StoredSession); suspend fun clear() }

class KeystoreTokenStore(context: Context, private val cipher: PrivateCipher, private val gson: Gson = Gson(), namespace: String = "default") : TokenStore {
    init { require(namespace == "default" || namespace.matches(Regex("[a-f0-9]{12}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "session-$namespace.enc"))
    override suspend fun load(): StoredSession? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        runCatching { gson.fromJson(cipher.open(file.openRead().bufferedReader().use { it.readText() }), StoredSession::class.java) }.getOrElse {
            file.delete(); null
        }
    }
    override suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        val output = file.startWrite()
        try { output.write(cipher.seal(gson.toJson(session)).toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
