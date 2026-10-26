package org.athar.app.khatmah

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.*
import org.athar.app.notifications.*

@Composable
fun KhatmahReminderCard(
    graph: AppGraph,
    plan: KhatmahPlanState,
    metrics: KhatmahMetrics,
) {
    val owner = graph.session.userId ?: return
    val ruleRead by remember(owner) { graph.khatmahReminders.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceRead by remember(owner) { graph.notificationPreferences.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val context = LocalContext.current
    val activity = remember(context) { context.findKhatmahReminderActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val scope = rememberCoroutineScope()
    val rule = (ruleRead as? ProtectedRead.Ready<KhatmahReminderRule?>)?.value
    val preferences = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
    var hour by rememberSaveable(owner) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).hour } ?: 20) }
    var minute by rememberSaveable(owner) { mutableIntStateOf(rule?.let { LocalTime.parse(it.time).minute } ?: 0) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }
    var message by rememberSaveable(owner) { mutableStateOf<Int?>(null) }
    var showConsent by rememberSaveable(owner) { mutableStateOf(false) }
    var showSettings by rememberSaveable(owner) { mutableStateOf(false) }

    val schedulable = !plan.conflict &&
        plan.status == "active" &&
        plan.completedPages < 604 &&
        metrics.dailyPages > 0 &&
        !metrics.overdue

    LaunchedEffect(rule?.revision) {
        rule?.let {
            val parsed = LocalTime.parse(it.time)
            hour = parsed.hour
            minute = parsed.minute
        }
    }
    LaunchedEffect(owner) {
        try { graph.notificationPreferences.refresh(owner) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { }
    }

    fun saveRule() {
        if (!schedulable) {
            message = R.string.khatmah_reminder_suppressed
            return
        }
        busy = true
        scope.launch {
            try {
                graph.khatmahReminders.saveRule(
                    owner,
                    LocalTime.of(hour, minute),
                    ZoneId.systemDefault(),
                    plan,
                )
                message = R.string.khatmah_reminder_saved
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.khatmah_reminder_failed
            } finally { busy = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveRule() else message = R.string.routine_permission_denied
    }

    fun permissionThenSave() {
        val host = activity ?: run { message = R.string.khatmah_reminder_failed; return }
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
        val current = preferences ?: run { message = R.string.routine_preferences_unavailable; return }
        scope.launch {
            try {
                val updated = graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = true,
                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                        categories = mapOf("khatmah" to true),
                    ),
                    owner,
                )
                if (updated.allows("khatmah")) permissionThenSave()
                else message = R.string.khatmah_reminder_failed
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.khatmah_reminder_failed
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(stringResource(R.string.khatmah_reminder_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.khatmah_reminder_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (ruleRead) {
                ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
                is ProtectedRead.Ready -> {
                    if (rule?.enabled == true) {
                        AtharStatusPill(
                            stringResource(
                                if (schedulable) R.string.khatmah_reminder_active
                                else R.string.khatmah_reminder_suppressed
                            ),
                            emphasized = !schedulable,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.khatmah_reminder_workload,
                            metrics.dailyPages,
                            metrics.remainingPages,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()
                        },
                        enabled = !busy,
                    ) {
                        Text(stringResource(R.string.khatmah_reminder_time,"%02d:%02d".format(hour,minute)))
                    }

                    if (rule?.enabled == true) {
                        Button(
                            onClick = {
                                if (preferences?.allows("khatmah") == true) permissionThenSave()
                                else showConsent = true
                            },
                            enabled = !busy && schedulable && preferences != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.save)) }
                        OutlinedButton(
                            onClick = {
                                busy=true
                                scope.launch {
                                    try {
                                        graph.khatmahReminders.disable(owner)
                                        message=R.string.khatmah_reminder_disabled
                                    } catch (failure:CancellationException) { throw failure }
                                    catch (_:Exception) { message=R.string.khatmah_reminder_failed }
                                    finally { busy=false }
                                }
                            },
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.khatmah_reminder_disable)) }
                    } else {
                        Button(
                            onClick = {
                                if (preferences?.allows("khatmah") == true) permissionThenSave()
                                else showConsent = true
                            },
                            enabled = !busy && schedulable && preferences != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.khatmah_reminder_enable)) }
                    }

                    message?.let {
                        Text(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it == R.string.khatmah_reminder_failed || it == R.string.routine_permission_denied)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showConsent) AlertDialog(
        onDismissRequest={showConsent=false},
        title={Text(stringResource(R.string.khatmah_reminder_consent_title))},
        text={Text(stringResource(R.string.khatmah_reminder_consent_body))},
        confirmButton={TextButton(onClick={showConsent=false;consentThenSave()}){Text(stringResource(R.string.khatmah_reminder_consent_confirm))}},
        dismissButton={TextButton(onClick={showConsent=false}){Text(stringResource(R.string.cancel))}},
    )
    if (showSettings) AlertDialog(
        onDismissRequest={showSettings=false},
        title={Text(stringResource(R.string.routine_permission_title))},
        text={Text(stringResource(R.string.routine_permission_settings_body))},
        confirmButton={TextButton(onClick={showSettings=false;context.startActivity(permission.appNotificationSettings(context))}){Text(stringResource(R.string.lesson_reminder_open_settings))}},
        dismissButton={TextButton(onClick={showSettings=false}){Text(stringResource(R.string.cancel))}},
    )
}

private tailrec fun Context.findKhatmahReminderActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findKhatmahReminderActivity()
    else -> null
}
