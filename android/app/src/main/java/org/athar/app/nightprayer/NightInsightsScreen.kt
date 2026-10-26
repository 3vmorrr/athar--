package org.athar.app.nightprayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun NightInsightsScreen(
    graph: AppGraph,
    vm: NightViewModel,
) {
    val owner = requireNotNull(graph.session.userId)
    val state by vm.state.collectAsStateWithLifecycle()
    val preferences = remember(graph.session) {
        NightPreferencesRepository(graph.session)
    }

    var exemptions by remember(owner) {
        mutableStateOf<Set<LocalDate>>(emptySet())
    }
    var exemptionsReady by remember(owner) { mutableStateOf(false) }
    var exemptionsError by remember(owner) { mutableStateOf(false) }
    var identity by remember(owner) { mutableStateOf<NightIdentity?>(null) }
    var identityReady by remember(owner) { mutableStateOf(false) }
    var identityUnavailable by remember(owner) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(owner, retry) {
        exemptionsReady = false
        exemptionsError = false
        try {
            exemptions = vm.exemptions()
            exemptionsReady = true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            exemptionsError = true
        }
    }

    LaunchedEffect(owner, retry) {
        identityReady = false
        identityUnavailable = false
        identity = null
        try {
            val saved = preferences.get(owner).values
            identity = saved?.let { values ->
                runCatching {
                    val zone = ZoneId.of(values.timezone)
                    resolveNightIdentity(
                        now = Instant.now(),
                        zone = zone,
                    ) { date ->
                        PrayerCalculator.calculate(
                            date = date,
                            latitude = values.latitude,
                            longitude = values.longitude,
                            zone = zone,
                            method = values.calculationMethod,
                            hanafi = values.madhab == "HANAFI",
                            highLatitude = values.highLatitudeRule,
                        )
                    }
                }.getOrNull()
            }
            identityUnavailable = identity == null
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            identityUnavailable = true
        } finally {
            identityReady = true
        }
    }

    if (state.loading) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(AtharSpacing.Xl),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if (state.unreadable) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(AtharSpacing.Xl),
        ) {
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

    val totals = remember(state.rows) {
        state.rows
            .groupBy { LocalDate.parse(it.first.nightDate) }
            .mapValues { (_, rows) -> rows.sumOf { it.first.verseCount } }
    }
    val bestVerses = remember(totals) { totals.values.maxOrNull() ?: 0 }
    val bestDates = remember(totals, bestVerses) {
        if (bestVerses <= 0) {
            emptyList()
        } else {
            totals
                .filterValues { it == bestVerses }
                .keys
                .sortedDescending()
        }
    }
    val measuredRows = remember(state.rows) {
        state.rows
            .filter { it.first.measuredSeconds != null && it.first.measuredSeconds!! > 0 }
            .sortedWith(
                compareByDescending<Pair<org.athar.app.core.data.LocalNightSession, com.google.gson.JsonObject>> {
                    it.first.measuredSeconds ?: 0
                }.thenByDescending { it.first.nightDate },
            )
    }
    val longestMeasured = measuredRows.firstOrNull()
    val highestMilestone = NightRules.milestone(bestVerses)
    val nextMilestone = when {
        bestVerses < 10 -> 10
        bestVerses < 100 -> 100
        bestVerses < 1000 -> 1000
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "insights-intro", contentType = "intro") {
            AtharHeroSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.night_insights),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.night_insights_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "insight-records-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_personal_records))
        }

        if (state.rows.isEmpty()) {
            item(key = "insights-empty", contentType = "empty") {
                QuietEmptyState(stringResource(R.string.night_empty))
            }
        } else {
            item(key = "record-best-night", contentType = "record") {
                InsightRecordCard(
                    title = stringResource(R.string.night_record_best_verses),
                    value = bestVerses.toString(),
                    detail = when {
                        bestDates.isEmpty() -> stringResource(R.string.night_record_unavailable)
                        bestDates.size == 1 -> stringResource(
                            R.string.night_record_on_date,
                            bestDates.first().toString(),
                        )
                        else -> stringResource(
                            R.string.night_record_tied_dates,
                            bestDates.size,
                            bestDates.first().toString(),
                        )
                    },
                )
            }

            item(key = "record-longest-measured", contentType = "record") {
                InsightRecordCard(
                    title = stringResource(R.string.night_record_longest_measured),
                    value = longestMeasured
                        ?.first
                        ?.measuredSeconds
                        ?.let(::formatInsightDuration)
                        ?: "—",
                    detail = longestMeasured?.first?.nightDate?.let {
                        stringResource(R.string.night_record_on_date, it)
                    } ?: stringResource(R.string.night_record_no_measured),
                )
            }

            item(key = "record-milestone", contentType = "record") {
                InsightRecordCard(
                    title = stringResource(R.string.night_record_reference_level),
                    value = if (highestMilestone > 0) {
                        highestMilestone.toString()
                    } else {
                        "—"
                    },
                    detail = if (nextMilestone != null) {
                        stringResource(
                            R.string.night_record_next_reference,
                            nextMilestone,
                        )
                    } else {
                        stringResource(R.string.night_record_highest_reference)
                    },
                )
            }
        }

        item(key = "streak-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_consistency))
            Text(
                stringResource(R.string.night_streak_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            exemptionsError -> {
                item(key = "streak-exemption-error", contentType = "state") {
                    MessagePanel(
                        stringResource(R.string.local_data_unreadable),
                        true,
                    ) {
                        TextButton(onClick = { retry++ }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            !exemptionsReady -> {
                item(key = "streak-loading", contentType = "loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            else -> {
                val thresholds = listOf(1, 10, 100, 1000)
                thresholds.forEach { threshold ->
                    val streak = NightRules.streakInsight(
                        totals = totals,
                        threshold = threshold,
                        current = identity?.nightDate,
                        nightInProgress = identity?.inProgress == true,
                        exemptions = exemptions,
                    )
                    item(
                        key = "streak-$threshold",
                        contentType = "streak",
                    ) {
                        StreakInsightCard(
                            threshold = threshold,
                            current = streak.current,
                            longest = streak.longest,
                            qualifyingNights = streak.qualifyingNights,
                            currentUnavailable = identityUnavailable || !identityReady,
                        )
                    }
                }
            }
        }

        if (identityUnavailable && identityReady) {
            item(key = "current-streak-unavailable", contentType = "state") {
                MessagePanel(
                    stringResource(R.string.night_current_streak_unavailable),
                )
            }
        }

        item(key = "insight-source", contentType = "source") {
            Text(
                stringResource(R.string.night_insights_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightRecordCard(
    title: String,
    value: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(AtharSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun StreakInsightCard(
    threshold: Int,
    current: Int?,
    longest: Int,
    qualifyingNights: Int,
    currentUnavailable: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(
                stringResource(R.string.night_streak_threshold, threshold),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                AtharMetric(
                    value = if (currentUnavailable || current == null) {
                        "—"
                    } else {
                        current.toString()
                    },
                    label = stringResource(R.string.night_streak_current),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
                AtharMetric(
                    value = longest.toString(),
                    label = stringResource(R.string.night_streak_longest),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
                AtharMetric(
                    value = qualifyingNights.toString(),
                    label = stringResource(R.string.night_streak_qualifying),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
            }
        }
    }
}

private fun formatInsightDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remainder = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%02d:%02d".format(minutes, remainder)
    }
}
