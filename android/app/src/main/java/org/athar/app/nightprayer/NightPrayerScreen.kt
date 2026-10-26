package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

data class NightUiState(val rows: List<Pair<LocalNightSession, JsonObject>> = emptyList(), val busy: Boolean = false, val error: Boolean = false,
    val conflictRows:List<NightConflict> = emptyList(),val loading:Boolean=true,val unreadable:Boolean=false,
    val reviewId:String?=null,val review:NightConflictReview?=null,val reviewLoading:Boolean=false,val reviewError:Boolean=false,val reviewChanged:Boolean=false,val reviewLocalError:Boolean=false){
    val conflicts:Int get()=conflictRows.size
}
class NightViewModel(val graph: AppGraph) : ViewModel() {
    private val user = requireNotNull(graph.session.userId)
    suspend fun exemptions()=graph.nightPrayer.localExemptions(user)
    private val mutable = MutableStateFlow(NightUiState())
    val state = mutable.asStateFlow()
    private var observation:Job?=null
    private var reviewJob:Job?=null
    init { retryLocal() }
    fun retryLocal(){
        observation?.cancel()
        mutable.value=mutable.value.copy(loading=true,unreadable=false)
        observation=viewModelScope.launch { combine(graph.nightPrayer.observe(user),graph.nightPrayer.observeConflicts(user)){rows,conflicts->rows to conflicts}.protectedReads().collect {
            when(it){
                is ProtectedRead.Ready->mutable.value=mutable.value.copy(rows=it.value.first,conflictRows=it.value.second,loading=false,unreadable=false)
                ProtectedRead.Unreadable->{reviewJob?.cancel();mutable.value=mutable.value.copy(rows=emptyList(),conflictRows=emptyList(),reviewId=null,review=null,loading=false,unreadable=true)}
                ProtectedRead.Loading->Unit
            }
        } }
    }
    fun sync() = operation { graph.nightPrayer.sync(user) }
    fun save(payload: JsonObject, id: String?, done: () -> Unit) = operation { if (id == null) graph.nightPrayer.save(payload,expectedOwner=user) else graph.nightPrayer.save(payload, id,user); done() }
    fun delete(id: String) = operation { graph.nightPrayer.delete(id,user) }
    fun review(id:String){
        if(mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user)return
        reviewJob?.cancel()
        mutable.value=mutable.value.copy(reviewId=id,review=null,reviewLoading=true,reviewError=false,reviewChanged=false,reviewLocalError=false)
        reviewJob=viewModelScope.launch{
            try{val result=graph.nightPrayer.reviewConflict(id,user);if(mutable.value.reviewId==id)mutable.value=mutable.value.copy(review=result,reviewLoading=false)}
            catch(failure:CancellationException){throw failure}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(reviewLoading=false,reviewError=true,reviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(reviewLoading=false,reviewError=true)}
        }
    }
    fun closeReview(){if(!mutable.value.busy){reviewJob?.cancel();mutable.value=mutable.value.copy(reviewId=null,review=null,reviewLoading=false,reviewError=false,reviewChanged=false,reviewLocalError=false)}}
    fun resolve(choice:NightConflictChoice){
        val selected=mutable.value.review?:return
        if(mutable.value.busy||mutable.value.unreadable||mutable.value.loading||graph.session.userId!=user)return
        viewModelScope.launch{
            mutable.value=mutable.value.copy(busy=true,reviewError=false,reviewChanged=false,reviewLocalError=false,error=false)
            try{
                graph.nightPrayer.resolveConflict(selected,choice)
                mutable.value=mutable.value.copy(reviewId=null,review=null)
                try{graph.nightPrayer.sync(user)}catch(failure:CancellationException){throw failure}catch(_:Exception){mutable.value=mutable.value.copy(error=true)}
            }catch(failure:CancellationException){throw failure}
            catch(_:NightConflictChanged){mutable.value=mutable.value.copy(review=null,reviewChanged=true)}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(reviewError=true,reviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(reviewError=true)}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user) return
        viewModelScope.launch {
            if(graph.session.userId!=user)return@launch
            mutable.value = mutable.value.copy(busy = true, error = false)
            try { block() } catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}

@Composable fun NightPrayerScreen(graph: AppGraph) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("tonight") }
    var addRequest by remember { mutableIntStateOf(0) }
    val vm: NightViewModel = viewModel(
        key = "night:${graph.session.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NightViewModel(graph) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AtharImmersiveHero(
            presentation = AtharHeroPresentation(
                artwork = AtharHeroArtwork.NightPrayer,
                eyebrow = stringResource(R.string.night_prayer),
                title = stringResource(R.string.night_prayer),
                supportingText = stringResource(R.string.night_intro),
                statusText = when {
                    state.unreadable -> stringResource(R.string.local_data_unreadable)
                    state.conflicts > 0 -> stringResource(R.string.night_conflict)
                    state.rows.any { it.first.dirty } -> stringResource(R.string.night_pending)
                    else -> null
                },
                statusTone = if (state.unreadable || state.conflicts > 0) {
                    AtharHeroStatusTone.Warning
                } else {
                    AtharHeroStatusTone.Neutral
                },
                primaryActionLabel = if (!state.loading && !state.unreadable) {
                    stringResource(R.string.night_add)
                } else {
                    null
                },
            ),
            onPrimaryAction = {
                section = "history"
                addRequest++
            },
        )

