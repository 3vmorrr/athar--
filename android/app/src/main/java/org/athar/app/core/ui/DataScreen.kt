package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.CachedResult
import org.athar.app.core.network.ApiException

data class DataState(val loading: Boolean = true, val result: CachedResult? = null, val error: String? = null)
class DataViewModel(private val graph: AppGraph, private val path: String, private val query: Map<String, String>) : ViewModel() {
    private val mutable = MutableStateFlow(DataState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try { mutable.value = DataState(false, graph.content.get(path, query)) }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { mutable.value = DataState(false, error = if (failure is ApiException) failure.message else null) }
        }
    }
}
@Composable fun DataScreen(graph: AppGraph, path: String, query: Map<String, String> = emptyMap(), content: @Composable (JsonElement, () -> Unit) -> Unit) {
    val identity = "${graph.session.userId}:${androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()}:$path" + query.toSortedMap().toString()
    val vm: DataViewModel = viewModel(key = identity, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DataViewModel(graph, path, query) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val revision by graph.content.revision.collectAsStateWithLifecycle()
    LaunchedEffect(identity, revision) { vm.load() }
    Column(Modifier.fillMaxSize()) {
        when {
            state.result != null -> {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.result!!.offline) Box(Modifier.padding(16.dp)) { MessagePanel(stringResource(R.string.offline_notice)) }
                content(state.result!!.data, vm::load)
            }
            state.loading -> ContentLoadingSkeleton()
            else -> Box(Modifier.padding(24.dp)) { FailurePanel(state.error, vm::load) }
        }
    }
}
