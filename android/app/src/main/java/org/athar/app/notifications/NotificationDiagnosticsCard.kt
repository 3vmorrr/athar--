package org.athar.app.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.AtharStatusPill
import org.athar.app.core.ui.MessagePanel

@Composable
fun NotificationDiagnosticsCard(
    graph: AppGraph,
    owner: Long,
) {
    val scope = rememberCoroutineScope()
    var read by remember(owner) { mutableStateOf<ProtectedRead<NotificationDiagnosticsSnapshot>>(ProtectedRead.Loading) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                read = graph.notificationDiagnostics.read(owner)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                read = ProtectedRead.Unreadable
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(owner) { refresh() }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.notification_diagnostics),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.notification_diagnostics_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = ::refresh, enabled = !busy) {
                    Text(stringResource(R.string.refresh))
                }
            }

            when (val current = read) {
                ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProtectedRead.Unreadable -> MessagePanel(
                    stringResource(R.string.notification_diagnostics_unreadable),
                    true,
                )
                is ProtectedRead.Ready -> DiagnosticsBody(current.value)
            }
        }
    }
}

@Composable
private fun DiagnosticsBody(snapshot: NotificationDiagnosticsSnapshot) {
    val masterReady = snapshot.masterEnabled && snapshot.consentCurrent
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.notification_diagnostics_master))
        AtharStatusPill(
            stringResource(
                if (masterReady) R.string.notification_diagnostics_allowed
                else R.string.notification_diagnostics_blocked
            ),
            emphasized = !masterReady,
        )
    }

    DiagnosticTextRow(
        R.string.notification_diagnostics_quiet,
        if (snapshot.quietHoursEnabled) R.string.reminder_quiet_enabled
        else R.string.reminder_quiet_disabled,
    )
    DiagnosticTextRow(
        R.string.notification_diagnostics_history,
        if (snapshot.hasDeliveryHistory) R.string.notification_diagnostics_yes
        else R.string.notification_diagnostics_no,
    )

    Text(
        stringResource(
            R.string.notification_diagnostics_outcomes,
            snapshot.outcomes.ready,
            snapshot.outcomes.deferred,
            snapshot.outcomes.dropped,
            snapshot.outcomes.consentBlocked,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(
            R.string.notification_diagnostics_inbox_pending,
            snapshot.inboxPendingMutations,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HorizontalDivider()

    NotificationPreferenceContract.categories.forEach { category ->
        val allowed = snapshot.categoryAllowed[category] == true
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            Text(
                stringResource(categoryLabel(category)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.notification_diagnostics_pending_count,
                    snapshot.pendingByCategory[category] ?: 0,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AtharStatusPill(
                stringResource(
                    if (allowed) R.string.notification_diagnostics_allowed
                    else R.string.notification_diagnostics_blocked
                ),
                emphasized = !allowed,
            )
        }
    }
}

@Composable
private fun DiagnosticTextRow(label: Int, value: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun categoryLabel(category: String): Int = when (category) {
    "lesson_reminders" -> R.string.lesson_reminder_title
    "following_updates" -> R.string.following_updates
    "quran_routine" -> R.string.quran_wird_title
    "adhkar_routine" -> R.string.adhkar
    "salawat_friday" -> R.string.salawat_friday_summary
    "custom_routines" -> R.string.custom_routines_title
    "khatmah" -> R.string.khatmah
    "night_prayer" -> R.string.night_prayer
    else -> error("unknown_notification_category")
}
