package org.athar.app.notifications

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.athar.app.core.ui.*

@Composable
fun RoutineReminderSettingsScreen(graph: AppGraph, open: (String) -> Unit) {
    val owner = graph.session.userId ?: return
    val routines by remember(owner) { graph.builtinRoutines.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceRead by remember(owner) { graph.notificationPreferences.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)

    LaunchedEffect(owner) {
        try { graph.notificationPreferences.refresh(owner) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "reminder-hero") {
            AtharHeroSurface(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminder_settings), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.builtin_routine_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "reminder-overview") {
            ReminderCenterOverview(graph, owner, open, preferenceRead)
        }
        item(key = "reminder-quiet-hours") {
            ReminderQuietHoursCard(graph, owner)
        }
        when (val current = routines) {
            ProtectedRead.Loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ProtectedRead.Unreadable -> item(key = "unreadable") {
                MessagePanel(stringResource(R.string.local_data_unreadable), true)
            }
            is ProtectedRead.Ready -> {
                val preferences = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
                item(key = "salawat") {
                    BuiltinRoutineCard(
                        graph, owner, BuiltinRoutineKind.SALAWAT,
                        current.value.rules.firstOrNull { it.kind == BuiltinRoutineKind.SALAWAT },
                        preferences,
                    )
                }
                item(key = "friday") {
                    BuiltinRoutineCard(
                        graph, owner, BuiltinRoutineKind.FRIDAY,
                        current.value.rules.firstOrNull { it.kind == BuiltinRoutineKind.FRIDAY },
                        preferences,
                    )
                }
                item(key = "custom-routines") {
                    CustomRoutineSection(graph, owner, preferences)
                }
            }
        }
    }
}

@Composable
private fun BuiltinRoutineCard(
    graph: AppGraph,
    owner: Long,
    kind: BuiltinRoutineKind,
    existing: BuiltinRoutineRule?,
    preferences: NotificationPreferences?,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findRoutineActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    var hour by rememberSaveable(owner, kind.key) {
        mutableIntStateOf(existing?.let { LocalTime.parse(it.time).hour } ?: if (kind == BuiltinRoutineKind.FRIDAY) 10 else 20)
    }
    var minute by rememberSaveable(owner, kind.key) {
        mutableIntStateOf(existing?.let { LocalTime.parse(it.time).minute } ?: 0)
    }
    var days by remember(owner, kind.key) {
        mutableStateOf(existing?.days ?: if (kind == BuiltinRoutineKind.FRIDAY) setOf(5) else (1..7).toSet())
    }
    var busy by rememberSaveable(owner, kind.key) { mutableStateOf(false) }
    var message by rememberSaveable(owner, kind.key) { mutableStateOf<Int?>(null) }
    var showConsent by rememberSaveable(owner, kind.key) { mutableStateOf(false) }
    var showSettings by rememberSaveable(owner, kind.key) { mutableStateOf(false) }

    LaunchedEffect(existing?.revision) {
        existing?.let {
            val parsed = LocalTime.parse(it.time)
            hour = parsed.hour
            minute = parsed.minute
            days = it.days
        }
    }

    fun persist() {
        if (days.isEmpty()) { message = R.string.routine_days_required; return }
        busy = true
        scope.launch {
            try {
                graph.builtinRoutines.save(owner, kind, LocalTime.of(hour, minute), days, ZoneId.systemDefault())
                message = R.string.routine_saved
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.routine_save_failed
            } finally { busy = false }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) persist() else message = R.string.routine_permission_denied
    }

    fun permissionThenSave() {
        val host = activity ?: run { message = R.string.routine_save_failed; return }
        when (permission.state(host).status) {
            NotificationPermissionStatus.NOT_REQUIRED,
            NotificationPermissionStatus.GRANTED -> persist()
            NotificationPermissionStatus.READY_TO_REQUEST,
            NotificationPermissionStatus.DENIED_CAN_RETRY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.markRequestLaunched()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else persist()
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
                        categories = mapOf("salawat_friday" to true),
                    ),
                    owner,
                )
                if (updated.allows("salawat_friday")) permissionThenSave()
                else message = R.string.routine_save_failed
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.routine_save_failed
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(
                stringResource(if (kind == BuiltinRoutineKind.SALAWAT) R.string.salawat_routine_title else R.string.friday_routine_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(if (kind == BuiltinRoutineKind.SALAWAT) R.string.salawat_routine_body else R.string.friday_routine_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { TimePickerDialog(context,{ _,h,m -> hour=h;minute=m },hour,minute,true).show() },
                enabled = !busy,
            ) { Text(stringResource(R.string.routine_time, "%02d:%02d".format(hour, minute))) }

            if (kind == BuiltinRoutineKind.SALAWAT) {
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
            } else AtharStatusPill(stringResource(R.string.friday_only))

            Text(
                stringResource(R.string.routine_timezone, ZoneId.systemDefault().id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val saveAction = {
                if (preferences?.allows("salawat_friday") == true) permissionThenSave() else showConsent = true
            }
            if (existing?.enabled == true) {
                Button(onClick=saveAction,enabled=!busy&&preferences!=null,modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save))
                }
                OutlinedButton(
                    onClick = {
                        busy=true
                        scope.launch {
                            try {
                                graph.builtinRoutines.disable(owner,kind)
                                message=R.string.routine_disabled
                            } catch (failure:CancellationException) { throw failure }
                            catch (_:Exception) { message=R.string.routine_save_failed }
                            finally { busy=false }
                        }
                    },
                    enabled=!busy,
                    modifier=Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.routine_disable)) }
            } else {
                Button(onClick=saveAction,enabled=!busy&&preferences!=null,modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.routine_enable))
                }
            }

            message?.let {
                Text(
                    stringResource(it),
                    style=MaterialTheme.typography.bodySmall,
                    color=if(it in listOf(R.string.routine_save_failed,R.string.routine_permission_denied,R.string.routine_days_required))
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if(showConsent) AlertDialog(
        onDismissRequest={showConsent=false},
        title={Text(stringResource(R.string.routine_consent_title))},
        text={Text(stringResource(R.string.routine_consent_body))},
        confirmButton={TextButton(onClick={showConsent=false;consentThenSave()}){Text(stringResource(R.string.routine_consent_confirm))}},
        dismissButton={TextButton(onClick={showConsent=false}){Text(stringResource(R.string.cancel))}},
    )
    if(showSettings) AlertDialog(
        onDismissRequest={showSettings=false},
        title={Text(stringResource(R.string.routine_permission_title))},
        text={Text(stringResource(R.string.routine_permission_settings_body))},
        confirmButton={TextButton(onClick={showSettings=false;context.startActivity(permission.appNotificationSettings(context))}){Text(stringResource(R.string.lesson_reminder_open_settings))}},
        dismissButton={TextButton(onClick={showSettings=false}){Text(stringResource(R.string.cancel))}},
    )
}

private tailrec fun Context.findRoutineActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findRoutineActivity()
    else -> null
}
