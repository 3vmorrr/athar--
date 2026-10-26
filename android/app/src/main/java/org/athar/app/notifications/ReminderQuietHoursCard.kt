package org.athar.app.notifications

import android.app.TimePickerDialog
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

@Composable
fun ReminderQuietHoursCard(
    graph: AppGraph,
    owner: Long,
) {
    val context = LocalContext.current
    val read by remember(owner) { graph.reminderGlobalPolicy.observe(owner) }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    val policy = (read as? ProtectedRead.Ready<ReminderGlobalPolicy>)?.value
    var enabled by rememberSaveable(owner) { mutableStateOf(policy?.quietEnabled ?: false) }
    var startHour by rememberSaveable(owner) { mutableIntStateOf(policy?.let { LocalTime.parse(it.quietStart).hour } ?: 22) }
    var startMinute by rememberSaveable(owner) { mutableIntStateOf(policy?.let { LocalTime.parse(it.quietStart).minute } ?: 0) }
    var endHour by rememberSaveable(owner) { mutableIntStateOf(policy?.let { LocalTime.parse(it.quietEnd).hour } ?: 7) }
    var endMinute by rememberSaveable(owner) { mutableIntStateOf(policy?.let { LocalTime.parse(it.quietEnd).minute } ?: 0) }
    var busy by rememberSaveable(owner) { mutableStateOf(false) }
    var saved by rememberSaveable(owner) { mutableStateOf(false) }
    var failed by rememberSaveable(owner) { mutableStateOf(false) }

    LaunchedEffect(policy) {
        policy?.let {
            enabled = it.quietEnabled
            LocalTime.parse(it.quietStart).let { value -> startHour=value.hour;startMinute=value.minute }
            LocalTime.parse(it.quietEnd).let { value -> endHour=value.hour;endMinute=value.minute }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(stringResource(R.string.reminder_quiet_hours), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.reminder_quiet_hours_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (read) {
                ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
                is ProtectedRead.Ready -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(if(enabled)R.string.reminder_quiet_enabled else R.string.reminder_quiet_disabled),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked=enabled,
                            onCheckedChange={enabled=it;saved=false;failed=false},
                            enabled=!busy,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        OutlinedButton(
                            onClick={
                                TimePickerDialog(context,{_,h,m->startHour=h;startMinute=m;saved=false},startHour,startMinute,true).show()
                            },
                            enabled=!busy,
                            modifier=Modifier.weight(1f),
                        ){
                            Text(stringResource(R.string.reminder_quiet_start,"%02d:%02d".format(startHour,startMinute)))
                        }
                        OutlinedButton(
                            onClick={
                                TimePickerDialog(context,{_,h,m->endHour=h;endMinute=m;saved=false},endHour,endMinute,true).show()
                            },
                            enabled=!busy,
                            modifier=Modifier.weight(1f),
                        ){
                            Text(stringResource(R.string.reminder_quiet_end,"%02d:%02d".format(endHour,endMinute)))
                        }
                    }
                    Text(
                        stringResource(R.string.routine_timezone, ZoneId.systemDefault().id),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick={
                            busy=true;saved=false;failed=false
                            scope.launch {
                                try {
                                    graph.reminderGlobalPolicy.save(
                                        owner,
                                        enabled,
                                        LocalTime.of(startHour,startMinute),
                                        LocalTime.of(endHour,endMinute),
                                        ZoneId.systemDefault(),
                                    )
                                    saved=true
                                } catch (failure:CancellationException) {
                                    throw failure
                                } catch (_:Exception) {
                                    failed=true
                                } finally {
                                    busy=false
                                }
                            }
                        },
                        enabled=!busy && !(startHour==endHour && startMinute==endMinute),
                        modifier=Modifier.fillMaxWidth(),
                    ){Text(stringResource(R.string.save))}
                    if(saved) Text(stringResource(R.string.reminder_quiet_saved),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(failed) Text(stringResource(R.string.reminder_quiet_failed),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
