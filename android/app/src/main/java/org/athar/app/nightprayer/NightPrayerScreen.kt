package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.LocalNightSession
import org.athar.app.core.ui.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import java.time.*

data class NightUiState(val rows: List<Pair<LocalNightSession, JsonObject>> = emptyList(), val busy: Boolean = false, val error: Boolean = false, val conflicts: Int = 0,val loading:Boolean=true,val unreadable:Boolean=false)
class NightViewModel(val graph: AppGraph) : ViewModel() {
    private val user = requireNotNull(graph.session.userId)
    private val mutable = MutableStateFlow(NightUiState())
    val state = mutable.asStateFlow()
    private var observation:Job?=null
    init { retryLocal() }
    fun retryLocal(){
        observation?.cancel()
        mutable.value=mutable.value.copy(loading=true,unreadable=false)
        observation=viewModelScope.launch { graph.nightPrayer.observe(user).protectedReads().collect {
            when(it){
                is ProtectedRead.Ready->mutable.value=mutable.value.copy(rows=it.value,loading=false,unreadable=false)
                ProtectedRead.Unreadable->mutable.value=mutable.value.copy(rows=emptyList(),loading=false,unreadable=true)
                ProtectedRead.Loading->Unit
            }
        } }
    }
    fun sync() = operation { mutable.value = mutable.value.copy(conflicts = graph.nightPrayer.sync()) }
    fun save(payload: JsonObject, id: String?, done: () -> Unit) = operation { if (id == null) graph.nightPrayer.save(payload) else graph.nightPrayer.save(payload, id); done() }
    fun delete(id: String) = operation { graph.nightPrayer.delete(id) }
    fun useServer(id: String) = operation { graph.nightPrayer.useServer(id); mutable.value = mutable.value.copy(conflicts = 0) }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy||mutable.value.loading||mutable.value.unreadable) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = false)
            try { block() } catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}

@Composable fun NightPrayerScreen(graph: AppGraph) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("history") }
    val vm: NightViewModel = viewModel(key = "night:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NightViewModel(graph) as T })
    Column(Modifier.fillMaxSize()) {
        AtharHeroSurface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        AtharIcons.NightsStay,
                        null,
                        Modifier.padding(12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.night_prayer), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.night_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(section == "history", { section = "history" }, label = { Text(stringResource(R.string.night_history)) })
            FilterChip(section == "calendar", { section = "calendar" }, label = { Text(stringResource(R.string.night_calendar)) })
            FilterChip(section == "tools", { section = "tools" }, label = { Text(stringResource(R.string.night_tools)) })
        }
        AtharPageTransition(section, Modifier.weight(1f)) {
            when(it) {
                "tools" -> NightToolsScreen(graph)
                "calendar" -> NightCalendarScreen(vm)
                else -> NightHistoryScreen(graph)
            }
        }
    }
}

