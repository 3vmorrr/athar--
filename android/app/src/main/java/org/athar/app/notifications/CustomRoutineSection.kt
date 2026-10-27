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

private data class CustomUiAction(
    val kind: String,
    val id: String? = null,
    val label: String? = null,
    val time: String? = null,
    val days: Set<Int> = emptySet(),
)

@Composable
fun CustomRoutineSection(
    graph: AppGraph,
    owner: Long,
    preferences: NotificationPreferences?,
) {
    val stateRead by remember(owner) { graph.customRoutines.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val context = LocalContext.current
    val activity = remember(context) { context.findCustomRoutineActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    var editorRule by remember { mutableStateOf<CustomRoutineRule?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteRule by remember { mutableStateOf<CustomRoutineRule?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<CustomUiAction?>(null) }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    fun perform(action: CustomUiAction) {
        busy = true
        scope.launch {
            try {
                when (action.kind) {
                    "create" -> graph.customRoutines.create(
                        owner,
                        requireNotNull(action.label),
                        LocalTime.parse(requireNotNull(action.time)),
                        action.days,
                        ZoneId.systemDefault(),
                    )
                    "edit" -> graph.customRoutines.edit(
                        owner,
                        requireNotNull(action.id),
                        requireNotNull(action.label),
                        LocalTime.parse(requireNotNull(action.time)),
                        action.days,
                        ZoneId.systemDefault(),
                    )
                    "resume" -> graph.customRoutines.setEnabled(owner, requireNotNull(action.id), true)
                    "pause" -> graph.customRoutines.setEnabled(owner, requireNotNull(action.id), false)
                    "delete" -> graph.customRoutines.delete(owner, requireNotNull(action.id))
                    else -> error("unknown_custom_action")
                }
                message = when (action.kind) {
                    "delete" -> R.string.custom_routine_deleted
                    "pause" -> R.string.custom_routine_paused
                    "resume" -> R.string.custom_routine_resumed
                    else -> R.string.custom_routine_saved
                }
                creating = false
                editorRule = null
                deleteRule = null
                pending = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.custom_routine_failed
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        if (granted && action != null) perform(action)
        else {
            pending = null
            message = R.string.routine_permission_denied
        }
    }

    fun permissionThen(action: CustomUiAction) {
        val host = activity ?: run { message = R.string.custom_routine_failed; return }
        pending = action
        when (permission.state(host).status) {
            NotificationPermissionStatus.NOT_REQUIRED,
            NotificationPermissionStatus.GRANTED -> perform(action)
            NotificationPermissionStatus.READY_TO_REQUEST,
            NotificationPermissionStatus.DENIED_CAN_RETRY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.markRequestLaunched()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else perform(action)
            }
            NotificationPermissionStatus.SETTINGS_REQUIRED -> showSettings = true
        }
    }

    fun requireConsent(action: CustomUiAction) {
        if (preferences?.allows("custom_routines") == true) {
            permissionThen(action)
        } else {
            pending = action
            showConsent = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.custom_routines_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.custom_routines_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { creating = true; editorRule = null },
                enabled = !busy &&
                    ((stateRead as? ProtectedRead.Ready<CustomRoutineState>)?.value?.rules?.size ?: 0) < CustomRoutineState.MAX_RULES,
            ) { Text(stringResource(R.string.custom_routine_add)) }
        }

        when (val current = stateRead) {
            ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
            is ProtectedRead.Ready -> {
                if (current.value.rules.isEmpty()) {
                    QuietEmptyState(stringResource(R.string.custom_routine_empty))
                } else {
                    current.value.rules.forEach { rule ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
                                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(rule.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    AtharStatusPill(
                                        stringResource(if (rule.enabled) R.string.custom_routine_active else R.string.custom_routine_paused),
                                    )
                                }
                                Text(
                                    stringResource(R.string.custom_routine_time, rule.time),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    rule.days.sorted().joinToString(" · ") {
                                        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                                ) {
                                    TextButton(onClick = { editorRule = rule; creating = false }, enabled = !busy) {
                                        Text(stringResource(R.string.edit))
                                    }
                                    TextButton(
                                        onClick = {
                                            if (rule.enabled) perform(CustomUiAction("pause", id=rule.id))
                                            else requireConsent(CustomUiAction("resume", id=rule.id))
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text(stringResource(if (rule.enabled) R.string.custom_routine_pause else R.string.custom_routine_resume))
                                    }
                                    TextButton(onClick = { deleteRule = rule }, enabled = !busy) {
                                        Text(stringResource(R.string.delete_action))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        message?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (it == R.string.custom_routine_failed || it == R.string.routine_permission_denied)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (creating || editorRule != null) {
        CustomRoutineEditorDialog(
            existing = editorRule,
            onDismiss = { creating = false; editorRule = null },
            onSave = { label, time, days ->
                val existing = editorRule
                val action = if (existing == null) {
                    CustomUiAction("create",label=label,time=time.toString(),days=days)
                } else {
                    CustomUiAction("edit",id=existing.id,label=label,time=time.toString(),days=days)
                }
                if (existing?.enabled == false) perform(action) else requireConsent(action)
            },
        )
    }

    deleteRule?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteRule = null },
            title = { Text(stringResource(R.string.custom_routine_delete_title)) },
            text = { Text(stringResource(R.string.custom_routine_delete_body, target.label)) },
            confirmButton = {
                TextButton(onClick = { perform(CustomUiAction("delete",id=target.id)) }) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = { TextButton(onClick = { deleteRule = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent=false; pending=null },
        title = { Text(stringResource(R.string.custom_routine_consent_title)) },
        text = { Text(stringResource(R.string.custom_routine_consent_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val action = pending
                    val current = preferences
                    showConsent = false
                    if (action == null || current == null) {
                        pending = null
                        message = R.string.routine_preferences_unavailable
                    } else {
                        scope.launch {
                            try {
                                val updated = graph.notificationPreferences.update(
                                    NotificationPreferencePatch(
                                        expectedRevision = current.revision,
                                        masterEnabled = true,
                                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                                        categories = mapOf("custom_routines" to true),
                                    ),
                                    owner,
                                )
                                if (updated.allows("custom_routines")) permissionThen(action)
                                else { pending=null; message=R.string.custom_routine_failed }
                            } catch (failure:CancellationException) { throw failure }
                            catch (_:Exception) { pending=null; message=R.string.custom_routine_failed }
                        }
                    }
                },
            ) { Text(stringResource(R.string.custom_routine_consent_confirm)) }
        },
        dismissButton = { TextButton(onClick={showConsent=false;pending=null}){Text(stringResource(R.string.cancel))} },
    )

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings=false; pending=null },
        title = { Text(stringResource(R.string.routine_permission_title)) },
        text = { Text(stringResource(R.string.routine_permission_settings_body)) },
        confirmButton = {
            TextButton(onClick = {
                showSettings=false
                pending=null
                context.startActivity(permission.appNotificationSettings(context))
            }) { Text(stringResource(R.string.lesson_reminder_open_settings)) }
        },
        dismissButton = { TextButton(onClick={showSettings=false;pending=null}){Text(stringResource(R.string.cancel))} },
    )
}

@Composable
private fun CustomRoutineEditorDialog(
    existing: CustomRoutineRule?,
    onDismiss: () -> Unit,
    onSave: (String, LocalTime, Set<Int>) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var label by rememberSaveable(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var hour by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.let { LocalTime.parse(it.time).hour } ?: 20) }
    var minute by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.let { LocalTime.parse(it.time).minute } ?: 0) }
    var days by remember(existing?.id) { mutableStateOf(existing?.days ?: (1..7).toSet()) }
    val valid = label.trim().length in 1..60 && days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if(existing==null)R.string.custom_routine_add else R.string.custom_routine_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                OutlinedTextField(
                    value=label,
                    onValueChange={label=it.take(60)},
                    singleLine=true,
                    label={Text(stringResource(R.string.custom_routine_label))},
                    modifier=Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick={TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()},
                ) { Text(stringResource(R.string.custom_routine_time,"%02d:%02d".format(hour,minute))) }
                FlowRow(
                    horizontalArrangement=Arrangement.spacedBy(AtharSpacing.Sm),
                    verticalArrangement=Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected=day.value in days,
                            onClick={days=if(day.value in days)days-day.value else days+day.value},
                            label={Text(day.getDisplayName(TextStyle.SHORT,locale))},
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick={onSave(label.trim(),LocalTime.of(hour,minute),days)},
                enabled=valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}},
    )
}

private tailrec fun Context.findCustomRoutineActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findCustomRoutineActivity()
    else -> null
}
