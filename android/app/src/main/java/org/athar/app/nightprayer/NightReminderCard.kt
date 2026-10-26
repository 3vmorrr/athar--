package org.athar.app.nightprayer

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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.*
import org.athar.app.notifications.*

@Composable
fun NightReminderCard(graph: AppGraph) {
    val owner = graph.session.userId ?: return
    val ruleRead by remember(owner) { graph.nightReminders.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val preferenceRead by remember(owner) { graph.notificationPreferences.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val rule = (ruleRead as? ProtectedRead.Ready<NightReminderRule?>)?.value
    val notificationPreferences = (preferenceRead as? ProtectedRead.Ready<NotificationPreferences>)?.value
    val context = LocalContext.current
    val activity = remember(context) { context.findNightReminderActivity() }
    val permission = remember(context) { NotificationPermissionController(context) }
    val scope = rememberCoroutineScope()

    var prayerPreferences by remember(owner) { mutableStateOf<NightPrayerPreferences?>(rule?.preferences) }
    var preferenceUnavailable by rememberSaveable(owner) { mutableStateOf(false) }
    var exemptions by remember(owner) { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var exemptionsReady by rememberSaveable(owner) { mutableStateOf(false) }
    var exemptionsUnreadable by rememberSaveable(owner) { mutableStateOf(false) }
    var mode by rememberSaveable(owner) { mutableStateOf(rule?.mode ?: NightReminderMode.LAST_THIRD) }
    var hour by rememberSaveable(owner) { mutableIntStateOf(rule?.fixedTime?.let { LocalTime.parse(it).hour } ?: 2) }
    var minute by rememberSaveable(owner) { mutableIntStateOf(rule?.fixedTime?.let { LocalTime.parse(it).minute } ?: 0) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }
    var message by rememberSaveable(owner) { mutableStateOf<Int?>(null) }
    var showConsent by rememberSaveable(owner) { mutableStateOf(false) }
    var showSettings by rememberSaveable(owner) { mutableStateOf(false) }

    LaunchedEffect(rule?.revision) {
        rule?.let {
            mode = it.mode
            it.fixedTime?.let { value ->
                val parsed = LocalTime.parse(value)
                hour = parsed.hour
                minute = parsed.minute
            }
            if (prayerPreferences == null) prayerPreferences = it.preferences
        }
    }
    LaunchedEffect(owner) {
        try {
            val envelope = graph.nightPreferences.get(owner)
            prayerPreferences = envelope.values ?: rule?.preferences
            preferenceUnavailable = prayerPreferences == null
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) {
            prayerPreferences = rule?.preferences
            preferenceUnavailable = prayerPreferences == null
        }
        try {
            exemptions = graph.nightPrayer.localExemptions(owner)
            exemptionsReady = true
            exemptionsUnreadable = false
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) {
            exemptionsReady = false
            exemptionsUnreadable = true
        }
        try { graph.notificationPreferences.refresh(owner) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { }
    }

    val candidateRule = prayerPreferences?.let { prefs ->
        NightReminderRule(
            enabled = true,
            mode = mode,
            fixedTime = if (mode == NightReminderMode.FIXED_TIME) LocalTime.of(hour,minute).toString() else null,
            timezone = prefs.timezone,
            preferences = prefs,
            revision = 1,
            activatedAtMillis = 1,
        )
    }
    val preview = remember(candidateRule,exemptions,exemptionsReady) {
        if (candidateRule == null || !exemptionsReady) null
        else NightReminderPlanner.next(candidateRule,exemptions,Instant.now(),"Night Prayer","")
    }
    val schedulable = preview is NightReminderPlanResult.Ready

    fun saveRule() {
        val prefs = prayerPreferences ?: run { message = R.string.night_reminder_preferences_missing; return }
        if (!exemptionsReady) { message = R.string.local_data_unreadable; return }
        busy = true
        scope.launch {
            try {
                val result = graph.nightReminders.saveRule(
                    owner = owner,
                    mode = mode,
                    fixedTime = if (mode == NightReminderMode.FIXED_TIME) LocalTime.of(hour,minute) else null,
                    preferences = prefs,
                    exemptions = exemptions,
                )
                message = if (result is NightReminderPlanResult.Ready)
                    R.string.night_reminder_saved else unavailableString((result as NightReminderPlanResult.Unavailable).reason)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { message = R.string.night_reminder_failed }
            finally { busy = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveRule() else message = R.string.routine_permission_denied
    }

    fun permissionThenSave() {
        val host = activity ?: run { message = R.string.night_reminder_failed; return }
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
        val current = notificationPreferences ?: run { message = R.string.routine_preferences_unavailable; return }
        scope.launch {
            try {
                val updated = graph.notificationPreferences.update(
                    NotificationPreferencePatch(
                        expectedRevision = current.revision,
                        masterEnabled = true,
                        consentVersion = NotificationPreferenceContract.CONSENT_VERSION,
                        categories = mapOf("night_prayer" to true),
                    ),
                    owner,
                )
                if (updated.allows("night_prayer")) permissionThenSave()
                else message = R.string.night_reminder_failed
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { message = R.string.night_reminder_failed }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(AtharSpacing.Lg),verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            Text(stringResource(R.string.night_reminder_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.night_reminder_explainer),style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                FilterChip(mode==NightReminderMode.LAST_THIRD,{mode=NightReminderMode.LAST_THIRD},label={Text(stringResource(R.string.last_third))},enabled=!busy)
                FilterChip(mode==NightReminderMode.FIXED_TIME,{mode=NightReminderMode.FIXED_TIME},label={Text(stringResource(R.string.night_reminder_fixed_time))},enabled=!busy)
            }
            if (mode == NightReminderMode.FIXED_TIME) {
                TextButton(onClick={TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()},enabled=!busy) {
                    Text(stringResource(R.string.night_reminder_time,"%02d:%02d".format(hour,minute)))
                }
            }
            prayerPreferences?.let { prefs ->
                Text(stringResource(R.string.night_reminder_source,prefs.city.ifBlank { "—" },prefs.calculationMethod,prefs.timezone),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (preferenceUnavailable) MessagePanel(stringResource(R.string.night_reminder_preferences_missing),true)
            if (exemptionsUnreadable) MessagePanel(stringResource(R.string.local_data_unreadable),true)
            when (val value = preview) {
                is NightReminderPlanResult.Ready -> {
                    val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of(requireNotNull(prayerPreferences).timezone))
                    Text(stringResource(R.string.night_reminder_next,formatter.format(Instant.ofEpochMilli(value.spec.scheduledAtMillis))),style=MaterialTheme.typography.bodySmall)
                }
                is NightReminderPlanResult.Unavailable -> Text(stringResource(unavailableString(value.reason)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                null -> Unit
            }
            when (ruleRead) {
                ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable),true)
                is ProtectedRead.Ready -> {
                    val saveAction = {
                        if(notificationPreferences?.allows("night_prayer")==true)permissionThenSave() else showConsent=true
                    }
                    if (rule?.enabled == true) {
                        Button(onClick=saveAction,enabled=!busy&&schedulable&&notificationPreferences!=null&&!exemptionsUnreadable,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.save))}
                        OutlinedButton(onClick={
                            busy=true
                            scope.launch {
                                try { graph.nightReminders.disable(owner);message=R.string.night_reminder_disabled }
                                catch (failure:CancellationException){throw failure}
                                catch (_:Exception){message=R.string.night_reminder_failed}
                                finally{busy=false}
                            }
                        },enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.night_reminder_disable))}
                    } else {
                        Button(onClick=saveAction,enabled=!busy&&schedulable&&notificationPreferences!=null&&!exemptionsUnreadable,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.night_reminder_enable))}
                    }
                }
            }
            message?.let {
                Text(stringResource(it),style=MaterialTheme.typography.bodySmall,color=if(it==R.string.night_reminder_failed||it==R.string.routine_permission_denied)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if(showConsent) AlertDialog(
        onDismissRequest={showConsent=false},
        title={Text(stringResource(R.string.night_reminder_consent_title))},
        text={Text(stringResource(R.string.night_reminder_consent_body))},
        confirmButton={TextButton(onClick={showConsent=false;consentThenSave()}){Text(stringResource(R.string.night_reminder_consent_confirm))}},
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

private fun unavailableString(reason: NightReminderUnavailable): Int = when(reason) {
    NightReminderUnavailable.DISABLED -> R.string.night_reminder_disabled
    NightReminderUnavailable.PREFERENCES_MISSING -> R.string.night_reminder_preferences_missing
    NightReminderUnavailable.CALCULATION_UNAVAILABLE -> R.string.night_reminder_calculation_unavailable
    NightReminderUnavailable.NO_ELIGIBLE_NIGHT -> R.string.night_reminder_no_eligible_night
}

private tailrec fun Context.findNightReminderActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findNightReminderActivity()
    else -> null
}