@Composable private fun NightHistoryScreen(graph: AppGraph) {
    val vm: NightViewModel = viewModel(key = "night:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NightViewModel(graph) as T })
    val state by vm.state.collectAsStateWithLifecycle()
    if(state.loading){Box(Modifier.padding(20.dp)){LinearProgressIndicator(Modifier.fillMaxWidth())};return}
    if(state.unreadable){Box(Modifier.padding(20.dp)){MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick=vm::retryLocal){Text(stringResource(R.string.retry))}}};return}
    var editing by remember { mutableStateOf<Pair<LocalNightSession, JsonObject>?>(null) }
    var form by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val totals = remember(state.rows) { state.rows.groupBy { it.first.nightDate }.mapValues { (_, list) -> list.sumOf { it.first.verseCount } } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "summary", contentType = "summary") { Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.night_totals, totals.size, totals.values.sum()), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.night_self_report), style = MaterialTheme.typography.bodySmall)
            val pace = NightRules.pace(state.rows.mapNotNull { row -> row.first.measuredSeconds?.let { row.first.verseCount to it } })
            if (pace != null) Text(stringResource(R.string.night_measured_pace, pace))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = null; form = true }, enabled = !state.busy) { Text(stringResource(R.string.night_add)) }
                OutlinedButton(onClick = vm::sync, enabled = !state.busy) { Text(stringResource(R.string.sync)) }
            }
        } } }
        if (state.busy) item(key = "busy", contentType = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error) item(key = "error", contentType = "feedback") { FailurePanel(null, vm::sync) }
        if (state.conflicts > 0) item(key = "conflict", contentType = "feedback") { MessagePanel(stringResource(R.string.night_conflict), true) }
        if (state.rows.isEmpty()) item(key = "empty", contentType = "empty") { Text(stringResource(R.string.night_empty)) }
        items(state.rows, key = { it.first.id }, contentType = { "session" }) { (local, body) ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(local.nightDate, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.night_session_count, local.verseCount))
                if (body.text("entry_type") == "range") Text("${body.text("start_index")} – ${body.text("end_index")}", style = MaterialTheme.typography.labelMedium)
                if (body.text("note").isNotBlank()) Text(body.text("note"))
                Text(stringResource(if (local.dirty) R.string.night_pending else R.string.night_synced), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = local to body; form = true }, enabled = !state.busy) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { deleting = local.id }, enabled = !state.busy) { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) }
                    if (state.conflicts > 0 && local.dirty) TextButton(onClick = { vm.useServer(local.id) }) { Text(stringResource(R.string.use_server)) }
                }
            } }
        }
        item(key = "source", contentType = "source") { Text(stringResource(R.string.night_source), style = MaterialTheme.typography.bodySmall) }
    }
    if (form) NightEntryDialog(graph, editing, state.busy, { form = false }) { payload -> vm.save(payload, editing?.first?.id) { form = false } }
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_action)) }, text = { Text(stringResource(R.string.night_delete_confirmation)) }, confirmButton = { TextButton(onClick = { deleting?.let(vm::delete); deleting = null }) { Text(stringResource(R.string.delete_action)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun NightEntryDialog(graph: AppGraph, editing: Pair<LocalNightSession, JsonObject>?, busy: Boolean, dismiss: () -> Unit, save: (JsonObject) -> Unit) {
    val initial = editing?.second
    var date by remember { mutableStateOf(initial?.text("night_date") ?: LocalDate.now().toString()) }
    var zone by remember { mutableStateOf(initial?.text("timezone") ?: NightRules.serverTimezone(ZoneId.systemDefault())) }
    var range by remember { mutableStateOf(initial?.text("entry_type") == "range") }
    var count by remember { mutableStateOf(initial?.text("verse_count").orEmpty()) }
    var startSurah by remember { mutableStateOf("1") }; var startAyah by remember { mutableStateOf("1") }
    var endSurah by remember { mutableStateOf("1") }; var endAyah by remember { mutableStateOf("7") }
    var note by remember { mutableStateOf(initial?.text("note").orEmpty()) }
    var error by remember { mutableStateOf(false) }; var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initial) { if (initial?.text("entry_type") == "range") {
        val index = graph.quran.data().index
        val start = index.position(initial["start_index"].asInt); val end = index.position(initial["end_index"].asInt)
        startSurah = "${start.surah}"; startAyah = "${start.ayah}"; endSurah = "${end.surah}"; endAyah = "${end.ayah}"
    } }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (editing == null) R.string.night_add else R.string.edit)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.night_date_explanation)); DateField(date, { date = it }, stringResource(R.string.night_date), latest = LocalDate.now()) }
            item { OutlinedTextField(zone, { zone = it.take(80) }, label = { Text(stringResource(R.string.timezone)) }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!range, { range = false }, label = { Text(stringResource(R.string.night_manual_count)) }); FilterChip(range, { range = true }, label = { Text(stringResource(R.string.night_range)) }) } }
            if (range) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField(startSurah, { startSurah = it }, stringResource(R.string.start_surah), Modifier.weight(1f)); NumberField(startAyah, { startAyah = it }, stringResource(R.string.start_ayah), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField(endSurah, { endSurah = it }, stringResource(R.string.end_surah), Modifier.weight(1f)); NumberField(endAyah, { endAyah = it }, stringResource(R.string.end_ayah), Modifier.weight(1f)) } }
            } else item { NumberField(count, { count = it }, stringResource(R.string.verse_count), Modifier.fillMaxWidth()) }
            item { OutlinedTextField(note, { note = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.private_note)) }, maxLines = 4) }
            if (error) item { Text(stringResource(R.string.invalid_input), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && !submitting, onClick = {
        submitting = true; scope.launch {
            try {
                val parsed = LocalDate.parse(date); val tz = ZoneId.of(zone); require(parsed <= LocalDate.now(tz) && parsed.year >= 1900)
                val body = JsonObject().apply {
                    addProperty("night_date", parsed.toString()); addProperty("timezone", tz.id); addProperty("boundary_source", "manual"); addProperty("entry_type", if (range) "range" else "manual"); addProperty("note", note)
                    if (range) { val index = graph.quran.data().index; val selected = index.range(startSurah.toInt(), startAyah.toInt(), endSurah.toInt(), endAyah.toInt()); addProperty("start_index", selected.start.globalIndex); addProperty("end_index", selected.end.globalIndex) }
                    else { val total = count.toInt(); require(total in 1..100000); addProperty("verse_count", total) }
                }
                save(body)
            } catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true } finally { submitting = false }
        }
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun NumberField(value: String, change: (String) -> Unit, label: String, modifier: Modifier) { OutlinedTextField(value, { change(it.filter(Char::isDigit).take(6)) }, modifier, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
