package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.*

@OptIn(ExperimentalCoroutinesApi::class)
private class BookActivityViewModel(private val graph: AppGraph, private val owner: Long) : ViewModel() {
    private val retries = MutableStateFlow(0)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    val pending = retries.flatMapLatest {
        flow { graph.bookActivity.capture(owner); emitAll(graph.bookActivity.observePending(owner)) }
            .protectedReads().onStart { emit(ProtectedRead.Loading) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProtectedRead.Loading)
    fun retry() { retries.value++ }
    fun sync() {
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableFailed.value = false
        viewModelScope.launch {
            try { graph.bookActivity.sync(owner) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFailed.value = true }
            finally { mutableBusy.value = false; retry() }
        }
    }
}

@Composable internal fun BookActivitySyncPanel(graph: AppGraph, owner: Long) {
    val vm: BookActivityViewModel = viewModel(key = "book-activity:${graph.partition}:$owner", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = BookActivityViewModel(graph, owner) as T
    })
    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val value = pending) {
            ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            ProtectedRead.Unreadable -> Text(stringResource(R.string.account_data_unavailable))
            is ProtectedRead.Ready -> Text(stringResource(R.string.book_activity_pending, value.value), style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::retry, enabled = !busy) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = vm::sync, enabled = !busy && ((pending as? ProtectedRead.Ready)?.value ?: 0) > 0) { Text(stringResource(if (busy) R.string.syncing else R.string.book_activity_sync)) }
        }
        if (failed) MessagePanel(stringResource(R.string.book_activity_sync_unconfirmed), true)
    }
}
