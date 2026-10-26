package org.athar.app.quran

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
import androidx.compose.material3.Slider
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
import java.time.LocalDate
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
fun QuranWirdCard(graph: AppGraph) {
    val owner = graph.session.userId ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findQuranActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val stateFlow = remember(owner) { graph.quranWird.observe(owner) }
    val read by stateFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceFlow = remember(owner) { graph.notificationPreferences.observe(owner) }
    val preferenceRead by preferenceFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    val state = (read as? ProtectedRead.Ready<QuranWirdState?>)?.value
    val rule = state?.rule
    var target by rememberSaveable(owner) { mutableIntStateOf(rule?.targetAyahs ?: 10) }
    var hour by rememberSaveable(owner) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).hour } ?: 20) }
    var minute by rememberSaveable(owner) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).minute } ?: 0) }
    var days by remember(owner) { mutableStateOf(rule?.days ?: (1..7).toSet()) }
    var showConsent by rememberSaveable(owner) { mutableStateOf(false) }
    var showSettings by rememberSaveable(owner) { mutableStateOf(false) }
    var message by rememberSaveable(owner) { mutableStateOf<Int?>(null) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }

    LaunchedEffect(rule?.revision) {
        rule?.let {
            target = it.targetAyahs
            val time = LocalTime.parse(it.time)
            hour = time.hour
            minute = time.minute
            days = it.days
        }
    }
    LaunchedEffect(owner) {
        try {
            graph.notificationPreferences.refresh(owner)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Retained encrypted preference remains the offline source of truth.
        }
    }

    fun saveRule() {
        if (days.isEmpty()) {
            message = R.string.quran_wird_days_required
            return
        }
        busy = true
        scope.launch {
            try {
                graph.quranWird.saveRule(
                    owner = owner,
                    targetAyahs = target,
                    time = LocalTime.of(hour, minute),
                    days = days,
                    timezone = ZoneId.systemDefault(),
                )
                message = R.string.quran_wird_saved
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.quran_wird_save_failed
            } finally {
                busy = false
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveRule() else message = R.string.quran_wird_permission_denied
    }

    fun permissionThenSave() {
        val host = activity ?: run {
            message = R.string.quran_wird_save_failed
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
            message = R.string.quran_wird_preferences_unavailable
            return
        }
        scope.launch {
            try {
                val updated = graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = true,
                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                        categories = mapOf("quran_routine" to true),
                    ),
                    owner,
                )
                if (updated.allows("quran_routine")) permissionThenSave()
                else message = R.string.quran_wird_save_failed
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.quran_wird_save_failed
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(stringResource(R.string.quran_wird_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.quran_wird_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (read) {
                ProtectedRead.Loading -> Text(stringResource(R.string.loading))
                ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
                is ProtectedRead.Ready -> {
                    Text(stringResource(R.string.quran_wird_target, target), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = target.toFloat(),
                        onValueChange = { target = it.toInt().coerceIn(1, 50) },
                        valueRange = 1f..50f,
                        steps = 48,
                        enabled = !busy,
                    )
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
                        Text(stringResource(R.string.quran_wird_time, "%02d:%02d".format(hour, minute)))
                    }

                    Text(stringResource(R.string.quran_wird_days), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        DayOfWeek.values().forEach { day ->
                            FilterChip(
                                selected = day.value in days,
                                onClick = {
                                    days = if (day.value in days) days - day.value else days + day.value
                                },
                                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                                enabled = !busy,
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.quran_wird_timezone, ZoneId.systemDefault().id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    state?.progress?.let { progress ->
                        val today = LocalDate.now(ZoneId.of(rule?.timezone ?: ZoneId.systemDefault().id))
                        if (progress.date == today.toString() && rule != null) {
                            Text(
                                stringResource(
                                    R.string.quran_wird_today_progress,
                                    progress.advancedAyahs().coerceAtMost(rule.targetAyahs),
                                    rule.targetAyahs,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (rule?.enabled == true) {
                        Button(
                            onClick = {
                                val prefs = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
                                if (prefs?.allows("quran_routine") == true) permissionThenSave()
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
                                        graph.quranWird.disable(owner)
                                        message = R.string.quran_wird_disabled
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        message = R.string.quran_wird_save_failed
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.quran_wird_disable)) }
                    } else {
                        Button(
                            onClick = {
                                val prefs = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
                                if (prefs?.allows("quran_routine") == true) permissionThenSave()
                                else showConsent = true
                            },
                            enabled = !busy && preferenceRead is ProtectedRead.Ready,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.quran_wird_enable)) }
                    }

                    message?.let {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (
                                it == R.string.quran_wird_save_failed ||
                                it == R.string.quran_wird_permission_denied ||
                                it == R.string.quran_wird_days_required
                            ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text(stringResource(R.string.quran_wird_consent_title)) },
        text = { Text(stringResource(R.string.quran_wird_consent_body)) },
        confirmButton = {
            TextButton(onClick = {
                showConsent = false
                consentThenSave()
            }) { Text(stringResource(R.string.quran_wird_consent_confirm)) }
        },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text(stringResource(R.string.quran_wird_permission_title)) },
        text = { Text(stringResource(R.string.quran_wird_permission_settings_body)) },
        confirmButton = {
            TextButton(onClick = {
                showSettings = false
                context.startActivity(permission.appNotificationSettings(context))
            }) { Text(stringResource(R.string.lesson_reminder_open_settings)) }
        },
        dismissButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private tailrec fun Context.findQuranActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findQuranActivity()
    else -> null
}
