package org.athar.app.notifications

import android.Manifest
import android.app.Activity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.*

data class ReminderCenterCounts(
    val lessons: Int,
    val quran: Int,
    val adhkar: Int,
    val builtin: Int,
    val custom: Int,
    val khatmah: Int = 0,
    val night: Int = 0,
) {
    val total: Int get() = lessons + quran + adhkar + builtin + custom + khatmah + night
    val activeTypes: Int get() = listOf(lessons,quran,adhkar,builtin,custom,khatmah,night).count { it > 0 }
}

@Composable
fun ReminderCenterOverview(
    graph: AppGraph,
    owner: Long,
    open: (String) -> Unit,
    preferenceRead: ProtectedRead<NotificationPreferences>,
) {
    val choices by remember(owner) { graph.lessonReminderChoices.observeAll(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val quran by remember(owner) { graph.quranWird.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val adhkar by remember(owner) { graph.adhkarReminders.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val builtin by remember(owner) { graph.builtinRoutines.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val custom by remember(owner) { graph.customRoutines.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val khatmah by remember(owner) { graph.khatmahReminders.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val night by remember(owner) { graph.nightReminders.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)

    val context = LocalContext.current
    val activity = remember(context) { context.findReminderCenterActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionRevision by remember { mutableIntStateOf(0) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }
    var message by rememberSaveable(owner) { mutableStateOf<Int?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionState = remember(permissionRevision, activity) { activity?.let(permission::state) }
    val prefs = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
    val lessonValues = (choices as? ProtectedRead.Ready)?.value.orEmpty()
    val quranState = (quran as? ProtectedRead.Ready)?.value
    val adhkarState = (adhkar as? ProtectedRead.Ready)?.value
    val builtinState = (builtin as? ProtectedRead.Ready)?.value
    val customState = (custom as? ProtectedRead.Ready)?.value
    val khatmahRule = (khatmah as? ProtectedRead.Ready)?.value
    val nightRule = (night as? ProtectedRead.Ready)?.value
    val counts = ReminderCenterCounts(
        lessons = lessonValues.size,
        quran = if (quranState?.rule?.enabled == true) 1 else 0,
        adhkar = adhkarState?.rules?.count { it.enabled } ?: 0,
        builtin = builtinState?.rules?.count { it.enabled } ?: 0,
        custom = customState?.rules?.count { it.enabled } ?: 0,
        khatmah = if (khatmahRule?.enabled == true) 1 else 0,
        night = if (nightRule?.enabled == true) 1 else 0,
    )
    val unreadable = listOf(choices,quran,adhkar,builtin,custom,khatmah,night,preferenceRead).any { it == ProtectedRead.Unreadable }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRevision++
        message = if (granted) R.string.reminder_permission_ready else R.string.routine_permission_denied
    }

    fun requestAndroidPermission() {
        val host = activity ?: return
        when (permission.state(host).status) {
            NotificationPermissionStatus.NOT_REQUIRED,
            NotificationPermissionStatus.GRANTED -> message = R.string.reminder_permission_ready
            NotificationPermissionStatus.READY_TO_REQUEST,
            NotificationPermissionStatus.DENIED_CAN_RETRY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.markRequestLaunched()
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            NotificationPermissionStatus.SETTINGS_REQUIRED ->
                context.startActivity(permission.appNotificationSettings(context))
        }
    }

    fun setMaster(enabled: Boolean) {
        val current = prefs ?: run { message = R.string.routine_preferences_unavailable; return }
        busy = true
        scope.launch {
            try {
                graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = enabled,
                        consentVersion = if (enabled && !current.consent.current)
                            NotificationPreferenceContract.CONSENT_VERSION else null,
                    ),
                    owner,
                )
                message = if (enabled) R.string.reminder_master_resumed else R.string.reminder_master_paused
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = R.string.reminder_center_update_failed
            } finally {
                busy = false
            }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminder_center_overview), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.reminder_center_counts, counts.total, counts.activeTypes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AtharStatusPill(
                    stringResource(
                        if (prefs?.masterEnabled == true && prefs.consent.current)
                            R.string.reminder_master_active else R.string.reminder_master_paused_status
                    ),
                )
            }

            val permissionLabel = when (permissionState?.status) {
                NotificationPermissionStatus.NOT_REQUIRED,
                NotificationPermissionStatus.GRANTED -> R.string.reminder_permission_ready
                NotificationPermissionStatus.READY_TO_REQUEST,
                NotificationPermissionStatus.DENIED_CAN_RETRY -> R.string.reminder_permission_needs_action
                NotificationPermissionStatus.SETTINGS_REQUIRED -> R.string.reminder_permission_settings
                null -> R.string.reminder_permission_unknown
            }
            Text(stringResource(permissionLabel), style = MaterialTheme.typography.bodyMedium)
            if (permissionState?.granted == false) {
                TextButton(onClick = ::requestAndroidPermission) {
                    Text(stringResource(
                        if (permissionState.status == NotificationPermissionStatus.SETTINGS_REQUIRED)
                            R.string.lesson_reminder_open_settings else R.string.reminder_permission_enable
                    ))
                }
            }

            if (prefs != null) {
                Button(
                    onClick = { setMaster(!(prefs.masterEnabled && prefs.consent.current)) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(
                        if (prefs.masterEnabled && prefs.consent.current)
                            R.string.reminder_pause_all else R.string.reminder_resume_all
                    ))
                }
            }

            if (unreadable) MessagePanel(stringResource(R.string.reminder_center_partial_unreadable), true)
            if (counts.total == 0 && !unreadable) QuietEmptyState(stringResource(R.string.reminder_center_empty))

            ReminderCountRow(R.string.lesson_reminder_title, counts.lessons) { open("upcoming") }
            ReminderCountRow(R.string.quran_wird_title, counts.quran) { open("quran") }
            ReminderCountRow(R.string.adhkar, counts.adhkar) { open("adhkar") }
            ReminderCountRow(R.string.salawat_friday_summary, counts.builtin)
            ReminderCountRow(R.string.custom_routines_title, counts.custom)
            ReminderCountRow(R.string.khatmah, counts.khatmah)
            if (khatmahRule?.enabled == true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Text(
                        stringResource(R.string.khatmah_reminder_time, khatmahRule.time),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        scope.launch {
                            try { graph.khatmahReminders.disable(owner) }
                            catch (failure: CancellationException) { throw failure }
                            catch (_: Exception) { message = R.string.reminder_center_update_failed }
                        }
                    }) { Text(stringResource(R.string.khatmah_reminder_disable)) }
                }
            }
            ReminderCountRow(R.string.night_prayer, counts.night) { open("night-prayer") }
            if (nightRule?.enabled == true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Text(
                        stringResource(
                            R.string.reminder_night_active_detail,
                            if (nightRule.mode == org.athar.app.nightprayer.NightReminderMode.LAST_THIRD)
                                stringResource(R.string.last_third)
                            else nightRule.fixedTime.orEmpty(),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        scope.launch {
                            try { graph.nightReminders.disable(owner) }
                            catch (failure: CancellationException) { throw failure }
                            catch (_: Exception) { message = R.string.reminder_center_update_failed }
                        }
                    }) { Text(stringResource(R.string.night_reminder_disable)) }
                }
            }

            lessonValues.forEach { choice ->
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(AtharSpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        Text(
                            stringResource(R.string.reminder_lesson_item, choice.lessonId),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { open("lessons/" + choice.lessonId) }) {
                            Text(stringResource(R.string.open))
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        graph.lessonReminders.disable(choice.lessonId, owner)
                                    } catch (failure: CancellationException) { throw failure }
                                    catch (_: Exception) { message = R.string.reminder_center_update_failed }
                                }
                            },
                        ) { Text(stringResource(R.string.lesson_reminder_turn_off)) }
                    }
                }
            }

            if (quranState?.rule?.enabled == true) {
                Text(
                    stringResource(
                        R.string.reminder_quran_active_detail,
                        quranState.rule.targetAyahs,
                        quranState.rule.time,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            adhkarState?.rules?.filter { it.enabled }?.forEach { rule ->
                Text(
                    stringResource(R.string.reminder_adhkar_active_detail, rule.category, rule.time),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            message?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == R.string.reminder_center_update_failed || it == R.string.routine_permission_denied)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReminderCountRow(
    label: Int,
    count: Int,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        if (onOpen != null) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.manage)) }
        }
    }
}

private tailrec fun Context.findReminderCenterActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findReminderCenterActivity()
    else -> null
}
