package org.athar.app.khatmah

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import java.time.LocalDate

@Composable
internal fun KhatmahScreen(graph: AppGraph) {
    val owner = graph.session.userId ?: return
    val lease = runCatching { graph.session.lease(owner) }.getOrNull() ?: return
    val vm: KhatmahViewModel = viewModel(key = "khatmah:${graph.partition}:$owner:${lease.generation}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val gateway = object : KhatmahGateway {
                override fun requireActive() = graph.session.requireLease(lease)
                override suspend fun load(): Pair<KhatmahPlan?, Boolean> {
                    requireActive()
                    val result = graph.content.get("me/khatmah", expectedOwner = owner)
                    requireActive(); return KhatmahContract.parse(result.data) to result.offline
                }
                override suspend fun write(draft: KhatmahDraft, expected: Int, action: String?): KhatmahPlan {
                    requireActive()
                    val body = if (action == null) mapOf("start_date" to draft.start, "target_date" to draft.target, "completed_pages" to draft.pages.toInt(), "expected_revision" to expected)
                        else buildMap<String, Any?> {
                            put("action", action); put("expected_revision", expected)
                            if (action == "restart") { put("start_date", draft.start); put("target_date", draft.target) }
                        }
                    val result = graph.session.request(if (action == null) "PUT" else "POST", if (action == null) "me/khatmah" else "me/khatmah/action", body, expectedUser = owner)
                    requireActive(); val saved = requireNotNull(KhatmahContract.parse(result))
                    graph.content.invalidate("me/khatmah", owner)
                    requireActive(); return saved
                }
            }
            return KhatmahViewModel(gateway) as T
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.dayChanged(); delay(30_000) }
        }
    }
    LaunchedEffect(vm, graph.session) {
        graph.session.state.collect { if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate() }
    }
    KhatmahContent(state, vm::edit, vm::save, { vm.refresh() }, { vm.refresh(discard = true) }, vm::action)
}

/** Plain rendering boundary: samples can render it without graph, HTTP or a ViewModel. */
@Composable
internal fun KhatmahContent(
    state: KhatmahUiState,
    edit: (KhatmahDraft) -> Unit,
    save: () -> Unit,
    refresh: () -> Unit,
    discard: () -> Unit,
    action: (String, KhatmahDraft?) -> Unit,
) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    var restart by remember { mutableStateOf(KhatmahDraft.from(null, state.today)) }
    val plan = state.plan
    val metrics = plan?.let { KhatmahContract.metrics(it, state.today) }
    val preview = runCatching {
        val (start, target, pages) = state.draft.values(state.today, state.baseline)
        KhatmahContract.metrics(KhatmahPlan(start, target, pages, plan?.status ?: "active", 1), state.today)
    }.getOrNull()
    val actionable = state.ready && !state.loading && !state.busy && !state.offline
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("khatmah-editor")) {
        item(key = "identity") { AtharScreenIdentity(stringResource(R.string.khatmah), AtharIcons.MenuBook, subtitle = stringResource(R.string.khatmah_plan_hint)) }
        if (state.loading || state.busy) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.offline) item(key = "offline") { MessagePanel(stringResource(R.string.khatmah_offline), true) }
        state.feedback?.let { feedback -> item(key = "feedback") {
            val label = when (feedback) {
                KhatmahFeedback.SAVED -> R.string.saved
                KhatmahFeedback.SAVED_WITH_DRAFT -> R.string.khatmah_saved_with_draft
                KhatmahFeedback.INVALID -> R.string.khatmah_invalid
                KhatmahFeedback.CONFLICT -> R.string.khatmah_conflict
                KhatmahFeedback.UNAVAILABLE -> R.string.account_data_unavailable
                KhatmahFeedback.UNCONFIRMED -> R.string.khatmah_unconfirmed
            }
            MessagePanel(stringResource(label), feedback !in setOf(KhatmahFeedback.SAVED, KhatmahFeedback.SAVED_WITH_DRAFT))
        } }
        if (state.ready) {
            if (plan != null) item(key = "summary") {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.khatmah_summary, plan.completed, metrics!!.getValue("progress_percent"), metrics.getValue("remaining_pages")), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.daily_pages, metrics.getValue("daily_pages").toString()))
                    Text(stringResource(R.string.khatmah_schedule, metrics.getValue("days_remaining"), plan.start.toString(), plan.target.toString()))
                    Text(stringResource(when (plan.status) { "paused" -> R.string.paused; "finished" -> R.string.finished; else -> R.string.active }))
                    if (state.today.isAfter(plan.target) && plan.completed < 604) Text(stringResource(R.string.khatmah_overdue))
                } }
            }
            // A finish ACK may arrive after more typing: keep that unsaved draft visible.
            if (plan?.status != "finished" || state.dirty) {
                item(key = "start") { DateField(state.draft.start, { edit(state.draft.copy(start = it)) }, stringResource(R.string.khatmah_start), Modifier.testTag("khatmah-start")) }
                item(key = "target") { DateField(state.draft.target, { edit(state.draft.copy(target = it)) }, stringResource(R.string.target_date), Modifier.testTag("khatmah-target")) }
                item(key = "pages") { OutlinedTextField(state.draft.pages, { edit(state.draft.copy(pages = KhatmahContract.numberInput(it))) }, Modifier.fillMaxWidth().testTag("khatmah-pages"), label = { Text(stringResource(R.string.completed_pages)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item(key = "preview") { Text(if (preview == null) stringResource(R.string.khatmah_invalid) else stringResource(R.string.khatmah_preview, preview.getValue("daily_pages"), preview.getValue("days_remaining"))) }
                item(key = "save") { Button(onClick = save, enabled = actionable && preview != null && plan?.status != "finished", modifier = Modifier.testTag("khatmah-save")) { Text(stringResource(R.string.save)) } }
            }
            if (plan != null) item(key = "actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plan.status != "finished") {
                        OutlinedButton(onClick = { confirmation = if (plan.status == "active") "pause" else "resume" }, enabled = actionable && !state.dirty) { Text(stringResource(if (plan.status == "active") R.string.pause else R.string.khatmah_resume)) }
                        OutlinedButton(onClick = { confirmation = "finish" }, enabled = actionable && !state.dirty && plan.completed == 604) { Text(stringResource(R.string.khatmah_finish)) }
                    }
                    OutlinedButton(onClick = { restart = KhatmahDraft.from(null, state.today); confirmation = "restart" }, enabled = actionable && !state.dirty) { Text(stringResource(R.string.khatmah_restart)) }
                }
            }
        }
        item(key = "reload") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = refresh, enabled = !state.loading && !state.busy) { Text(stringResource(R.string.retry)) }
            if (state.ready) TextButton(onClick = { confirmation = "discard" }, enabled = !state.loading && !state.busy) { Text(stringResource(R.string.khatmah_discard)) }
        } }
    }
    confirmation?.let { pending ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(if (pending == "discard") R.string.khatmah_discard else R.string.khatmah)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when (pending) { "restart" -> R.string.khatmah_restart_confirm; "discard" -> R.string.khatmah_discard_confirm; "finish" -> R.string.khatmah_finish_confirm; else -> R.string.khatmah_action_confirm }))
                if (pending == "restart") {
                    DateField(restart.start, { restart = restart.copy(start = it) }, stringResource(R.string.khatmah_start))
                    DateField(restart.target, { restart = restart.copy(target = it) }, stringResource(R.string.target_date))
                }
            }
        }, confirmButton = { TextButton(onClick = { confirmation = null; if (pending == "discard") discard() else action(pending, if (pending == "restart") restart else null) }, enabled = pending == "discard" || actionable) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
