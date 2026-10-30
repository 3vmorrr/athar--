package org.athar.app.nightprayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.SessionLease
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.*
import org.athar.app.quran.QuranRange
import java.time.*

internal sealed interface NightPlanEvent {
    data class Edit(val draft: NightPlanDraft) : NightPlanEvent
    data object Calculate : NightPlanEvent
    data object Save : NightPlanEvent
    data object Retry : NightPlanEvent
    data object Reload : NightPlanEvent
    data class Clear(val version: Long) : NightPlanEvent
    data class Copy(val range: QuranRange) : NightPlanEvent
    data class Read(val scope: NightReadingScope) : NightPlanEvent
    data object OpenRecord : NightPlanEvent
    data class EditRecord(val date: String, val zone: String, val note: String) : NightPlanEvent
    data object Record : NightPlanEvent
    data object CloseRecord : NightPlanEvent
}

@Composable internal fun NightPlanScreen(graph: AppGraph, fontSize: Int = 28) {
    val actor by graph.session.state.collectAsStateWithLifecycle()
    val owner = (actor as? org.athar.app.auth.AuthState.SignedIn)?.user?.id ?: return
    val lease = runCatching { graph.session.lease(owner) }.getOrNull() ?: return
    key(lease) { NightPlanOwned(graph, lease, fontSize) }
}

@Composable private fun NightPlanOwned(graph: AppGraph, lease: SessionLease, fontSize: Int) {
    val context = LocalContext.current
    val vm: NightPlanViewModel = viewModel(key = "night-plan:${graph.partition}:${lease.owner}:${lease.generation}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val gateway = object : NightPlanGateway {
                override fun requireActive() = graph.session.requireLease(lease)
                override fun changes() = graph.nightPlans.observe(lease)
                override suspend fun index() = graph.quran.data().index
                override suspend fun read() = graph.nightPlans.read(lease)
                override suspend fun save(range: QuranRange, expected: Long) = graph.nightPlans.save(lease, range, expected)
                override suspend fun clear(expected: Long) = graph.nightPlans.clear(lease, expected)
                override suspend fun record(record: NightPlanRecord, expected: Long) = graph.nightPlans.record(lease, expected, record.plan, record.id,
                    LocalDate.parse(record.body["night_date"].asString), ZoneId.of(record.body["timezone"].asString), record.body["note"].asString)
            }
            return NightPlanViewModel(gateway) as T
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm, graph.session) { graph.session.state.collect { if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate() } }
    if (runCatching { graph.session.requireLease(lease) }.isFailure) return
    var reader by remember { mutableStateOf<NightReadingScope?>(null) }
    val list = remember { LazyListState() }
    reader?.let { scope -> NightReaderScreen(graph, lease, scope, fontSize) { reader = null }; return }
    NightPlanContent(state, list) { event ->
        if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate()
        else when (event) {
            is NightPlanEvent.Edit -> vm.edit(event.draft)
            NightPlanEvent.Calculate -> vm.calculate()
            NightPlanEvent.Save -> vm.save()
            NightPlanEvent.Retry -> vm.retry()
            NightPlanEvent.Reload -> vm.reloadForm()
            is NightPlanEvent.Clear -> vm.clear(event.version)
            is NightPlanEvent.Read -> reader = event.scope
            NightPlanEvent.OpenRecord -> vm.openRecord()
            is NightPlanEvent.EditRecord -> vm.editRecord(event.date, event.zone, event.note)
            NightPlanEvent.Record -> vm.record()
            NightPlanEvent.CloseRecord -> vm.closeRecord()
            is NightPlanEvent.Copy -> {
                val range = event.range
                val text = "${range.start.surah}:${range.start.ayah} — ${range.end.surah}:${range.end.ayah} · ${range.verseCount}"
                val clip = ClipData.newPlainText(context.getString(R.string.night_plan_reference), text).apply {
                    description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            }
        }
    }
}

