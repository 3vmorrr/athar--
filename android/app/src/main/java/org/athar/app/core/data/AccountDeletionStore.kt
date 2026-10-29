package org.athar.app.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.core.security.PrivateCipher

internal class AccountDeletionUnreadable : java.io.IOException("local_data_unreadable")

/** Ciphertext binds every recovery phase to its API namespace, owner and exact request. */
internal class AccountDeletionStore(private val namespace: String, private val database: AppDatabase, private val cipher: PrivateCipher) {
    private val mutations = Mutex()
    private val initialization = Mutex()
    private var initialized = false
    private val blocked = MutableStateFlow<Set<Long>>(emptySet())
    val blockedOwners = blocked.asStateFlow()
    fun isBlocked(owner: Long) = owner in blocked.value

    suspend fun initialize() = initialization.withLock {
        if (!initialized) {
            refresh()
            initialized = true
        }
    }
    private suspend fun refresh() { blocked.value = database.dao().allDeletions().map { it.userId }.toSet() }
    private fun decode(row: LocalAccountDeletion): AccountDeletionIntent? {
        try {
        require(row.userId > 0)
        val payload = row.payloadCipher
        if (payload == null) { require(row.state == DeletionPhase.CLEANED.wire); return null }
        return AccountDeletionIntent.decode(namespace, row, cipher.open(payload))
        } catch (_: Exception) { throw AccountDeletionUnreadable() }
    }
    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        initialize()
        mutations.withLock { try { block() } finally { refresh() } }
    }
    suspend fun intent(owner: Long): AccountDeletionIntent? = serialized { database.dao().deletion(owner)?.let(::decode) }
    suspend fun pendingOwners(): List<Long> = serialized { database.dao().allDeletions().filter { it.payloadCipher != null }.map { it.userId } }
    suspend fun prepare(owner: Long, authorize: () -> Unit): AccountDeletionIntent = serialized {
        database.withTransaction {
            authorize()
            val existing = database.dao().deletion(owner)
            if (existing != null) requireNotNull(decode(existing))
            else AccountDeletionIntent.prepare(namespace, owner).also { put(it) }
        }
    }
    private suspend fun put(value: AccountDeletionIntent) {
        require(value.namespace == namespace)
        database.dao().deletion(LocalAccountDeletion(value.owner, value.requestId, value.phase.wire, cipher.seal(value.encode()), System.currentTimeMillis()))
    }
    suspend fun transition(value: AccountDeletionIntent, next: DeletionPhase): AccountDeletionIntent = serialized {
        database.withTransaction {
            val current = requireNotNull(database.dao().deletion(value.owner)).let(::decode)
            require(current == value)
            val allowed = when (value.phase) {
                DeletionPhase.PREPARED -> setOf(DeletionPhase.PENDING, DeletionPhase.REJECTED)
                DeletionPhase.PENDING -> setOf(DeletionPhase.CONFIRMED, DeletionPhase.REJECTED)
                DeletionPhase.REJECTED -> emptySet()
                DeletionPhase.CONFIRMED -> setOf(DeletionPhase.CLEANED)
                DeletionPhase.CLEANED -> emptySet()
            }
            require(next in allowed)
            value.copy(phase = next).also { put(it) }
        }
    }
    /** Only a locally observed, definitive first-attempt rejection may reach REJECTED. */
    suspend fun removeRejected(value: AccountDeletionIntent) = serialized {
        database.withTransaction {
            require(value.phase == DeletionPhase.REJECTED)
            require(database.dao().deletion(value.owner)?.let(::decode) == value)
            database.dao().abortDeletion(value.owner, value.requestId)
        }
    }
    suspend fun forgetCapability(value: AccountDeletionIntent) = serialized {
        database.withTransaction {
            require(value.phase == DeletionPhase.CLEANED)
            val row = requireNotNull(database.dao().deletion(value.owner)); require(decode(row) == value)
            database.dao().deletion(row.copy(payloadCipher = null, updatedAt = System.currentTimeMillis()))
        }
    }
}
