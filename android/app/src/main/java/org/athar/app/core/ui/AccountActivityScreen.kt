package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ActivitySummary

@Composable internal fun AccountActivityScreen(graph: AppGraph) {
    val owner = requireNotNull(graph.session.userId)
    DataScreen(graph, "me/activity-summary") { data, reload ->
        val summary = runCatching { ActivitySummary.parse(data, owner) }.getOrNull()
        if (summary == null) { AccountDataFailure(reload); return@DataScreen }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "identity", contentType = "heading") { AtharScreenIdentity(stringResource(R.string.account_activity), AtharIcons.CalendarMonth) }
            item(key = "source", contentType = "state") {
                MessagePanel(stringResource(R.string.activity_server_snapshot, summary.date.toString()))
                TextButton(onClick = reload) { Text(stringResource(R.string.retry)) }
            }
            item(key = "adhkar", contentType = "metric") { AtharMetric(summary.adhkar.toString(), stringResource(R.string.activity_adhkar_recorded)) }
            item(key = "lessons", contentType = "metric") { AtharMetric(summary.lessons.toString(), stringResource(R.string.activity_lessons_recorded)) }
            item(key = "days", contentType = "metric") { AtharMetric(summary.activeDays.toString(), stringResource(R.string.activity_active_days)) }
            item(key = "progress", contentType = "metric") { AtharMetric(summary.lessonsWithProgress.toString(), stringResource(R.string.activity_lessons_progress)) }
            item(key = "completed", contentType = "metric") { AtharMetric(summary.completedLessons.toString(), stringResource(R.string.activity_lessons_complete)) }
            item(key = "unavailable", contentType = "state") { MessagePanel(stringResource(R.string.activity_unavailable_tracking)) }
            item(key = "recent-title", contentType = "heading") { Text(stringResource(R.string.activity_recent_days), style = MaterialTheme.typography.titleMedium) }
            if (summary.recent.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
            items(summary.recent.reversed(), key = { it.date.toString() }, contentType = { "activity-day" }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.date.toString(), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.activity_day_recorded, day.adhkar, day.lessons))
                    HorizontalDivider()
                }
            }
        }
    }
}
