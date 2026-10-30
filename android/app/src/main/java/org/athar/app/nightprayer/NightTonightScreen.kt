package org.athar.app.nightprayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.athar.app.auth.SessionLease
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.*
import org.athar.app.core.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

internal data class NightSavedSettings(val zone: ZoneId, val latitude: Double, val longitude: Double, val method: String, val hanafi: Boolean, val highLatitude: String) {
    fun identity(now: Instant) = resolveNightIdentity(now, zone) { date -> PrayerCalculator.calculate(date, latitude, longitude, zone, method, hanafi, highLatitude) }
    companion object {
        fun parse(values: JsonObject): NightSavedSettings {
            fun text(key: String) = requireNotNull(values[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            fun coordinate(key: String, limit: Double) = requireNotNull(values[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }.asDouble.also { require(it.isFinite() && it in -limit..limit) }
            val madhab = text("madhab").also { require(it in setOf("SHAFI", "HANAFI")) }
            return NightSavedSettings(ZoneId.of(text("timezone")), coordinate("latitude", 90.0), coordinate("longitude", 180.0),
                text("calculation_method").also { require(it in PrayerCalculator.methods) }, madhab == "HANAFI",
                text("high_latitude_rule").also { require(it in setOf("MIDDLE_OF_THE_NIGHT", "SEVENTH_OF_THE_NIGHT", "TWILIGHT_ANGLE")) })
        }
    }
}

@Composable
internal fun NightTonightScreen(graph: AppGraph, history: () -> Unit, tools: () -> Unit) {
    val actor by graph.session.state.collectAsStateWithLifecycle()
    val owner = (actor as? org.athar.app.auth.AuthState.SignedIn)?.user?.id ?: return
    val lease = runCatching { graph.session.lease(owner) }.getOrNull() ?: return
    key(lease) { NightTonightOwned(graph, lease, history, tools) }
}

@Composable
private fun NightTonightOwned(graph: AppGraph, lease: SessionLease, history: () -> Unit, tools: () -> Unit) {
    val owner = lease.owner
    val timer = nightTimerViewModel(graph, lease)
    val timerState by timer.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var retry by remember(lease) { mutableIntStateOf(0) }
    var now by remember(lease) { mutableStateOf(Instant.now()) }
    var settings by remember(lease) { mutableStateOf<NightSavedSettings?>(null) }
    var loading by remember(lease) { mutableStateOf(true) }
    var offline by remember(lease) { mutableStateOf(false) }
    val records = remember(graph, lease, retry) {
        graph.nightPrayer.observe(owner).onEach { graph.session.requireLease(lease) }.protectedReads()
    }
    val rows by records.collectAsStateWithLifecycle(ProtectedRead.Loading)
    LaunchedEffect(graph, lease, retry) {
        loading = true
        try {
            graph.session.requireLease(lease)
            val result = graph.content.get("night-prayer/preferences", expectedOwner = owner)
            graph.session.requireLease(lease)
            settings = NightSavedSettings.parse(result.data.asJsonObject.getAsJsonObject("values"))
            offline = result.offline
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { settings = null; offline = false }
        finally { loading = false }
    }
    DisposableEffect(context, lifecycle, lease) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { now = Instant.now() }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) { now = Instant.now(); retry++ } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { context.unregisterReceiver(receiver); lifecycle.lifecycle.removeObserver(observer) }
    }
    val identity = remember(settings, now) { try { settings?.identity(now) } catch (_: Exception) { null } }
    LaunchedEffect(lifecycle, identity, lease) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(nightRefreshDelay(identity, now)) }
        }
    }
    // Captured generations prevent an old same-owner screen from borrowing a new login.
    if (runCatching { graph.session.requireLease(lease) }.isFailure) return
    NightTonightContent(identity, rows, loading, offline, { retry++ }, history, tools, timerContent = {
        val selected = timerState.frame?.timer
        val resumeAllowed = selected != null && now >= Instant.parse(selected.activeAt) && now < Instant.parse(selected.fajr)
        NightTimerCardContent(timerState, identity, settings?.method, resumeAllowed) { event ->
            if (runCatching { graph.session.requireLease(lease) }.isSuccess) timer.handle(event) else timer.clearPrivate()
        }
    })
    NightTimerRecordingDialog(graph, timerState, timer)
}

