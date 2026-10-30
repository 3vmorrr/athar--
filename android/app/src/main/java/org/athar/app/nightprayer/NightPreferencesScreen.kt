package org.athar.app.nightprayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.SessionLease
import org.athar.app.core.ui.*

internal enum class NightPreferenceField { CITY, TIMEZONE, LATITUDE, LONGITUDE, METHOD, MADHAB, HIGH_LATITUDE }
internal sealed interface NightPreferencesEvent {
    data object Back : NightPreferencesEvent
    data object Refresh : NightPreferencesEvent
    data object Save : NightPreferencesEvent
    data object RetrySave : NightPreferencesEvent
    data object LoadDefaults : NightPreferencesEvent
    data object ApplyDefaults : NightPreferencesEvent
    data class Edit(val field: NightPreferenceField, val value: String) : NightPreferencesEvent
    data class Reload(val formVersion: Long) : NightPreferencesEvent
    data class UseAccountCopy(val proof: NightPreferenceRepair) : NightPreferencesEvent
}

@Composable internal fun NightPreferencesScreen(graph: AppGraph, back: () -> Unit) {
    val actor by graph.session.state.collectAsStateWithLifecycle()
    val owner = (actor as? org.athar.app.auth.AuthState.SignedIn)?.user?.id ?: return
    val lease = runCatching { graph.session.lease(owner) }.getOrNull() ?: return
    key(lease) { NightPreferencesOwned(graph, lease, back) }
}
@Composable private fun NightPreferencesOwned(graph: AppGraph, lease: SessionLease, back: () -> Unit) {
    val vm: NightPreferencesViewModel = viewModel(key = "night-preferences:${graph.partition}:${lease.owner}:${lease.generation}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NightPreferencesViewModel(lease.owner, graph.partition, object : NightPreferencesGateway {
            override fun requireActive() = graph.session.requireLease(lease)
            override suspend fun load() = graph.nightPreferences.load(lease)
            override suspend fun save(reviewed: NightPreferencesEnvelope, wanted: NightPreferenceValues) = graph.nightPreferences.save(lease, reviewed, wanted)
            override suspend fun repair(reviewed: NightPreferenceRepair) = graph.nightPreferences.useAccountCopy(lease, reviewed)
            override suspend fun publicDefaults() = graph.nightPreferences.publicDefaults(lease)
        }) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lease) { graph.session.state.collect { if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate() } }
    DisposableEffect(vm, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = back)
    if (runCatching { graph.session.requireLease(lease) }.isFailure) return
    NightPreferencesContent(state) { event ->
        if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate()
        else when (event) {
            NightPreferencesEvent.Back -> back()
            NightPreferencesEvent.Refresh -> vm.refresh()
            NightPreferencesEvent.Save -> vm.save()
            NightPreferencesEvent.RetrySave -> vm.retrySave()
            NightPreferencesEvent.LoadDefaults -> vm.loadDefaults()
            NightPreferencesEvent.ApplyDefaults -> vm.applyDefaults()
            is NightPreferencesEvent.Reload -> vm.reloadForm(event.formVersion)
            is NightPreferencesEvent.UseAccountCopy -> vm.useAccountCopy(event.proof)
            is NightPreferencesEvent.Edit -> {
                // Merge against the current draft, not a callback's older composition snapshot.
                val draft = vm.state.value.draft
                vm.edit(when (event.field) {
                    NightPreferenceField.CITY -> draft.copy(city = event.value)
                    NightPreferenceField.TIMEZONE -> draft.copy(timezone = event.value)
                    NightPreferenceField.LATITUDE -> draft.copy(latitude = event.value)
                    NightPreferenceField.LONGITUDE -> draft.copy(longitude = event.value)
                    NightPreferenceField.METHOD -> draft.copy(method = event.value)
                    NightPreferenceField.MADHAB -> draft.copy(madhab = event.value)
                    NightPreferenceField.HIGH_LATITUDE -> draft.copy(highLatitude = event.value)
                })
            }
        }
    }
}

