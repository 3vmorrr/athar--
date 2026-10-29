package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ActivitySummary

@Composable internal fun AccountActivityScreen(graph: AppGraph) {
    val owner = requireNotNull(graph.session.userId)
    val identity = "account-activity:${graph.partition}:$owner:" + androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val vm: DataViewModel = viewModel(key = identity, factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DataViewModel(graph, "me/activity-summary", emptyMap()) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val revision by graph.content.revision.collectAsStateWithLifecycle()
    LaunchedEffect(identity, revision) { vm.load() }
    val result = state.result
    val summary = result?.let { runCatching { ActivitySummary.parse(it.data, owner) }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "identity", contentType = "heading") { AtharScreenIdentity(stringResource(R.string.account_activity), AtharIcons.CalendarMonth) }
        item(key = "sync", contentType = "controls") { BookActivitySyncPanel(graph, owner) }
        if (state.loading) item(key = "loading", contentType = "state") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (result == null && !state.loading) item(key = "failed", contentType = "state") { FailurePanel(state.error, vm::load) }
        if (result != null && summary == null) item(key = "unreadable", contentType = "state") { AccountDataFailure(vm::load) }
        if (result?.offline == true) item(key = "cached", contentType = "state") { MessagePanel(stringResource(R.string.offline_notice)) }
        if (summary != null) {
            item(key = "source", contentType = "state") {
                MessagePanel(stringResource(R.string.activity_server_snapshot, summary.date.toString()))
                TextButton(onClick = vm::load) { Text(stringResource(R.string.retry)) }
            }
            item(key = "adhkar", contentType = "metric") { AtharMetric(summary.adhkar.toString(), stringResource(R.string.activity_adhkar_recorded)) }
            item(key = "lessons", contentType = "metric") { AtharMetric(summary.lessons.toString(), stringResource(R.string.activity_lessons_recorded)) }
            item(key = "days", contentType = "metric") { AtharMetric(summary.activeDays.toString(), stringResource(R.string.activity_active_days)) }
            item(key = "progress", contentType = "metric") { AtharMetric(summary.lessonsWithProgress.toString(), stringResource(R.string.activity_lessons_progress)) }
            item(key = "completed", contentType = "metric") { AtharMetric(summary.completedLessons.toString(), stringResource(R.string.activity_lessons_complete)) }
            if (summary.downloads != null) item(key = "downloads", contentType = "metric") { AtharMetric(summary.downloads.toString(), stringResource(R.string.activity_downloads_complete)) }
            item(key = "unavailable", contentType = "state") { MessagePanel(stringResource(if (summary.downloads == null) R.string.activity_unavailable_tracking else R.string.activity_available_book_tracking)) }
            item(key = "recent-title", contentType = "heading") { Text(stringResource(R.string.activity_recent_days), style = MaterialTheme.typography.titleMedium) }
            if (summary.recent.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
            items(summary.recent.reversed(), key = { it.date.toString() }, contentType = { "activity-day" }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.date.toString(), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.activity_day_recorded, day.adhkar, day.lessons))
                    if (day.downloads != null) Text(stringResource(R.string.activity_day_downloads, day.downloads))
                    HorizontalDivider()
                }
            }
        }
    }
}