/** Plain content keeps clock, preferences, Room and actor wiring outside rendering. */
@Composable
internal fun NightTonightContent(
    identity: NightIdentity?, records: ProtectedRead<List<Pair<LocalNightSession, JsonObject>>>,
    settingsLoading: Boolean, offline: Boolean, retry: () -> Unit, history: () -> Unit, tools: () -> Unit,
    timerContent: (@Composable () -> Unit)? = null,
) {
    if (records == ProtectedRead.Unreadable) {
        LazyColumn(Modifier.fillMaxSize().testTag("night-tonight"), contentPadding = PaddingValues(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
            item(key = "unreadable") { MessagePanel(stringResource(R.string.local_data_unreadable), true) {
                TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
            } }
            if (timerContent != null) item(key = "timer", contentType = "timer") { timerContent() }
        }; return
    }
    val rows = (records as? ProtectedRead.Ready)?.value?.map { it.first }.orEmpty()
    val recent = remember(rows) { rows.groupBy { it.nightDate }.toSortedMap(reverseOrder()).entries.take(5) }
    val tonight = remember(rows, identity?.nightDate) { rows.filter { it.nightDate == identity?.nightDate?.toString() } }
    val verses = tonight.sumOf { it.verseCount.toLong() }
    val milestone = NightRules.milestone(verses.coerceAtMost(1000).toInt())
    LazyColumn(Modifier.fillMaxSize().testTag("night-tonight"), contentPadding = PaddingValues(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
        item(key = "identity", contentType = "hero") { AtharHeroSurface(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.night_prayer), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.night_intro), style = MaterialTheme.typography.bodyMedium)
        } }
        if (settingsLoading || records == ProtectedRead.Loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (offline) item(key = "offline") { MessagePanel(stringResource(R.string.offline_notice)) }
        item(key = "summary", contentType = "summary") {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                    AtharSectionHeader(stringResource(R.string.night_tonight))
                    if (identity == null) {
                        if (!settingsLoading) MessagePanel(stringResource(R.string.night_identity_unavailable), true) {
                            TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
                            TextButton(onClick = tools) { Text(stringResource(R.string.night_tools)) }
                        }
                    } else {
                        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(identity.context.zone)
                        Text(stringResource(if (identity.inProgress) R.string.night_identity_active else R.string.night_identity_upcoming, identity.nightDate.toString()), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.night_identity_boundaries, formatter.format(identity.context.maghrib), formatter.format(identity.context.fajr)))
                        if (identity.inProgress) Text(stringResource(R.string.night_identity_last_third, formatter.format(identity.context.lastThird)))
                        if (records is ProtectedRead.Ready) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                                AtharMetric(verses.toString(), stringResource(R.string.night_tonight_verses), Modifier.weight(1f).widthIn(min = 100.dp))
                                AtharMetric(tonight.size.toString(), stringResource(R.string.night_tonight_sessions), Modifier.weight(1f).widthIn(min = 100.dp))
                            }
                            if (milestone > 0) AtharStatusPill(stringResource(R.string.night_reached_milestone, milestone))
                            val next = when { verses < 10 -> 10; verses < 100 -> 100; verses < 1000 -> 1000; else -> null }
                            if (next != null && identity.inProgress) Text(stringResource(R.string.night_next_reference, next))
                        }
                    }
                    TextButton(onClick = history) { Text(stringResource(R.string.night_history)) }
                }
            }
        }
        if (rows.any { it.dirty }) item(key = "pending") { AtharStatusPill(stringResource(R.string.night_pending), emphasized = true) }
        if (timerContent != null) item(key = "timer", contentType = "timer") { timerContent() }
        item(key = "recent-heading", contentType = "heading") { AtharSectionHeader(stringResource(R.string.night_recent_log)) }
        if (records is ProtectedRead.Ready && recent.isEmpty()) item(key = "empty") { QuietEmptyState(stringResource(R.string.night_empty)) }
        items(recent, key = { it.key }, contentType = { "night" }) { (date, sessions) ->
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    Text(date, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.night_recent_summary, sessions.sumOf { it.verseCount.toLong() }, sessions.size))
                }
            }
        }
        item(key = "source") { Text(stringResource(R.string.night_identity_notice), style = MaterialTheme.typography.bodySmall) }
    }
}
