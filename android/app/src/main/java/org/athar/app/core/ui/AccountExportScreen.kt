package org.athar.app.core.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionLease
import org.athar.app.core.data.*
import java.io.IOException
import java.time.LocalDate

private enum class ExportPhase { IDLE, PREPARING, READY, PICKING, WRITING, SAVED }
private enum class ExportError { LOCAL, REMOTE, PAYLOAD, WRITE, PARTIAL, EXPIRED }
private data class ExportState(val phase: ExportPhase = ExportPhase.IDLE, val error: ExportError? = null)

private class AccountExportViewModel(application: Application, private val graph: AppGraph, private val owner: Long) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ExportState())
    val state = mutable.asStateFlow()
    private var bytes: ByteArray? = null
    private var lease: SessionLease? = null
    private val resolver = application.contentResolver
    init {
        viewModelScope.launch {
            graph.session.state.collect { auth ->
                if (auth !is AuthState.SignedIn || auth.user.id != owner) {
                    discard()
                    mutable.value = ExportState(error = ExportError.EXPIRED)
                }
            }
        }
    }
    private fun discard() { bytes?.fill(0); bytes = null; lease = null }
    fun prepare() {
        if (mutable.value.phase in setOf(ExportPhase.PREPARING, ExportPhase.PICKING, ExportPhase.WRITING)) return
        discard(); mutable.value = ExportState(ExportPhase.PREPARING)
        viewModelScope.launch {
            var stage = ExportError.LOCAL
            try {
                val captured = graph.session.lease(owner)
                val local = retainedAccountData(owner, graph)
                graph.session.requireLease(captured)
                stage = ExportError.REMOTE
                val response = graph.session.request("GET", "me/export", expectedUser = owner)
                graph.session.requireLease(captured)
                stage = ExportError.PAYLOAD
                val account = response.asJsonObject
                val prepared = withContext(Dispatchers.Default) {
                    AccountExportPolicy.envelope(owner, graph.partition, account, local).toString().toByteArray(Charsets.UTF_8)
                        .also { require(it.isNotEmpty() && it.size <= MAX_ACCOUNT_EXPORT_BYTES) }
                }
                graph.session.requireLease(captured)
                bytes = prepared; lease = captured
                mutable.value = ExportState(ExportPhase.READY)
            } catch (cancelled: CancellationException) { discard(); throw cancelled }
            catch (_: Exception) { discard(); mutable.value = ExportState(error = stage) }
        }
    }
    fun beginPicker(): Boolean = try {
        check(mutable.value.phase == ExportPhase.READY && bytes != null)
        graph.session.requireLease(checkNotNull(lease))
        mutable.value = ExportState(ExportPhase.PICKING); true
    } catch (_: Exception) { discard(); mutable.value = ExportState(error = ExportError.EXPIRED); false }
    fun pickerFailed() { discard(); mutable.value = ExportState(error = ExportError.WRITE) }

    fun picked(uri: Uri?) {
        if (uri == null) { discard(); mutable.value = ExportState(); return }
        // Enter the cleanup try/finally even if the owning screen is cleared as the result arrives.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutable.value = ExportState(ExportPhase.WRITING)
            var completed = false
            try {
                require(uri.scheme == "content" && DocumentsContract.isDocumentUri(getApplication(), uri))
                val captured = checkNotNull(lease)
                val copy = checkNotNull(bytes)
                withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    writeAccountExport(copy, { coroutine.ensureActive(); graph.session.requireLease(captured) }) {
                        resolver.openOutputStream(uri, "wt") ?: throw IOException("Export destination unavailable")
                    }
                }
                graph.session.requireLease(captured)
                completed = true
                mutable.value = ExportState(ExportPhase.SAVED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = ExportState(error = ExportError.WRITE) }
            finally {
                if (!completed) {
                    val removed = withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { uri.scheme == "content" && DocumentsContract.isDocumentUri(getApplication(), uri) && DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
                    }
                    if (!removed) mutable.value = ExportState(error = ExportError.PARTIAL)
                }
                discard()
            }
        }
    }
    override fun onCleared() { discard() }
}

@Composable internal fun AccountExportScreen(graph: AppGraph) {
    val owner = graph.session.userId ?: return
    val application = LocalContext.current.applicationContext as Application
    val vm: AccountExportViewModel = viewModel(key = "account-export:${graph.partition}:$owner", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountExportViewModel(application, graph, owner) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember(owner) { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::picked)
    AccountExportContent(state, onPrepare = { confirm = true }, onChoose = {
        if (vm.beginPicker()) try { exporter.launch("athar-data-${LocalDate.now()}.json") } catch (_: Exception) { vm.pickerFailed() }
    })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.export_data)) }, text = { Text(stringResource(R.string.export_privacy_confirmation)) }, confirmButton = {
        TextButton(onClick = { confirm = false; vm.prepare() }) { Text(stringResource(R.string.export_prepare)) }
    }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun AccountExportContent(state: ExportState, onPrepare: () -> Unit, onChoose: () -> Unit) {
    val busy = state.phase in setOf(ExportPhase.PREPARING, ExportPhase.PICKING, ExportPhase.WRITING)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AtharScreenIdentity(stringResource(R.string.export_data), AtharIcons.Lock)
        Text(stringResource(R.string.export_explanation))
        if (state.phase == ExportPhase.READY) Button(onClick = onChoose) { Text(stringResource(R.string.export_choose_destination)) }
        else Button(onClick = onPrepare, enabled = !busy) { Text(stringResource(R.string.export_prepare)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.phase == ExportPhase.SAVED) MessagePanel(stringResource(R.string.saved))
        state.error?.let { error -> MessagePanel(stringResource(when (error) {
            ExportError.LOCAL -> R.string.local_data_unreadable
            ExportError.REMOTE -> R.string.network_error
            ExportError.PAYLOAD -> R.string.account_data_unavailable
            ExportError.WRITE -> R.string.export_write_failed
            ExportError.PARTIAL -> R.string.export_partial_file
            ExportError.EXPIRED -> R.string.auth_session_expired
        }), true) }
    }
}
