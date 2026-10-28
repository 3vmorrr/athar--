package org.athar.app.adhkar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable internal fun AdhkarStatsPanel(graph: AppGraph, revision: Int) {
    val owner = requireNotNull(graph.session.userId)
    var summary by remember(owner) { mutableStateOf<AdhkarSummary?>(null) }
    var pending by remember(owner) { mutableStateOf<Int?>(null) }
    var loading by remember(owner) { mutableStateOf(true) }
    var failed by remember(owner) { mutableStateOf(false) }
    var localUnreadable by remember(owner) { mutableStateOf(false) }
    var offline by remember(owner) { mutableStateOf(false) }
    var attempt by remember(owner) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(owner) { mutableStateOf(false) }
    LaunchedEffect(owner, revision, attempt) {
        loading = true; failed = false; pending = null; localUnreadable = false
        try {
            graph.session.requireVerifiedOwner(owner)
            val result = graph.content.get("me/adhkar-stats")
            val parsed = AdhkarSummary.parse(result.data)
            graph.session.requireVerifiedOwner(owner)
            summary = parsed; offline = result.offline
            try { pending = graph.counters.pendingCompletionCount(owner, owner) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { localUnreadable = true }
            graph.session.requireVerifiedOwner(owner)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AtharSectionHeader(title = stringResource(R.string.adhkar_statistics), actionLabel = stringResource(R.string.retry), onAction = { attempt++ })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (localUnreadable) MessagePanel(stringResource(R.string.local_data_unreadable), true)
        if (failed) MessagePanel(stringResource(R.string.network_error), true)
        summary?.let { data ->
            Text(stringResource(R.string.adhkar_statistics_date, data.date), style = MaterialTheme.typography.bodySmall)
            if (offline || failed) Text(stringResource(R.string.cached_content_retry), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AtharMetric(data.today.toString(), stringResource(R.string.adhkar_today_completions))
                AtharMetric(data.total.toString(), stringResource(R.string.adhkar_total_completions))
                AtharMetric(data.days.toString(), stringResource(R.string.adhkar_active_days))
            }
            pending?.takeIf { it > 0 }?.let { Text(stringResource(R.string.adhkar_pending_completions, it)) }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.all_categories)) }
            if (expanded) data.categories.forEach { row ->
                Text(row.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.adhkar_category_items, row.completed, row.items))
                LinearProgressIndicator(progress = { if (row.target > 0) (row.count.toDouble() / row.target).toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
