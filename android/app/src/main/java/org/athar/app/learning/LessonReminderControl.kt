package org.athar.app.learning

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.notifications.*

@Composable
fun LessonReminderControl(graph: AppGraph, event: UpcomingLesson) {
    val owner = graph.session.userId ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val choiceFlow = remember(owner, event.id) { graph.lessonReminderChoices.observe(owner, event.id) }
    val choiceRead by choiceFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceFlow = remember(owner) { graph.notificationPreferences.observe(owner) }
    val preferenceRead by preferenceFlow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showConsent by rememberSaveable(event.id) { mutableStateOf(false) }
    var showSettings by rememberSaveable(event.id) { mutableStateOf(false) }
    var message by rememberSaveable(event.id) { mutableStateOf<Int?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) permissionRevision++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(owner) {
        try { graph.notificationPreferences.refresh(owner) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { }
    }

    fun saveChoice() {
        scope.launch {
            try {
                graph.lessonReminderChoices.enable(event.id, owner)
                message = R.string.lesson_reminder_saved_choice
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { message = R.string.lesson_reminder_save_failed }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRevision++
        if (granted) saveChoice() else message = R.string.lesson_reminder_permission_denied
    }

    fun requestPermissionThenSave() {
        val host = activity ?: run {
            message = R.string.lesson_reminder_save_failed
            return
        }
        when (permission.state(host).status) {
            NotificationPermissionStatus.NOT_REQUIRED,
            NotificationPermissionStatus.GRANTED -> saveChoice()
            NotificationPermissionStatus.READY_TO_REQUEST,
            NotificationPermissionStatus.DENIED_CAN_RETRY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.markRequestLaunched()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else saveChoice()
            }
            NotificationPermissionStatus.SETTINGS_REQUIRED -> showSettings = true
        }
    }

    fun consentThenContinue() {
        val current = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
        if (current == null) {
            message = R.string.lesson_reminder_preferences_unavailable
            return
        }
        scope.launch {
            try {
                val updated = graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = true,
                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                        categories = mapOf("lesson_reminders" to true),
                    ),
                    owner,
                )
                if (updated.allows("lesson_reminders")) requestPermissionThenSave()
                else message = R.string.lesson_reminder_save_failed
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                runCatching { graph.notificationPreferences.refresh(owner) }
                message = R.string.lesson_reminder_save_failed
            }
        }
    }

    val choice = (choiceRead as? ProtectedRead.Ready<LessonReminderChoice?>)?.value
    val preferences = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
    val enabled = choice != null
    val eligible = event.status == EventStatus.SCHEDULED && event.start != null && event.remindersEnabled
    val permissionStatus = remember(permissionRevision, activity) { activity?.let { permission.state(it).status } }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            Text(stringResource(R.string.lesson_reminder_title), style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    enabled -> stringResource(R.string.lesson_reminder_enabled, choice?.leadMinutes ?: 15)
                    !eligible -> stringResource(R.string.lesson_reminder_unavailable)
                    else -> stringResource(R.string.lesson_reminder_default, LessonReminderChoice.DEFAULT_LEAD_MINUTES)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                choiceRead is ProtectedRead.Unreadable || preferenceRead is ProtectedRead.Unreadable ->
                    Text(stringResource(R.string.local_data_unreadable), color = MaterialTheme.colorScheme.error)
                enabled -> OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            graph.lessonReminderChoices.disable(event.id, owner)
                            message = R.string.lesson_reminder_disabled
                        } catch (failure: CancellationException) { throw failure }
                        catch (_: Exception) { message = R.string.lesson_reminder_save_failed }
                    }
                }) { Text(stringResource(R.string.lesson_reminder_turn_off)) }
                eligible -> Button(
                    onClick = {
                        if (preferences?.allows("lesson_reminders") == true) requestPermissionThenSave()
                        else showConsent = true
                    },
                    enabled = choiceRead is ProtectedRead.Ready && preferenceRead is ProtectedRead.Ready,
                ) { Text(stringResource(R.string.lesson_reminder_enable)) }
            }
            message?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == R.string.lesson_reminder_save_failed || it == R.string.lesson_reminder_permission_denied)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled && permissionStatus == NotificationPermissionStatus.SETTINGS_REQUIRED) {
                TextButton(onClick = { showSettings = true }) {
                    Text(stringResource(R.string.lesson_reminder_open_settings))
                }
            }
        }
    }

    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        title = { Text(stringResource(R.string.lesson_reminder_consent_title)) },
        text = { Text(stringResource(R.string.lesson_reminder_consent_body)) },
        confirmButton = {
            TextButton(onClick = { showConsent = false; consentThenContinue() }) {
                Text(stringResource(R.string.lesson_reminder_consent_confirm))
            }
        },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text(stringResource(R.string.cancel)) } },
    )

    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text(stringResource(R.string.lesson_reminder_permission_title)) },
        text = { Text(stringResource(R.string.lesson_reminder_permission_settings_body)) },
        confirmButton = {
            TextButton(onClick = {
                showSettings = false
                context.startActivity(permission.appNotificationSettings(context))
            }) { Text(stringResource(R.string.lesson_reminder_open_settings)) }
        },
        dismissButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
