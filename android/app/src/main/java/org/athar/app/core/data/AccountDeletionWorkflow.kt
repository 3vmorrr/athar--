package org.athar.app.core.data

import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.core.network.ApiException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** These tags distinguish a server rejection from a lost response or a client ownership fence. */
internal class DeletionRejected(val failure: ApiException) : IOException("deletion_rejected")
internal class DeletionNotDispatched(val failure: Exception) : IOException("deletion_not_dispatched")
internal class DeletionUnconfirmed : IOException("account_deletion_pending")
internal enum class DeletionRecoveryResult { COMPLETE, RETRY, REAUTH_REQUIRED }

internal interface AccountDeletionActions {
    suspend fun current(owner: Long): AccountDeletionIntent?
    suspend fun prepare(owner: Long): AccountDeletionIntent
    suspend fun transition(value: AccountDeletionIntent, next: DeletionPhase): AccountDeletionIntent
    suspend fun pause(value: AccountDeletionIntent)
    suspend fun undoRejected(value: AccountDeletionIntent)
    suspend fun deleteRemote(value: AccountDeletionIntent, password: String)
    suspend fun proof(value: AccountDeletionIntent): JsonElement
    suspend fun purgeOwned(value: AccountDeletionIntent)
    suspend fun forgetCredentials(owner: Long)
    suspend fun acknowledge(value: AccountDeletionIntent)
    suspend fun forgetCapability(value: AccountDeletionIntent)
    suspend fun schedule(owner: Long)
}

/** Passwords are never persisted. Missing proof is uncertainty, never authority to purge or resume. */
internal class AccountDeletionWorkflow(private val actions: AccountDeletionActions) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private fun lock(owner: Long): Mutex { require(owner > 0); return locks.computeIfAbsent(owner) { Mutex() } }
    private suspend fun confirmed(value: AccountDeletionIntent): AccountDeletionIntent? = try {
        if (DeletionReceiptPayload.confirms(value, actions.proof(value))) actions.transition(value, DeletionPhase.CONFIRMED) else null
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    suspend fun start(owner: Long, password: String): DeletionRecoveryResult = lock(owner).withLock {
        require(password.isNotEmpty())
        var value = actions.current(owner) ?: actions.prepare(owner)
        require(value.owner == owner)
        actions.schedule(owner)
        if (value.phase == DeletionPhase.REJECTED) { actions.undoRejected(value); value = actions.prepare(owner) }
        if (value.phase in setOf(DeletionPhase.CONFIRMED, DeletionPhase.CLEANED)) return@withLock finish(value)
        val fresh = value.phase == DeletionPhase.PREPARED
        actions.pause(value)
        if (!fresh) confirmed(value)?.let { return@withLock finish(it) }
        if (fresh) value = actions.transition(value, DeletionPhase.PENDING)
        try {
            actions.deleteRemote(value, password)
            value = actions.transition(value, DeletionPhase.CONFIRMED)
        } catch (cancelled: CancellationException) { actions.schedule(owner); throw cancelled }
        catch (failure: Exception) {
            // Only the first unambiguous attempt can be safely undone. A later rejection
            // cannot prove that an earlier interrupted request will never commit.
            val rejected = when (failure) { is DeletionRejected -> failure.failure; is DeletionNotDispatched -> failure.failure; else -> null }
            if (fresh && rejected != null) {
                val record = actions.transition(value, DeletionPhase.REJECTED)
                actions.undoRejected(record)
                throw rejected
            }
            confirmed(value)?.let { return@withLock finish(it) }
            actions.schedule(owner)
            if (rejected != null) throw rejected
            throw DeletionUnconfirmed()
        }
        finish(value)
    }

    suspend fun recover(owner: Long): DeletionRecoveryResult = lock(owner).withLock {
        val value = actions.current(owner) ?: return@withLock DeletionRecoveryResult.COMPLETE
        require(value.owner == owner)
        when (value.phase) {
            DeletionPhase.REJECTED -> { actions.undoRejected(value); DeletionRecoveryResult.COMPLETE }
            DeletionPhase.PREPARED -> { actions.pause(value); DeletionRecoveryResult.REAUTH_REQUIRED }
            DeletionPhase.PENDING -> {
                actions.pause(value)
                val proof = confirmed(value)
                if (proof == null) DeletionRecoveryResult.RETRY else finish(proof)
            }
            DeletionPhase.CONFIRMED, DeletionPhase.CLEANED -> finish(value)
        }
    }

    private suspend fun finish(initial: AccountDeletionIntent): DeletionRecoveryResult {
        var value = initial
        if (value.phase == DeletionPhase.CONFIRMED) {
            try {
                actions.purgeOwned(value)
                actions.forgetCredentials(value.owner)
                value = actions.transition(value, DeletionPhase.CLEANED)
            } catch (failure: Exception) { actions.schedule(value.owner); throw failure }
        }
        require(value.phase == DeletionPhase.CLEANED)
        return try {
            actions.acknowledge(value); actions.forgetCapability(value)
            DeletionRecoveryResult.COMPLETE
        } catch (cancelled: CancellationException) { actions.schedule(value.owner); throw cancelled }
        catch (_: Exception) { actions.schedule(value.owner); DeletionRecoveryResult.RETRY }
    }
}
