package org.athar.app.adhkar

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.MessagePanel
import org.athar.app.notifications.NotificationPermissionController
import org.athar.app.notifications.NotificationPermissionStatus
import org.athar.app.notifications.NotificationPreferenceContract
import org.athar.app.notifications.NotificationPreferencePatch
import org.athar.app.notifications.NotificationPreferences

@Composable
fun AdhkarReminderCard(
    graph: AppGraph,
    category: String,
) {
    val owner = graph.session.userId ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findAdhkarActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val stateFlow = remember(owner) { graph.adhkarReminders.observe(owner) }
    val read by stateFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceFlow = remember(owner) { graph.notificationPreferences.observe(owner) }
    val preferenceRead by preferenceFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    val state = (read as? ProtectedRead.Ready<AdhkarReminderState>)?.value
    val rule = state?.rules?.firstOrNull { it.category == category }
    var hour by rememberSaveable(owner, category) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).hour } ?: 20) }
    var minute by rememberSaveable(owner, category) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).minute } ?: 30) }
    var days by remember(owner, category) { mutableStateOf(rule?.days ?: (1..7).toSet()) }
    var showConsent by rememberSaveable(owner, category) { mutableStateOf(false) }
    var showSettings by rememberSaveable(owner, category) { mutableStateOf(false) }
    var busy by rememberSaveable(owner, category) { mutableStateOf(false) }
    var message by rememberSaveable(owner, category) { mutableStateOf<Int?>(null) }

    LaunchedEffect(rule?.revision) {
        rule?.let {
            val time = LocalTime.parse(it.time)
            hour = time.hour
            minute = time.minute
            days = it.days
            try { graph.adhkarReminders.refreshRequirements(owner, category) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { }
        }
    }
    LaunchedEffect(owner) {
        try { graph.notificationPreferences.refresh(owner) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { }
    }

    fun saveRule() {
        if (days.isEmpty()) {
            message = R.string.adhkar_reminder_days_required
            return
        }
        busy = true
        scope.launch {
            try {
                message = when (
                    graph.adhkarReminders.saveRule(
                        owner,
                        category,
                        LocalTime.of(hour, minute),
                        days,
                        ZoneId.systemDefault(),
                    )
                ) {
                    AdhkarRuleSaveResult.SAVED -> R.string.adhkar_reminder_saved
                    AdhkarRuleSaveResult.NO_FIXED_REQUIREMENTS -> R.string.adhkar_reminder_no_fixed
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.adhkar_reminder_save_failed
            } finally {
                busy = false
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveRule() else message = R.string.adhkar_reminder_permission_denied
    }

    fun permissionThenSave() {
        val host = activity ?: run {
            message = R.string.adhkar_reminder_save_failed
            return
        }
        when (permission.state(host).status) {
            NotificationPermissionStatus.NOT_REQUIRED,
            NotificationPermissionStatus.GRANTED -> saveRule()
            NotificationPermissionStatus.READY_TO_REQUEST,
            NotificationPermissionStatus.DENIED_CAN_RETRY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.markRequestLaunched()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else saveRule()
            }
            NotificationPermissionStatus.SETTINGS_REQUIRED -> showSettings = true
        }
    }

    fun consentThenSave() {
        val current = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
        if (current == null) {
            message = R.string.adhkar_reminder_preferences_unavailable
            return
        }
        scope.launch {
            try {
                val updated = graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = true,
                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                        categories = mapOf("adhkar_routine" to true),
                    ),
                    owner,
                )
                if (updated.allows("adhkar_routine")) permissionThenSave()
                else message = R.string.adhkar_reminder_save_failed
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.adhkar_reminder_save_failed
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(stringResource(R.string.adhkar_reminder_title, category), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.adhkar_reminder_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (read) {
                ProtectedRead.Loading -> Text(stringResource(R.string.loading))
                ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
                is ProtectedRead.Ready -> {
                    rule?.let {
                        Text(
                            stringResource(R.string.adhkar_reminder_requirements, it.requirements.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, selectedHour, selectedMinute ->
                                    hour = selectedHour
                                    minute = selectedMinute
                                },
                                hour,
                                minute,
                                true,
                            ).show()
                        },
                        enabled = !busy,
                    ) {
                        Text(stringResource(R.string.adhkar_reminder_time, "%02d:%02d".format(hour, minute)))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = day.value in days,
                                onClick = { days = if (day.value in days) days - day.value else days + day.value },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                                enabled = !busy,
                            )
                        }
                    }
                    if (rule?.enabled == true) {
                        Button(
                            onClick = {
                                val prefs = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
                                if (prefs?.allows("adhkar_routine") == true) permissionThenSave()
                                else showConsent = true
                            },
                            enabled = !busy && preferenceRead is ProtectedRead.Ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.save)) }
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        graph.adhkarReminders.disable(owner, category)
                                        message = R.string.adhkar_reminder_disabled
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        message = R.string.adhkar_reminder_save_failed
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.adhkar_reminder_disable)) }
                    } else {
                        Button(
                            onClick = {
                                val prefs = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
                                if (prefs?.allows("adhkar_routine") == true) permissionThenSave()
                                else showConsent = true
                            },
                            enabled = !busy && preferenceRead is ProtectedRead.Ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.adhkar_reminder_enable)) }
                    }
                    message?.let {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (
                                it == R.string.adhkar_reminder_save_failed ||
                                it == R.string.adhkar_reminder_permission_denied ||
                                it == R.string.adhkar_reminder_days_required ||
                                it == R.string.adhkar_reminder_no_fixed
                            ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text(stringResource(R.string.adhkar_reminder_consent_title)) },
        text = { Text(stringResource(R.string.adhkar_reminder_consent_body)) },
        confirmButton = {
            TextButton(onClick = { showConsent = false; consentThenSave() }) {
                Text(stringResource(R.string.adhkar_reminder_consent_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text(stringResource(R.string.adhkar_reminder_permission_title)) },
        text = { Text(stringResource(R.string.adhkar_reminder_permission_settings_body)) },
        confirmButton = {
            TextButton(onClick = {
                showSettings = false
                context.startActivity(permission.appNotificationSettings(context))
            }) { Text(stringResource(R.string.lesson_reminder_open_settings)) }
        },
        dismissButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private tailrec fun Context.findAdhkarActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAdhkarActivity()
    else -> null
}
