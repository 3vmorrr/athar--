package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.athar.app.auth.SessionLease
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.MessagePanel
import org.athar.app.core.ui.DateField
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun NightToolsScreen(graph: AppGraph, preferences: (() -> Unit)? = null) {
    val actor by graph.session.state.collectAsStateWithLifecycle()
    val owner = (actor as? org.athar.app.auth.AuthState.SignedIn)?.user?.id ?: return
    val lease = runCatching { graph.session.lease(owner) }.getOrNull() ?: return
    key(lease){NightToolsContent(graph, lease, preferences)}
}
@Composable private fun NightToolsContent(graph: AppGraph, lease: SessionLease, preferences: (() -> Unit)?) {
    val owner = lease.owner
    var fromSurah by remember { mutableStateOf("1") }; var fromAyah by remember { mutableStateOf("1") }
    var toSurah by remember { mutableStateOf("1") }; var toAyah by remember { mutableStateOf("7") }
    var count by remember { mutableStateOf("100") }; var byTarget by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(NightRules.serverTimezone(ZoneId.systemDefault())) }
    var method by remember { mutableStateOf("EGYPTIAN") }; var hanafi by remember { mutableStateOf(false) }
    var highLatitude by remember { mutableStateOf("MIDDLE_OF_THE_NIGHT") }
    var timeVersion by remember { mutableLongStateOf(0) }
    var savedCity by remember { mutableStateOf<String?>(null) }
    var savedUnavailable by remember { mutableStateOf(false) }
    var savedChanged by remember { mutableStateOf(false) }
    var night by remember { mutableStateOf<NightContext?>(null) }; var timeError by remember { mutableStateOf(false) }
    var exemptStart by remember { mutableStateOf("") }; var exemptEnd by remember { mutableStateOf("") }; var exemptTotal by remember { mutableIntStateOf(0) }
    var exemptAttempt by remember{mutableIntStateOf(0)}
    var exemptReady by remember{mutableStateOf(false)}
    var exemptUnreadable by remember{mutableStateOf(false)}
    var exemptFormError by remember{mutableStateOf(false)}
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun timeEdited() { timeVersion++; night = null; savedCity = null; savedChanged = false }
    LaunchedEffect(graph.session.userId,exemptAttempt) {
        exemptReady=false;exemptUnreadable=false
        try{graph.session.requireLease(lease);val days=graph.nightPrayer.localExemptions(owner);graph.session.requireLease(lease);exemptTotal=days.size;exemptReady=true}
        catch(failure:CancellationException){throw failure}
        catch(_:Exception){exemptUnreadable=true}
    }
    if (runCatching { graph.session.requireLease(lease) }.isFailure) return
    LazyColumn(modifier=Modifier.testTag("night-tools-list"),contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (preferences != null) item(key = "saved-preferences", contentType = "action") { OutlinedButton(onClick = preferences) { Text(stringResource(R.string.night_preferences_title)) } }
        item(key = "verse-heading", contentType = "heading") { Text(stringResource(R.string.verse_calculator), style = MaterialTheme.typography.headlineSmall) }
        item(key = "verse-form", contentType = "form") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(byTarget, { byTarget = true; result = null }, label = { Text(stringResource(R.string.target_count)) }); FilterChip(!byTarget, { byTarget = false; result = null }, label = { Text(stringResource(R.string.night_range)) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumericInput(fromSurah, { fromSurah = it; result = null }, R.string.start_surah, Modifier.weight(1f)); NumericInput(fromAyah, { fromAyah = it; result = null }, R.string.start_ayah, Modifier.weight(1f)) }
            if (byTarget) NumericInput(count, { count = it; result = null }, R.string.target_count, Modifier.fillMaxWidth())
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumericInput(toSurah, { toSurah = it; result = null }, R.string.end_surah, Modifier.weight(1f)); NumericInput(toAyah, { toAyah = it; result = null }, R.string.end_ayah, Modifier.weight(1f)) }
            Button(onClick = { scope.launch {
                try { val index = graph.quran.data().index; val selected = if (byTarget) index.target(fromSurah.toInt(), fromAyah.toInt(), count.toInt()) else index.range(fromSurah.toInt(), fromAyah.toInt(), toSurah.toInt(), toAyah.toInt()); result = "${selected.start.surah}:${selected.start.ayah} — ${selected.end.surah}:${selected.end.ayah} · ${selected.verseCount}"; error = false }
                catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true; result = null }
            } }) { Text(stringResource(R.string.calculate)) }
            if (result != null) MessagePanel(result!!)
            if (error) Text(stringResource(R.string.range_error), color = MaterialTheme.colorScheme.error)
        } }
        item(key = "time-heading", contentType = "heading") { HorizontalDivider(); Text(stringResource(R.string.last_third), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.prayer_calculation_notice)) }
        item(key = "time-form", contentType = "form") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !busy, modifier = Modifier.testTag("night-use-saved-calculation"), onClick = {
                val reviewedVersion = timeVersion
                busy = true; savedUnavailable = false; savedChanged = false
                scope.launch {
                    try {
                        val result = graph.nightPreferences.load(lease); graph.session.requireLease(lease)
                        requireNotNull(result.calculationSettings())
                        val values = result.snapshot.account.values
                        if (timeVersion != reviewedVersion) savedChanged = true
                        else {
                            latitude = requireNotNull(values.latitude).toString(); longitude = requireNotNull(values.longitude).toString()
                            zone = requireNotNull(values.timezone); method = requireNotNull(values.calculationMethod)
                            hanafi = values.madhab == "HANAFI"; highLatitude = requireNotNull(values.highLatitudeRule)
                            timeVersion++; night = null; timeError = false
                            savedCity = values.city?.takeIf { it.isNotBlank() } ?: zone
                        }
                    } catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { savedUnavailable = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.night_preferences_use_saved_calculator)) }
            if (savedUnavailable) MessagePanel(stringResource(R.string.night_preferences_calculator_unavailable), true)
            if (savedChanged) MessagePanel(stringResource(R.string.night_preferences_calculator_changed), true)
            savedCity?.let { Text(stringResource(R.string.night_preferences_calculator_applied, it)) }
            DateField(date, { date = it; timeEdited() }, stringResource(R.string.night_date))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(latitude, { latitude = it.take(15); timeEdited() }, Modifier.weight(1f), label = { Text(stringResource(R.string.latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(longitude, { longitude = it.take(15); timeEdited() }, Modifier.weight(1f), label = { Text(stringResource(R.string.longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            OutlinedTextField(zone, { zone = it.take(80); timeEdited() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.timezone)) }, singleLine = true)
            MethodSelector(method, { method = it; timeEdited() }, PrayerCalculator.methods)
            Row { Checkbox(hanafi, { hanafi = it; timeEdited() }); Text(stringResource(R.string.hanafi_asr), Modifier.padding(top = 12.dp)) }
            MethodSelector(highLatitude, { highLatitude = it; timeEdited() }, listOf("MIDDLE_OF_THE_NIGHT", "SEVENTH_OF_THE_NIGHT", "TWILIGHT_ANGLE"))
            Button(enabled = !busy, onClick = {
                val reviewedVersion = timeVersion
                val selectedDate = date; val selectedLatitude = latitude; val selectedLongitude = longitude; val selectedZone = zone
                val selectedMethod = method; val selectedHanafi = hanafi; val selectedHighLatitude = highLatitude
                busy = true; scope.launch {
                    try {
                        graph.session.requireLease(lease)
                        val calculated = withContext(Dispatchers.Default) { PrayerCalculator.calculate(LocalDate.parse(selectedDate), NightPreferenceDraft.numeric(selectedLatitude).toDouble(), NightPreferenceDraft.numeric(selectedLongitude).toDouble(), ZoneId.of(selectedZone), selectedMethod, selectedHanafi, selectedHighLatitude) }
                        graph.session.requireLease(lease)
                        if (timeVersion == reviewedVersion) { night = calculated; timeError = false }
                    } catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { if (timeVersion == reviewedVersion) { timeError = true; night = null } }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.calculate)) }
            if (timeError) MessagePanel(stringResource(R.string.prayer_calculation_failed), true)
            night?.let { context ->
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(context.zone)
                MessagePanel(stringResource(R.string.night_boundaries, formatter.format(context.maghrib), formatter.format(context.lastThird), formatter.format(context.fajr)))
            }
        } }
        item(key = "exemption-heading", contentType = "heading") { HorizontalDivider(); Text(stringResource(R.string.local_exemptions), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.exemptions_notice)) }
        item(key = "exemption-form", contentType = "form") { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(exemptReady)Text(stringResource(R.string.exemption_days, exemptTotal))
            else if(exemptUnreadable)MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick={exemptAttempt++}){Text(stringResource(R.string.retry))}}
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            if(exemptFormError)MessagePanel(stringResource(R.string.invalid_input),true)
            DateField(exemptStart, { exemptStart = it }, stringResource(R.string.start_date), latest = LocalDate.now())
            DateField(exemptEnd, { exemptEnd = it }, stringResource(R.string.end_date), latest = LocalDate.now())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=exemptReady,onClick = { scope.launch {
                    try {
                        val start = LocalDate.parse(exemptStart); val end = LocalDate.parse(exemptEnd)
                        require(!end.isBefore(start) && end <= LocalDate.now() && java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 365)
                        try {
                            val values = graph.nightPrayer.localExemptions(owner).toMutableSet()
                            var day=start;while(day<=end){values.add(day);day=day.plusDays(1)}
                            graph.nightPrayer.localExemptions(values,owner);exemptTotal=values.size;exemptFormError=false
                        }catch(failure:CancellationException){throw failure}
                        catch(_:Exception){exemptUnreadable=true;exemptReady=false}
                    }
                    catch (failure: CancellationException) { throw failure } catch (_: Exception) { exemptFormError = true }
                } }) { Text(stringResource(R.string.save)) }
                TextButton(enabled=exemptReady,onClick = { scope.launch { try{graph.nightPrayer.localExemptions(emptySet(),owner); exemptTotal = 0;exemptFormError=false}catch(failure:CancellationException){throw failure}catch(_:Exception){exemptUnreadable=true;exemptReady=false} } }) { Text(stringResource(R.string.clear_local_exemptions)) }
            }
        } }
    }
}
@Composable private fun NumericInput(value: String, change: (String) -> Unit, label: Int, modifier: Modifier) { OutlinedTextField(value, { change(NightPlanDraft.numberInput(it).take(6)) }, modifier, label = { Text(stringResource(label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
@Composable private fun MethodSelector(value: String, change: (String) -> Unit, values: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }) { Text(methodLabel(value)) }; DropdownMenu(expanded, { expanded = false }) { values.forEach { method -> DropdownMenuItem(text = { Text(methodLabel(method)) }, onClick = { change(method); expanded = false }) } } }
}
@Composable private fun methodLabel(value: String): String = stringResource(when(value) {
    "EGYPTIAN" -> R.string.method_egyptian
    "MUSLIM_WORLD_LEAGUE" -> R.string.method_mwl
    "UMM_AL_QURA" -> R.string.method_umm_al_qura
    "KARACHI" -> R.string.method_karachi
    "DUBAI" -> R.string.method_dubai
    "MOON_SIGHTING_COMMITTEE" -> R.string.method_moonsighting
    "MIDDLE_OF_THE_NIGHT" -> R.string.rule_middle
    "SEVENTH_OF_THE_NIGHT" -> R.string.rule_seventh
    else -> R.string.rule_angle
})
