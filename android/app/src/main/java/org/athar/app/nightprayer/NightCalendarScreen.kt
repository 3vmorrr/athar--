package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.athar.app.R
import org.athar.app.core.ui.AtharIcons
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable fun NightCalendarScreen(vm: NightViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedKey by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = remember(monthKey) { YearMonth.parse(monthKey) }
    val selected = remember(selectedKey) { LocalDate.parse(selectedKey) }
    var exemptions by remember { mutableStateOf(emptySet<LocalDate>()) }
    LaunchedEffect(monthKey) { exemptions = vm.graph.nightPrayer.localExemptions() }
    val totals = remember(state.rows) { state.rows.groupBy { LocalDate.parse(it.first.nightDate) }.mapValues { (_, rows) -> rows.sumOf { it.first.verseCount } } }
    val days = remember(month, totals, exemptions) { NightCalendar.month(month, totals, exemptions) }
    val selectedRows = remember(state.rows, selectedKey) { state.rows.filter { it.first.nightDate == selectedKey } }
    val locale = Locale.getDefault()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL uuuu", locale) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 1000.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "month-navigation", contentType = "controls") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { val next = month.minusMonths(1); monthKey = next.toString(); selectedKey = next.atDay(selected.dayOfMonth.coerceAtMost(next.lengthOfMonth())).toString() }, enabled = month.year > 1900 || month.monthValue > 1) { Icon(AtharIcons.ArrowBack, stringResource(R.string.previous_month)) }
                    Text(month.format(monthFormat), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { val next = month.plusMonths(1); monthKey = next.toString(); selectedKey = next.atDay(selected.dayOfMonth.coerceAtMost(next.lengthOfMonth())).toString() }, enabled = month < YearMonth.now()) { Icon(AtharIcons.ArrowForward, stringResource(R.string.next_month)) }
                }
            }
            item(key = "month-summary", contentType = "summary") {
                Text(stringResource(R.string.night_totals, days.count { it.verseCount > 0 }, days.sumOf { it.verseCount }), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.calendar_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "days", contentType = "calendar") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val agenda = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
                    if (agenda) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { day -> DayCell(day, day.date == selected, dateFormat, Modifier.fillMaxWidth(), true) { selectedKey = day.date.toString() } }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { DayOfWeek.entries.forEach { weekday -> Text(weekday.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) } }
                        val offset = month.atDay(1).dayOfWeek.value - 1
                        val cells: List<CalendarNight?> = List(offset) { null } + days
                        cells.chunked(7).forEach { week -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (week + List(7 - week.size) { null }).forEach { day ->
                                if (day == null) Spacer(Modifier.weight(1f).height(56.dp))
                                else DayCell(day, day.date == selected, dateFormat, Modifier.weight(1f), false) { selectedKey = day.date.toString() }
                            }
                        } }
                    }
                }
            }
            item(key = "selected-date", contentType = "heading") { Text(selected.format(dateFormat), style = MaterialTheme.typography.titleLarge) }
            if (selectedRows.isEmpty()) item(key = "no-entry", contentType = "empty") { Text(stringResource(R.string.calendar_no_entry), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(selectedRows, key = { "calendar-${it.first.id}" }, contentType = { "session" }) { (session, body) ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.night_session_count, session.verseCount), style = MaterialTheme.typography.titleMedium)
                    body["note"]?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text(stringResource(if (session.dirty) R.string.night_pending else R.string.night_synced), style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
}

@Composable private fun DayCell(day: CalendarNight, selected: Boolean, format: DateTimeFormatter, modifier: Modifier, agenda: Boolean, select: () -> Unit) {
    val status = stringResource(if (day.exempt) R.string.calendar_local_exemption else if (day.verseCount > 0) R.string.calendar_recorded else R.string.calendar_no_entry)
    val description = "${day.date.format(format)}، $status، ${stringResource(R.string.night_session_count, day.verseCount)}"
    Surface(onClick = select, modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = description; this.selected = selected }, shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (agenda) day.date.format(format) else day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
            Text(if (day.verseCount > 0) day.verseCount.toString() else if (day.exempt) status else "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