        FlowRow(
            Modifier.padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            FilterChip(
                section == "tonight",
                { section = "tonight" },
                label = { Text(stringResource(R.string.night_tonight)) },
            )
            FilterChip(
                section == "plan",
                { section = "plan" },
                label = { Text(stringResource(R.string.night_plan_title)) },
            )
            FilterChip(
                section == "history",
                { section = "history" },
                label = { Text(stringResource(R.string.night_history)) },
            )
            FilterChip(
                section == "calendar",
                { section = "calendar" },
                label = { Text(stringResource(R.string.night_calendar)) },
            )
            FilterChip(
                section == "tools",
                { section = "tools" },
                label = { Text(stringResource(R.string.night_tools)) },
            )
        }

        AtharPageTransition(section, Modifier.weight(1f)) {
            when (it) {
                "tonight" -> NightTonightScreen(graph, vm)
                "plan" -> NightPlanScreen(graph, vm)
                "tools" -> NightToolsScreen(graph)
                "calendar" -> NightCalendarScreen(vm)
                else -> NightHistoryScreen(graph, vm, addRequest)
            }
        }
    }
}

@Composable private fun NightHistoryScreen(
    graph: AppGraph,
    vm: NightViewModel,
    addRequest: Int,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    if(state.loading){Box(Modifier.padding(20.dp)){LinearProgressIndicator(Modifier.fillMaxWidth())};return}
    if(state.unreadable){Box(Modifier.padding(20.dp)){MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick=vm::retryLocal){Text(stringResource(R.string.retry))}}};return}
    var editing by remember { mutableStateOf<Pair<LocalNightSession, JsonObject>?>(null) }
    var form by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(addRequest) {
        if (addRequest > 0 && !state.loading && !state.unreadable) {
            editing = null
            form = true
        }
    }
    val totals = remember(state.rows) { state.rows.groupBy { it.first.nightDate }.mapValues { (_, list) -> list.sumOf { it.first.verseCount } } }
    val conflictedIds=remember(state.conflictRows){state.conflictRows.map{it.local.id}.toSet()}
    LazyColumn(Modifier.testTag("night-history-list"),contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_history))
        }
        item(key = "summary", contentType = "summary") { Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        items(state.conflictRows.filter{it.local.deleted},key={"deleted-conflict:${it.local.id}"},contentType={"deleted-conflict"}){conflict->
            OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(R.string.night_pending_delete),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={vm.review(conflict.local.id)},enabled=!state.busy,modifier=Modifier.testTag("night-conflict-${conflict.local.id}")){Text(stringResource(R.string.night_review_conflict))}
            }}
        }
        if (state.rows.isEmpty()) item(key = "empty", contentType = "empty") { Text(stringResource(R.string.night_empty)) }
        items(state.rows, key = { it.first.id }, contentType = { "session" }) { (local, body) ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(local.nightDate, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.night_session_count, local.verseCount))
                if (body.text("entry_type") == "range") Text("${body.text("start_index")} – ${body.text("end_index")}", style = MaterialTheme.typography.labelMedium)
                if (body.text("note").isNotBlank()) Text(body.text("note"))
                AtharStatusPill(
                    stringResource(if (local.dirty) R.string.night_pending else R.string.night_synced),
                    emphasized = local.dirty,
                )
                val conflicted=local.id in conflictedIds
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = local to body; form = true }, enabled = !state.busy&&!conflicted) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { deleting = local.id }, enabled = !state.busy&&!conflicted) { Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error) }
                    if(conflicted)TextButton(onClick={vm.review(local.id)},enabled=!state.busy,modifier=Modifier.testTag("night-conflict-${local.id}")){Text(stringResource(R.string.night_review_conflict))}
                }
            } }
        }
        item(key = "source", contentType = "source") { Text(stringResource(R.string.night_source), style = MaterialTheme.typography.bodySmall) }
    }
    if (form) NightEntryDialog(graph, editing, state.busy, editing?.first?.id in conflictedIds, { form = false }) { payload -> vm.save(payload, editing?.first?.id) { form = false } }
    if(state.reviewId!=null)NightConflictDialog(state,vm::closeReview,{state.reviewId?.let(vm::review)},vm::resolve)
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_action)) }, text = { Text(stringResource(R.string.night_delete_confirmation)) }, confirmButton = { TextButton(onClick = { deleting?.let(vm::delete); deleting = null }) { Text(stringResource(R.string.delete_action)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun NightEntryDialog(graph: AppGraph, editing: Pair<LocalNightSession, JsonObject>?, busy: Boolean, conflicted:Boolean, dismiss: () -> Unit, save: (JsonObject) -> Unit) {
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
        LazyColumn(Modifier.testTag("night-entry-fields"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(conflicted)item{Text(stringResource(R.string.night_edit_conflict),color=MaterialTheme.colorScheme.error)}
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
    }, confirmButton = { TextButton(enabled = !busy && !submitting&&!conflicted, onClick = {
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
