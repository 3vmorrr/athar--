package org.athar.app.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.athar.app.AppViewModel
import org.athar.app.OperationState
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun Feedback(state: OperationState) {
    state.error?.let { code -> MessagePanel(feedbackText(code), error = true) }
    state.message?.let { code -> MessagePanel(feedbackText(code), error = code == "mail_unavailable") }
}
@Composable private fun feedbackText(code: String): String = stringResource(authFeedbackResource(code))

@Composable fun AuthScreen(vm: AppViewModel) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val state by vm.operation.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    fun changeMode(next: String) { mode = next; password = ""; confirmation = ""; code = ""; vm.clearFeedback() }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Brand(Modifier.align(Alignment.CenterHorizontally))
            Text(stringResource(when (mode) { "register" -> R.string.create_account; "forgot" -> R.string.recover_password; "reset" -> R.string.reset_password; else -> R.string.welcome_back }), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.auth_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Feedback(state)
            if (mode == "register") {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.display_name)) }, singleLine = true)
                OutlinedTextField(username, { username = it.lowercase().take(24) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.username)) }, supportingText = { Text(stringResource(R.string.username_hint)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            }
            OutlinedTextField(email, { email = it.take(190) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.email)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            if (mode == "reset") OutlinedTextField(code, { code = OtpInput.normalize(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.verification_code)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            if (mode != "forgot") PasswordField(password, { password = it.take(200) }, stringResource(if (mode == "reset") R.string.new_password else R.string.password))
            if (mode == "register" || mode == "reset") PasswordField(confirmation, { confirmation = it.take(200) }, stringResource(R.string.confirm_password))
            Button(onClick = {
                focus.clearFocus()
                when (mode) { "register" -> vm.register(name, username, email, password, confirmation); "forgot" -> vm.forgot(email); "reset" -> vm.reset(email, code, password, confirmation); else -> vm.login(email, password) }
            }, enabled = !state.busy && email.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(when (mode) { "register" -> R.string.create_account; "forgot" -> R.string.send_reset; "reset" -> R.string.reset_password; else -> R.string.login }))
            }
            if (mode == "forgot") TextButton(onClick = { changeMode("reset") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.enter_reset_code)) }
            if (mode == "login") TextButton(onClick = { changeMode("forgot") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.forgot_password)) }
            HorizontalDivider()
            TextButton(onClick = { changeMode(if (mode == "login") "register" else "login") }, Modifier.fillMaxWidth()) { Text(stringResource(if (mode == "login") R.string.create_account else R.string.login)) }
        }
    }
}

@Composable fun VerificationScreen(vm: AppViewModel, pending: AuthState.Pending) {
    var code by remember { mutableStateOf("") }
    val state by vm.operation.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Brand(Modifier.align(Alignment.CenterHorizontally))
            Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.verify_body, pending.user.email))
            if (state.message == null && state.error == null && pending.deliveryStatus != null) MessagePanel(stringResource(if (pending.deliveryStatus == "accepted") R.string.mail_accepted else R.string.mail_unavailable), pending.deliveryStatus != "accepted")
            Feedback(state)
            OutlinedTextField(code, { code = OtpInput.normalize(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.verification_code)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = { vm.verify(code,pending.user.id) }, enabled = !state.busy && code.length == 6, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.verify_action)) }
            TextButton(onClick = {vm.resend(pending.user.id)}, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resend_code)) }
            TextButton(onClick = {vm.logout(pending.user.id)}, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) }
        }
    }
}