/** App wiring, persistence and private imperative work stay outside plain rendering. */
@Composable internal fun NightPlanContent(state: NightPlanUiState, list: LazyListState = remember { LazyListState() }, event: (NightPlanEvent) -> Unit) {
    var clearVersion by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableStateOf(false) }
    val saved = (state.read as? ProtectedRead.Ready)?.value
    val actionable = state.ready && !state.busy
    val draft = state.draft
    LazyColumn(Modifier.fillMaxSize().testTag("night-plan-screen"), state = list, contentPadding = PaddingValues(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
        item(key = "identity", contentType = "heading") { AtharSectionHeader(stringResource(R.string.night_plan_title)); Text(stringResource(R.string.night_plan_notice)) }
        if (state.busy || state.read == ProtectedRead.Loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.read == ProtectedRead.Unreadable) item(key = "unreadable") { MessagePanel(stringResource(R.string.local_data_unreadable), true) {
            TextButton(onClick = { event(NightPlanEvent.Retry) }) { Text(stringResource(R.string.retry)) }
        } }
        state.feedback?.let { feedback -> item(key = "feedback") { MessagePanel(stringResource(planFeedback(feedback, state.record != null)), feedback in setOf(NightPlanFeedback.INVALID, NightPlanFeedback.CHANGED, NightPlanFeedback.UNAVAILABLE, NightPlanFeedback.UNCONFIRMED)) } }
        item(key = "form", contentType = "form") { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                FilterChip(draft.byTarget, { event(NightPlanEvent.Edit(draft.copy(byTarget = true))) }, enabled = state.ready, label = { Text(stringResource(R.string.target_count)) })
                FilterChip(!draft.byTarget, { event(NightPlanEvent.Edit(draft.copy(byTarget = false))) }, enabled = state.ready, label = { Text(stringResource(R.string.night_range)) })
            }
            PlanNumber(draft.startSurah, R.string.start_surah, state.ready, "plan-start-surah") { event(NightPlanEvent.Edit(draft.copy(startSurah = it))) }
            PlanNumber(draft.startAyah, R.string.start_ayah, state.ready, "plan-start-ayah") { event(NightPlanEvent.Edit(draft.copy(startAyah = it))) }
            if (draft.byTarget) PlanNumber(draft.count, R.string.target_count, state.ready, "plan-count") { event(NightPlanEvent.Edit(draft.copy(count = it))) }
            else {
                PlanNumber(draft.endSurah, R.string.end_surah, state.ready, "plan-end-surah") { event(NightPlanEvent.Edit(draft.copy(endSurah = it))) }
                PlanNumber(draft.endAyah, R.string.end_ayah, state.ready, "plan-end-ayah") { event(NightPlanEvent.Edit(draft.copy(endAyah = it))) }
            }
            Button(onClick = { event(NightPlanEvent.Calculate) }, enabled = actionable, modifier = Modifier.testTag("plan-calculate")) { Text(stringResource(R.string.calculate)) }
            state.preview?.let { range ->
                Text(stringResource(R.string.night_plan_preview, planReference(range), range.verseCount), modifier = Modifier.testTag("plan-preview"))
                Button(onClick = { event(NightPlanEvent.Save) }, enabled = actionable, modifier = Modifier.testTag("plan-save")) { Text(stringResource(R.string.night_save_plan)) }
            }
        } } }
        item(key = "saved-heading", contentType = "heading") { AtharSectionHeader(stringResource(R.string.night_saved_plan)) }
        if (saved != null && saved.plan == null) item(key = "empty") { QuietEmptyState(stringResource(R.string.night_plan_empty)) }
        if (saved?.plan != null && state.savedRange != null) item(key = "saved-plan", contentType = "plan") {
            val range = state.savedRange
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.padding(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                Text(planReference(range), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("plan-saved-reference"))
                Text(stringResource(R.string.night_plan_verse_count, range.verseCount))
                Text(stringResource(R.string.night_plan_separate_progress), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    Button(onClick = { event(NightPlanEvent.Read(NightReadingScope(saved.owner, saved.namespace, "plan", saved.plan.id, saved.plan.startIndex, saved.plan.endIndex))) }, enabled = actionable, modifier = Modifier.testTag("plan-read")) { Text(stringResource(R.string.night_reader_open)) }
                    OutlinedButton(onClick = { event(NightPlanEvent.Copy(range)) }, enabled = actionable, modifier = Modifier.testTag("plan-copy")) { Text(stringResource(R.string.night_copy_reference)) }
                    Button(onClick = { event(NightPlanEvent.OpenRecord) }, enabled = actionable, modifier = Modifier.testTag("plan-record")) { Text(stringResource(R.string.night_record_completed)) }
                    TextButton(onClick = { clearVersion = saved.version }, enabled = actionable) { Text(stringResource(R.string.night_clear_plan)) }
                }
            } }
        }
        item(key = "retry") { FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
            TextButton(onClick = { event(NightPlanEvent.Retry) }, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = { reload = true }, enabled = actionable) { Text(stringResource(R.string.night_plan_reload)) }
        } }
    }
    clearVersion?.let { reviewed -> PlanConfirmation(R.string.night_plan_clear_confirm, actionable, { clearVersion = null }, { clearVersion = null; event(NightPlanEvent.Clear(reviewed)) }) }
    if (reload) PlanConfirmation(R.string.night_plan_reload_confirm, actionable, { reload = false }, { reload = false; event(NightPlanEvent.Reload) })
    state.record?.let { dialog -> AlertDialog(onDismissRequest = { if (!state.busy) event(NightPlanEvent.CloseRecord) }, title = { Text(stringResource(R.string.night_record_completed)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            Text(stringResource(R.string.night_record_date_notice))
            Text(stringResource(R.string.night_plan_preview, planReference(dialog.range), dialog.range.verseCount))
            Text(stringResource(R.string.night_plan_record_confirm))
            state.feedback?.let { Text(stringResource(planFeedback(it, true))) }
            if (dialog.frozen) Text(stringResource(R.string.night_plan_record_frozen))
            if (!dialog.frozen) {
                DateField(dialog.date, { event(NightPlanEvent.EditRecord(it, dialog.zone, dialog.note)) }, stringResource(R.string.night_maghrib_date), latest = runCatching { LocalDate.now(ZoneId.of(dialog.zone)) }.getOrNull())
                OutlinedTextField(dialog.zone, { event(NightPlanEvent.EditRecord(dialog.date, it, dialog.note)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.timezone)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(dialog.note, { event(NightPlanEvent.EditRecord(dialog.date, dialog.zone, it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.private_note)) }, enabled = !state.busy, maxLines = 4)
            } else { Text(dialog.date); Text(dialog.zone) }
        }
    }, confirmButton = { TextButton(onClick = { event(NightPlanEvent.Record) }, enabled = actionable, modifier = Modifier.testTag("plan-record-confirm")) { Text(stringResource(if (dialog.frozen) R.string.retry else R.string.night_record_completed)) } }, dismissButton = { TextButton(onClick = { event(NightPlanEvent.CloseRecord) }, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } }) }
}

private fun planReference(range: QuranRange) = "${range.start.surah}:${range.start.ayah} — ${range.end.surah}:${range.end.ayah}"
private fun planFeedback(feedback: NightPlanFeedback, record: Boolean) = when (feedback) {
    NightPlanFeedback.SAVED -> R.string.saved
    NightPlanFeedback.SAVED_WITH_DRAFT -> R.string.night_plan_saved_with_draft
    NightPlanFeedback.CLEARED -> R.string.night_plan_cleared
    NightPlanFeedback.RECORDED_LOCALLY -> R.string.night_plan_recorded_locally
    NightPlanFeedback.INVALID -> if (record) R.string.invalid_input else R.string.range_error
    NightPlanFeedback.CHANGED -> R.string.night_plan_changed
    NightPlanFeedback.UNAVAILABLE -> R.string.account_data_unavailable
    NightPlanFeedback.UNCONFIRMED -> R.string.night_plan_unconfirmed
}
@Composable private fun PlanNumber(value: String, label: Int, enabled: Boolean, tag: String, change: (String) -> Unit) {
    OutlinedTextField(value, { change(NightPlanDraft.numberInput(it)) }, Modifier.fillMaxWidth().testTag(tag), label = { Text(stringResource(label)) }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
@Composable private fun PlanConfirmation(message: Int, enabled: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.night_plan_title)) }, text = { Text(stringResource(message)) }, confirmButton = { TextButton(onClick = confirm, enabled = enabled) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