@Composable internal fun NightPreferencesContent(state: NightPreferencesUiState, modifier: Modifier = Modifier, event: (NightPreferencesEvent) -> Unit) {
    val list = remember { LazyListState() }
    var reloadVersion by remember { mutableStateOf<Long?>(null) }
    var repair by remember { mutableStateOf<NightPreferenceRepair?>(null) }
    val editable = state.load != null && state.load.repair == null
    val draft = state.draft
    LazyColumn(modifier.fillMaxSize().testTag("night-preferences"), state = list, contentPadding = PaddingValues(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
        item(key = "identity", contentType = "heading") {
            TextButton(onClick = { event(NightPreferencesEvent.Back) }) { Text(stringResource(R.string.back)) }
            AtharSectionHeader(stringResource(R.string.night_preferences_title))
            Text(stringResource(R.string.night_preferences_manual_notice))
        }
        if (state.loading || state.busy) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item(key = "error") { MessagePanel(stringResource(if (error == NightPreferenceError.UNREADABLE) R.string.night_preferences_unreadable else R.string.account_data_unavailable), true) {
            TextButton(onClick = { event(NightPreferencesEvent.Refresh) }, enabled = !state.busy && !state.loading) { Text(stringResource(R.string.retry)) }
        } } }
        state.load?.let { load ->
            if (load.offline || load.newerCachedRevision) item(key = "offline") { MessagePanel(stringResource(R.string.night_preferences_cached_copy)) }
            if (load.repair != null) item(key = "repair") { MessagePanel(stringResource(R.string.night_preferences_copy_review), true) {
                OutlinedButton(onClick = { repair = load.repair }, enabled = !state.busy && !state.loading, modifier = Modifier.testTag("preferences-review-copy")) { Text(stringResource(R.string.night_preferences_review_account)) }
            } }
            if (load.repair == null && state.expected != null && state.expected != load.snapshot.account) item(key = "loaded-copy", contentType = "review") {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                    AtharSectionHeader(stringResource(R.string.night_preferences_loaded_copy)); PreferenceSummary(load.snapshot.account.values)
                } }
            }
            if (load.repair == null && load.snapshot.account.values == NightPreferenceValues() && state.error == null) item(key = "unset") {
                Text(stringResource(R.string.night_preferences_unset), modifier = Modifier.testTag("preferences-unset"))
            }
        }
        state.feedback?.let { feedback -> item(key = "feedback") { MessagePanel(stringResource(preferenceFeedback(feedback)), feedback in setOf(NightPreferenceFeedback.CHANGED, NightPreferenceFeedback.INVALID, NightPreferenceFeedback.UNCONFIRMED)) } }
        state.pending?.let { pending -> item(key = "pending", contentType = "review") { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            Text(stringResource(R.string.night_preferences_pending_notice))
            PreferenceSummary(pending.wanted)
            Button(onClick = { event(NightPreferencesEvent.RetrySave) }, enabled = !state.busy && !state.loading, modifier = Modifier.testTag("preferences-retry-save")) { Text(stringResource(R.string.retry)) }
        } } } }
        item(key = "city", contentType = "field") { PreferenceInput(R.string.night_preferences_city, draft.city, editable, "preferences-city") { event(NightPreferencesEvent.Edit(NightPreferenceField.CITY, it)) } }
        item(key = "timezone", contentType = "field") { PreferenceInput(R.string.timezone, draft.timezone, editable, "preferences-timezone") { event(NightPreferencesEvent.Edit(NightPreferenceField.TIMEZONE, it)) } }
        item(key = "latitude", contentType = "field") { PreferenceInput(R.string.latitude, draft.latitude, editable, "preferences-latitude", true) { event(NightPreferencesEvent.Edit(NightPreferenceField.LATITUDE, it)) } }
        item(key = "longitude", contentType = "field") { PreferenceInput(R.string.longitude, draft.longitude, editable, "preferences-longitude", true) { event(NightPreferencesEvent.Edit(NightPreferenceField.LONGITUDE, it)) } }
        item(key = "method", contentType = "choice") { PreferenceChoices(R.string.night_preferences_method, draft.method, PreferenceMethods, editable) { event(NightPreferencesEvent.Edit(NightPreferenceField.METHOD, it)) } }
        item(key = "school", contentType = "choice") { PreferenceChoices(R.string.night_preferences_school, draft.madhab, PreferenceSchools, editable) { event(NightPreferencesEvent.Edit(NightPreferenceField.MADHAB, it)) } }
        item(key = "latitude-rule", contentType = "choice") { PreferenceChoices(R.string.night_preferences_high_latitude, draft.highLatitude, PreferenceHighLatitudes, editable) { event(NightPreferencesEvent.Edit(NightPreferenceField.HIGH_LATITUDE, it)) } }
        item(key = "save", contentType = "action") { FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
            Button(onClick = { event(NightPreferencesEvent.Save) }, enabled = state.canSave, modifier = Modifier.testTag("preferences-save")) { Text(stringResource(R.string.save)) }
            TextButton(onClick = { event(NightPreferencesEvent.Refresh) }, enabled = !state.busy && !state.loading) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = { reloadVersion = state.formVersion }, enabled = state.load != null && !state.busy && !state.loading, modifier = Modifier.testTag("preferences-reload")) { Text(stringResource(R.string.night_preferences_reload)) }
        } }
        item(key = "public-defaults", contentType = "defaults") { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            AtharSectionHeader(stringResource(R.string.night_preferences_public_choices))
            Text(stringResource(R.string.night_preferences_public_notice))
            if (state.publicLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.publicFailed) Text(stringResource(R.string.night_preferences_public_unavailable), color = MaterialTheme.colorScheme.error)
            state.publicDefaults?.let { defaults ->
                val language = LocalConfiguration.current.locales[0].language
                Text(if (language == "ar") defaults.sourceArabic else defaults.sourceEnglish)
                Text(stringResource(PreferenceMethods.first { it.first == defaults.method }.second))
                OutlinedButton(onClick = { event(NightPreferencesEvent.ApplyDefaults) }, enabled = defaults.enabled && editable && !state.publicLoading && !state.publicFailed, modifier = Modifier.testTag("preferences-apply-defaults")) { Text(stringResource(R.string.night_preferences_apply_choices)) }
            }
            TextButton(onClick = { event(NightPreferencesEvent.LoadDefaults) }, enabled = !state.publicLoading, modifier = Modifier.testTag("preferences-load-defaults")) { Text(stringResource(R.string.night_preferences_load_choices)) }
        } } }
    }
    reloadVersion?.let { version -> AlertDialog(onDismissRequest = { reloadVersion = null }, title = { Text(stringResource(R.string.night_preferences_reload)) },
        text = { Text(stringResource(R.string.night_preferences_reload_confirm)) }, confirmButton = { TextButton(onClick = { reloadVersion = null; event(NightPreferencesEvent.Reload(version)) }, enabled = !state.busy && !state.loading, modifier = Modifier.testTag("preferences-reload-confirm")) { Text(stringResource(R.string.night_preferences_reload)) } },
        dismissButton = { TextButton(onClick = { reloadVersion = null }) { Text(stringResource(R.string.cancel)) } }) }
    repair?.let { proof -> AlertDialog(onDismissRequest = { repair = null }, title = { Text(stringResource(R.string.night_preferences_review_account)) }, text = {
        Column(Modifier.verticalScroll(remember { ScrollState(0) }), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            Text(stringResource(R.string.night_preferences_use_account_confirm)); PreferenceSummary(proof.account.values)
        }
    }, confirmButton = { TextButton(onClick = { repair = null; event(NightPreferencesEvent.UseAccountCopy(proof)) }, enabled = !state.busy && !state.loading, modifier = Modifier.testTag("preferences-copy-confirm")) { Text(stringResource(R.string.night_preferences_use_account)) } },
        dismissButton = { TextButton(onClick = { repair = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable private fun PreferenceInput(label: Int, value: String, enabled: Boolean, tag: String, decimal: Boolean = false, edit: (String) -> Unit) {
    OutlinedTextField(value, edit, Modifier.fillMaxWidth().testTag(tag), enabled = enabled, label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun PreferenceChoices(label: Int, selected: String, options: List<Pair<String, Int>>, enabled: Boolean, choose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        if (selected.isNotEmpty() && options.none { it.first == selected }) Text(stringResource(R.string.night_preferences_unsupported, selected), color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) { options.forEach { (key, resource) ->
            FilterChip(selected == key, { choose(key) }, enabled = enabled, label = { Text(stringResource(resource)) })
        } }
    }
}
@Composable private fun PreferenceSummary(values: NightPreferenceValues) {
    val unset = stringResource(R.string.night_preferences_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
        Text("${stringResource(R.string.night_preferences_city)}: ${values.city?.takeIf { it.isNotEmpty() } ?: unset}")
        Text("${stringResource(R.string.timezone)}: ${values.timezone ?: unset}")
        Text("${stringResource(R.string.latitude)}: ${values.latitude?.toString() ?: unset}")
        Text("${stringResource(R.string.longitude)}: ${values.longitude?.toString() ?: unset}")
        listOf(R.string.night_preferences_method to (values.calculationMethod to PreferenceMethods), R.string.night_preferences_school to (values.madhab to PreferenceSchools),
            R.string.night_preferences_high_latitude to (values.highLatitudeRule to PreferenceHighLatitudes)).forEach { (label, choice) ->
            val resource = choice.second.firstOrNull { it.first == choice.first }?.second
            Text("${stringResource(label)}: ${if (resource != null) stringResource(resource) else choice.first ?: unset}")
        }
    }
}
private fun preferenceFeedback(value: NightPreferenceFeedback) = when (value) {
    NightPreferenceFeedback.SAVED -> R.string.night_preferences_saved
    NightPreferenceFeedback.SAVED_WITH_DRAFT -> R.string.night_preferences_saved_with_draft
    NightPreferenceFeedback.CHANGED -> R.string.night_preferences_changed
    NightPreferenceFeedback.INVALID -> R.string.invalid_input
    NightPreferenceFeedback.UNCONFIRMED -> R.string.night_preferences_unconfirmed
    NightPreferenceFeedback.DRAFT_KEPT -> R.string.night_preferences_draft_kept
    NightPreferenceFeedback.RESTORED -> R.string.night_preferences_restored
}
private val PreferenceMethods = listOf("EGYPTIAN" to R.string.method_egyptian, "MUSLIM_WORLD_LEAGUE" to R.string.method_mwl,
    "UMM_AL_QURA" to R.string.method_umm_al_qura, "KARACHI" to R.string.method_karachi, "DUBAI" to R.string.method_dubai, "MOON_SIGHTING_COMMITTEE" to R.string.method_moonsighting)
private val PreferenceSchools = listOf("SHAFI" to R.string.night_school_standard, "HANAFI" to R.string.hanafi_asr)
private val PreferenceHighLatitudes = listOf("MIDDLE_OF_THE_NIGHT" to R.string.rule_middle, "SEVENTH_OF_THE_NIGHT" to R.string.rule_seventh, "TWILIGHT_ANGLE" to R.string.rule_angle)
