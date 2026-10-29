package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.athar.app.AppViewModel
import org.athar.app.OperationState
import org.athar.app.R
import org.athar.app.auth.Feedback

@Composable internal fun AccountDeletionRecoveryScreen(vm: AppViewModel, owner: Long) {
    val statuses by vm.graph.accountDeletion.status.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var password by remember(owner) { mutableStateOf("") }
    val status = statuses[owner]
    LaunchedEffect(owner) { vm.checkDeletion(owner) }
    AccountDeletionContent(status?.busy == true || operation.busy, status?.error ?: operation.error, password, { password = it },
        onCheck = { vm.checkDeletion(owner) },
        onRetry = { val submitted = password; password = ""; vm.deleteAccount(submitted, owner) },
        onLogout = { password = ""; vm.logout(owner) })
}

@Composable private fun AccountDeletionContent(busy: Boolean, error: String?, password: String, onPassword: (String) -> Unit, onCheck: () -> Unit, onRetry: () -> Unit, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AtharScreenIdentity(stringResource(R.string.delete_account), AtharIcons.Lock)
            Text(stringResource(R.string.account_deletion_pending))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Feedback(OperationState(error = error))
            OutlinedButton(onClick = onCheck, enabled = !busy) { Text(stringResource(R.string.check_deletion_result)) }
            Text(stringResource(R.string.account_deletion_reauth))
            PasswordField(password, onPassword, stringResource(R.string.current_password))
            Button(onClick = onRetry, enabled = !busy && password.isNotEmpty()) { Text(stringResource(R.string.retry_account_deletion)) }
            TextButton(onClick = onLogout, enabled = !busy) { Text(stringResource(R.string.logout)) }
        }
    }
}
