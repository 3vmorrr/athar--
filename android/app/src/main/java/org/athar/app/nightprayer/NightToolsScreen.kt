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
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.MessagePanel
import org.athar.app.core.ui.DateField
import org.athar.app.core.network.ApiException
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun NightToolsScreen(graph: AppGraph) {
    key(graph.session.userId){NightToolsContent(graph)}
}
@Composable private fun NightToolsContent(graph:AppGraph) {
    val owner=remember{requireNotNull(graph.session.userId)}
    var fromSurah by remember { mutableStateOf("1") }; var fromAyah by remember { mutableStateOf("1") }
    var toSurah by remember { mutableStateOf("1") }; var toAyah by remember { mutableStateOf("7") }
    var count by remember { mutableStateOf("100") }; var byTarget by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var city by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }; var longitude by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(NightRules.serverTimezone(ZoneId.systemDefault())) }
    var method by remember { mutableStateOf("EGYPTIAN") }; var hanafi by remember { mutableStateOf(false) }
    var highLatitude by remember { mutableStateOf("MIDDLE_OF_THE_NIGHT") }
    var night by remember { mutableStateOf<NightContext?>(null) }; var timeError by remember { mutableStateOf(false) }
    val preferences = remember(graph.session) {
        NightPreferencesRepository(graph.session)
    }
    var preferenceRevision by remember { mutableIntStateOf(0) }
    var preferenceAttempt by remember { mutableIntStateOf(0) }
    var preferenceReady by remember { mutableStateOf(false) }
    var preferenceUnavailable by remember { mutableStateOf(false) }
    var preferenceConflict by remember { mutableStateOf(false) }
    var preferenceSaved by remember { mutableStateOf(false) }
    var preferenceFormError by remember { mutableStateOf(false) }
    var exemptStart by remember { mutableStateOf("") }; var exemptEnd by remember { mutableStateOf("") }; var exemptTotal by remember { mutableIntStateOf(0) }
    var exemptAttempt by remember{mutableIntStateOf(0)}
    var exemptReady by remember{mutableStateOf(false)}
    var exemptUnreadable by remember{mutableStateOf(false)}
    var exemptFormError by remember{mutableStateOf(false)}
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(owner, preferenceAttempt) {
        preferenceReady = false
        preferenceUnavailable = false
        preferenceConflict = false
        preferenceSaved = false
        try {
            val envelope = preferences.get(owner)
            preferenceRevision = envelope.revision
            envelope.values?.let { saved ->
                city = saved.city
                latitude = saved.latitude.toString()
                longitude = saved.longitude.toString()
                zone = saved.timezone
                method = saved.calculationMethod
                hanafi = saved.madhab == "HANAFI"
                highLatitude = saved.highLatitudeRule
            }
            preferenceReady = true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            preferenceUnavailable = true
        }
    }
    LaunchedEffect(graph.session.userId,exemptAttempt) {
        exemptReady=false;exemptUnreadable=false
        try{exemptTotal=graph.nightPrayer.localExemptions(owner).size;exemptReady=true}
        catch(failure:CancellationException){throw failure}
        catch(_:Exception){exemptUnreadable=true}
    }
    LazyColumn(modifier=Modifier.testTag("night-tools-list"),contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            when {
                preferenceUnavailable -> MessagePanel(
                    stringResource(R.string.night_preferences_unavailable),
                    true,
                ) {
                    TextButton(onClick = { preferenceAttempt++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                !preferenceReady -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (preferenceConflict) {
                MessagePanel(
                    stringResource(R.string.night_preferences_conflict),
                    true,
                ) {
                    TextButton(onClick = { preferenceAttempt++ }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            if (preferenceSaved) {
                MessagePanel(stringResource(R.string.night_preferences_saved))
            }
            if (preferenceFormError) {
                MessagePanel(stringResource(R.string.invalid_input), true)
            }

            Text(
                stringResource(R.string.night_preferences_manual_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                city,
                {
                    city = it.take(80)
                    preferenceSaved = false
                    preferenceFormError = false
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.city)) },
                singleLine = true,
            )
            DateField(date, { date = it; night = null }, stringResource(R.string.night_date))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    latitude,
                    {
                        latitude = it.take(15)
                        night = null
                        preferenceSaved = false
                    },
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.latitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    longitude,
                    {
                        longitude = it.take(15)
                        night = null
                        preferenceSaved = false
                    },
                    Modifier.weight(1f),
                    label = { Text(stringResource(R.string.longitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            OutlinedTextField(
                zone,
                {
                    zone = it.take(80)
                    night = null
                    preferenceSaved = false
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.timezone)) },
                singleLine = true,
            )
            MethodSelector(
                method,
                {
                    method = it
                    night = null
                    preferenceSaved = false
                },
                PrayerCalculator.methods,
            )
            Row {
                Checkbox(
                    hanafi,
                    {
                        hanafi = it
                        night = null
                        preferenceSaved = false
                    },
                )
                Text(stringResource(R.string.hanafi_asr), Modifier.padding(top = 12.dp))
            }
            MethodSelector(
                highLatitude,
                {
                    highLatitude = it
                    night = null
                    preferenceSaved = false
                },
                listOf(
                    "MIDDLE_OF_THE_NIGHT",
                    "SEVENTH_OF_THE_NIGHT",
                    "TWILIGHT_ANGLE",
                ),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(enabled = !busy, onClick = { busy = true; scope.launch {
                    try {
                        night = withContext(Dispatchers.Default) {
                            PrayerCalculator.calculate(
                                LocalDate.parse(date),
                                latitude.toDouble(),
                                longitude.toDouble(),
                                ZoneId.of(zone),
                                method,
                                hanafi,
                                highLatitude,
                            )
                        }
                        timeError = false
                    }
                    catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { timeError = true; night = null }
                    finally { busy = false }
                } }) {
                    Text(stringResource(R.string.calculate))
                }

                OutlinedButton(
                    enabled = !busy && preferenceReady,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val normalizedZone = NightRules.serverTimezone(
                                    ZoneId.of(zone),
                                )
                                val values = NightPrayerPreferences(
                                    city = city.trim(),
                                    timezone = normalizedZone,
                                    latitude = latitude.toDouble(),
                                    longitude = longitude.toDouble(),
                                    calculationMethod = method,
                                    madhab = if (hanafi) "HANAFI" else "SHAFI",
                                    highLatitudeRule = highLatitude,
                                )
                                val saved = preferences.save(
                                    values = values,
                                    expectedRevision = preferenceRevision,
                                    expectedOwner = owner,
                                )
                                preferenceRevision = saved.revision
                                preferenceSaved = true
                                preferenceConflict = false
                                preferenceFormError = false
                                preferenceUnavailable = false
                                zone = normalizedZone
                                graph.content.invalidate(
                                    "night-prayer/preferences",
                                    owner,
                                )
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: ApiException) {
                                if (failure.status == 409) {
                                    preferenceConflict = true
                                    preferenceSaved = false
                                } else {
                                    preferenceUnavailable = true
                                }
                            } catch (_: IllegalArgumentException) {
                                preferenceFormError = true
                            } catch (_: Exception) {
                                preferenceUnavailable = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.night_preferences_save))
                }
            }

            if (timeError) {
                MessagePanel(stringResource(R.string.prayer_calculation_failed), true)
            }
            night?.let { context ->
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
                    .withZone(context.zone)
                MessagePanel(
                    stringResource(
                        R.string.night_boundaries,
                        formatter.format(context.maghrib),
                        formatter.format(context.lastThird),
                        formatter.format(context.fajr),
                    ),
                )
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
@Composable private fun NumericInput(value: String, change: (String) -> Unit, label: Int, modifier: Modifier) { OutlinedTextField(value, { change(it.filter(Char::isDigit).take(6)) }, modifier, label = { Text(stringResource(label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
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
