package org.athar.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.athar.app.core.data.AppSettings
import org.athar.app.core.network.ApiException
import org.athar.app.auth.AccountInput
import org.athar.app.auth.OtpInput

data class OperationState(val busy: Boolean = false, val error: String? = null, val message: String? = null)
class AppViewModel(val graph: AppGraph) : ViewModel() {
    val auth = graph.session.state
    val settings: StateFlow<AppSettings?> = graph.settings.settings.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableOperation = MutableStateFlow(OperationState())
    val operation = mutableOperation.asStateFlow()
    private var restoring:kotlinx.coroutines.Job?=null
    init { restoreSession() }
    fun restoreSession(){if(restoring?.isActive==true)return;restoring=viewModelScope.launch{graph.session.restore()}}
    fun completeOnboarding() { viewModelScope.launch { graph.settings.completeOnboarding() } }
    fun clearFeedback() { if (!mutableOperation.value.busy) mutableOperation.value = OperationState() }
    fun invalid(code: String) { if (!mutableOperation.value.busy) mutableOperation.value = OperationState(error = code) }
    fun login(email: String, password: String) = perform { graph.session.login(email, password) }
    fun register(
        name: String,
        username: String,
        email: String,
        password: String,
        confirmation: String,
    ) {
        if (password != confirmation) {
            invalid("password_mismatch")
            return
        }
        if (
            !AccountInput.validProfile(name, username) ||
            !AccountInput.validEmail(email) ||
            !AccountInput.validPassword(password)
        ) {
            invalid("invalid_input")
            return
        }
        perform {
            graph.session.register(
                name.trim(),
                AccountInput.normalizeUsernameInput(username).trim(),
                email.trim(),
                password,
                confirmation,
            )
        }
    }
    fun verify(
        code: String,
        expectedUser: Long? = graph.session.userId,
    ) {
        val normalized = OtpInput.normalize(code)
        if (normalized.length != 6) {
            invalid("invalid_input")
            return
        }
        perform { graph.session.verify(normalized, expectedUser) }
    }
    fun resend(expectedUser:Long?=graph.session.userId) = perform { mutableOperation.value = OperationState(message = if (graph.session.resend(expectedUser) == "accepted") "mail_accepted" else "mail_unavailable") }
    fun forgot(email: String) {
        if (!AccountInput.validEmail(email)) {
            invalid("invalid_input")
            return
        }
        perform {
            graph.session.forgot(email.trim())
            mutableOperation.value = OperationState(message = "reset_sent")
        }
    }
    fun reset(
        email: String,
        code: String,
        password: String,
        confirmation: String,
    ) {
        if (password != confirmation) {
            invalid("password_mismatch")
            return
        }
        val normalizedCode = OtpInput.normalize(code)
        if (
            !AccountInput.validEmail(email) ||
            normalizedCode.length != 6 ||
            !AccountInput.validPassword(password)
        ) {
            invalid("invalid_input")
            return
        }
        perform {
            graph.session.reset(
                email.trim(),
                normalizedCode,
                password,
                confirmation,
            )
            mutableOperation.value = OperationState(message = "password_updated")
        }
    }
    fun logout(expectedUser:Long?=graph.session.userId) = perform { graph.session.logout(expectedUser) }
    fun deleteAccount(password:String,owner:Long)=perform(successMessage="account_deleted"){graph.accountDeletion.delete(owner,password)}
    fun checkDeletion(owner:Long)=perform{graph.accountDeletion.recover(owner)}
    fun perform(successMessage: String? = null, block: suspend () -> Unit) {
        if (mutableOperation.value.busy) return
        viewModelScope.launch {
            mutableOperation.value = OperationState(busy = true)
            try { block(); if (mutableOperation.value.busy) mutableOperation.value = OperationState(message = successMessage) }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { mutableOperation.value = OperationState(error = when(failure){
                is ApiException->failure.code.ifBlank{"network_error"}
                is org.athar.app.core.data.AccountDeletionUnreadable->"local_data_unreadable"
                is org.athar.app.core.data.DeletionUnconfirmed->"account_deletion_pending"
                else->"network_error"
            }) }
        }
    }
}
