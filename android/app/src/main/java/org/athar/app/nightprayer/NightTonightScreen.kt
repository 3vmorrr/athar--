package org.athar.app.nightprayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class NightSavedSettings(
    val zone: ZoneId,
    val latitude: Double,
    val longitude: Double,
    val method: String,
    val hanafi: Boolean,
    val highLatitude: String,
)

@Composable
fun NightTonightScreen(
    graph: AppGraph,
    vm: NightViewModel,
    timerVm: NightTimerViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var settings by remember(graph.session.userId) {
        mutableStateOf<NightSavedSettings?>(null)
    }
    var settingsLoading by remember(graph.session.userId) {
        mutableStateOf(true)
    }
    var settingsUnavailable by remember(graph.session.userId) {
        mutableStateOf(false)
    }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(graph.session.userId, retry) {
        settingsLoading = true
        settingsUnavailable = false
        try {
            val result = graph.content.get("night-prayer/preferences")
            val root = result.data.asJsonObject
            val values = root["values"]?.takeUnless { it.isJsonNull }?.asJsonObject
            settings = values?.let(::parseNightSettings)
            settingsUnavailable = settings == null
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            settings = null
            settingsUnavailable = true
        } finally {
            settingsLoading = false
        }
    }

    if (state.loading || settingsLoading) {
        Box(Modifier.fillMaxSize().padding(AtharSpacing.Xl)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if (state.unreadable) {
        Box(Modifier.fillMaxSize().padding(AtharSpacing.Xl)) {
            MessagePanel(
                stringResource(R.string.local_data_unreadable),
                true,
            ) {
                TextButton(onClick = vm::retryLocal) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        return
    }

    val identity = remember(settings, retry) {
        settings?.let { saved ->
            runCatching {
                resolveNightIdentity(
                    now = Instant.now(),
                    zone = saved.zone,
                ) { date ->
                    PrayerCalculator.calculate(
                        date = date,
                        latitude = saved.latitude,
                        longitude = saved.longitude,
                        zone = saved.zone,
                        method = saved.method,
                        hanafi = saved.hanafi,
                        highLatitude = saved.highLatitude,
                    )
                }
            }.getOrNull()
        }
    }
    val tonightDate = identity?.nightDate?.toString()
    val tonightRows = remember(state.rows, tonightDate) {
        if (tonightDate == null) emptyList()
        else state.rows.filter { it.first.nightDate == tonightDate }
    }
    val tonightVerses = remember(tonightRows) {
        tonightRows.sumOf { it.first.verseCount }
    }
    val milestone = NightRules.milestone(tonightVerses)
    val nextMilestone = when {
        tonightVerses < 10 -> 10
        tonightVerses < 100 -> 100
        tonightVerses < 1000 -> 1000
        else -> null
    }
    val recent = remember(state.rows) {
        state.rows
            .groupBy { it.first.nightDate }
            .map { (date, rows) ->
                Triple(
                    date,
                    rows.sumOf { it.first.verseCount },
                    rows.size,
                )
            }
            .sortedByDescending { it.first }
            .take(5)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "tonight-summary", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharSectionHeader(stringResource(R.string.night_tonight))
                    when {
                        identity == null -> {
                            MessagePanel(
                                stringResource(R.string.night_identity_unavailable),
                                settingsUnavailable,
                            ) {
                                TextButton(onClick = { retry++ }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                        else -> {
                            val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                .withZone(identity.context.zone)
                            Text(
                                stringResource(
                                    if (identity.inProgress) {
                                        R.string.night_identity_active
                                    } else {
                                        R.string.night_identity_upcoming
                                    },
                                    identity.nightDate.toString(),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.night_identity_boundaries,
                                    formatter.format(identity.context.maghrib),
                                    formatter.format(identity.context.fajr),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (identity.inProgress) {
                                Text(
                                    stringResource(
                                        R.string.night_identity_last_third,
                                        formatter.format(identity.context.lastThird),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(AtharSpacing.Md),
                                verticalArrangement =
                                    Arrangement.spacedBy(AtharSpacing.Md),
                            ) {
                                AtharMetric(
                                    value = tonightVerses.toString(),
                                    label = stringResource(R.string.night_tonight_verses),
                                    modifier = Modifier.weight(1f)
                                        .widthIn(min = 100.dp),
                                )
                                AtharMetric(
                                    value = tonightRows.size.toString(),
                                    label = stringResource(R.string.night_tonight_sessions),
                                    modifier = Modifier.weight(1f)
                                        .widthIn(min = 100.dp),
                                )
                            }

                            if (milestone > 0) {
                                AtharStatusPill(
                                    stringResource(
                                        R.string.night_reached_milestone,
                                        milestone,
                                    ),
                                )
                            }
                            if (
                                nextMilestone != null &&
                                identity.inProgress
                            ) {
                                Text(
                                    stringResource(
                                        R.string.night_next_reference,
                                        nextMilestone,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "measured-timer", contentType = "timer") {
            NightSessionTimerCard(
                graph = graph,
                nightViewModel = vm,
                timer = timerVm,
                identity = identity,
                calculationMethod = settings?.method,
            )
        }

        if (state.conflicts > 0 || state.rows.any { it.first.dirty }) {
            item(key = "tonight-local-state", contentType = "state") {
                Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    if (state.conflicts > 0) {
                        MessagePanel(
                            stringResource(R.string.night_conflict),
                            true,
                        )
                    }
                    if (state.rows.any { it.first.dirty }) {
                        AtharStatusPill(
                            stringResource(R.string.night_pending),
                            emphasized = true,
                        )
                    }
                }
            }
        }

        item(key = "recent-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_recent_log))
        }

        if (recent.isEmpty()) {
            item(key = "recent-empty", contentType = "empty") {
                QuietEmptyState(stringResource(R.string.night_empty))
            }
        } else {
            items(
                recent,
                key = { it.first },
                contentType = { "night-summary" },
            ) { (date, verses, sessions) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(AtharSpacing.Lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                date,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.night_recent_summary,
                                    verses,
                                    sessions,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val rowMilestone = NightRules.milestone(verses)
                        if (rowMilestone > 0) {
                            AtharStatusPill(
                                stringResource(
                                    R.string.night_milestone_short,
                                    rowMilestone,
                                ),
                            )
                        }
                    }
                }
            }
        }

        item(key = "tonight-note", contentType = "source") {
            Text(
                stringResource(R.string.night_identity_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun parseNightSettings(
    values: com.google.gson.JsonObject,
): NightSavedSettings? = runCatching {
    val zoneName = values["timezone"]?.asString
        ?.takeIf { it.isNotBlank() }
        ?: return@runCatching null
    val latitude = values["latitude"]?.asDouble
        ?: return@runCatching null
    val longitude = values["longitude"]?.asDouble
        ?: return@runCatching null
    val method = values["calculation_method"]?.asString
        ?.takeIf { it in PrayerCalculator.methods }
        ?: return@runCatching null
    val madhab = values["madhab"]?.asString
        ?.takeIf { it in setOf("SHAFI", "HANAFI") }
        ?: return@runCatching null
    val highLatitude = values["high_latitude_rule"]?.asString
        ?.takeIf {
            it in setOf(
                "MIDDLE_OF_THE_NIGHT",
                "SEVENTH_OF_THE_NIGHT",
                "TWILIGHT_ANGLE",
            )
        }
        ?: return@runCatching null

    NightSavedSettings(
        zone = ZoneId.of(zoneName),
        latitude = latitude,
        longitude = longitude,
        method = method,
        hanafi = madhab == "HANAFI",
        highLatitude = highLatitude,
    )
}.getOrNull()
