package org.athar.app.nightprayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.ui.*
import java.time.LocalDate
import java.time.ZoneId

private data class NightPlanSelection(
    val startIndex: Int,
    val endIndex: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
) {
    val verseCount: Int get() = endIndex - startIndex + 1
    val reference: String
        get() = "$startSurah:$startAyah — $endSurah:$endAyah"
}

@Composable
fun NightPlanScreen(graph: AppGraph) {
    val owner = remember { requireNotNull(graph.session.userId) }
    val repository = remember(owner) {
        NightVersePlanRepository(
            dao = graph.database.dao(),
            cipher = graph.cipher,
            session = graph.session,
        )
    }
    val stored by remember(owner, repository) {
        repository.observe(owner).protectedReads()
    }.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)

    var byTarget by rememberSaveable { mutableStateOf(true) }
    var fromSurah by rememberSaveable { mutableStateOf("1") }
    var fromAyah by rememberSaveable { mutableStateOf("1") }
    var targetCount by rememberSaveable { mutableStateOf("100") }
    var toSurah by rememberSaveable { mutableStateOf("1") }
    var toAyah by rememberSaveable { mutableStateOf("7") }
    var preview by remember { mutableStateOf<NightPlanSelection?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var savedFeedback by remember { mutableStateOf(false) }
    var recordPlan by remember { mutableStateOf<NightVersePlan?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("night-plan-screen"),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "plan-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_plan_title))
            Text(
                stringResource(R.string.night_plan_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "plan-form", contentType = "form") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        FilterChip(
                            selected = byTarget,
                            onClick = {
                                byTarget = true
                                preview = null
                            },
                            label = { Text(stringResource(R.string.target_count)) },
                        )
                        FilterChip(
                            selected = !byTarget,
                            onClick = {
                                byTarget = false
                                preview = null
                            },
                            label = { Text(stringResource(R.string.night_range)) },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                        PlanNumberField(
                            value = fromSurah,
                            change = {
                                fromSurah = it
                                preview = null
                            },
                            label = stringResource(R.string.start_surah),
                            modifier = Modifier.weight(1f),
                        )
                        PlanNumberField(
                            value = fromAyah,
                            change = {
                                fromAyah = it
                                preview = null
                            },
                            label = stringResource(R.string.start_ayah),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (byTarget) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            listOf(10, 100, 1000).forEach { value ->
                                FilterChip(
                                    selected = targetCount == value.toString(),
                                    onClick = {
                                        targetCount = value.toString()
                                        preview = null
                                    },
                                    label = { Text(value.toString()) },
                                )
                            }
                        }
                        PlanNumberField(
                            value = targetCount,
                            change = {
                                targetCount = it
                                preview = null
                            },
                            label = stringResource(R.string.target_count),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                            PlanNumberField(
                                value = toSurah,
                                change = {
                                    toSurah = it
                                    preview = null
                                },
                                label = stringResource(R.string.end_surah),
                                modifier = Modifier.weight(1f),
                            )
                            PlanNumberField(
                                value = toAyah,
                                change = {
                                    toAyah = it
                                    preview = null
                                },
                                label = stringResource(R.string.end_ayah),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val index = graph.quran.data().index
                                    val range = if (byTarget) {
                                        index.target(
                                            fromSurah.toInt(),
                                            fromAyah.toInt(),
                                            targetCount.toInt(),
                                        )
                                    } else {
                                        index.range(
                                            fromSurah.toInt(),
                                            fromAyah.toInt(),
                                            toSurah.toInt(),
                                            toAyah.toInt(),
                                        )
                                    }
                                    preview = NightPlanSelection(
                                        startIndex = range.start.globalIndex,
                                        endIndex = range.end.globalIndex,
                                        startSurah = range.start.surah,
                                        startAyah = range.start.ayah,
                                        endSurah = range.end.surah,
                                        endAyah = range.end.ayah,
                                    )
                                    error = false
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    preview = null
                                    error = true
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.calculate))
                    }

                    preview?.let { selected ->
                        MessagePanel(
                            stringResource(
                                R.string.night_plan_preview,
                                selected.reference,
                                selected.verseCount,
                            ),
                        )
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        repository.save(
                                            owner = owner,
                                            startIndex = selected.startIndex,
                                            endIndex = selected.endIndex,
                                            startSurah = selected.startSurah,
                                            startAyah = selected.startAyah,
                                            endSurah = selected.endSurah,
                                            endAyah = selected.endAyah,
                                        )
                                        savedFeedback = true
                                        error = false
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        error = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.night_save_plan))
                        }
                    }

                    if (error) {
                        Text(
                            stringResource(R.string.range_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item(key = "saved-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_saved_plan))
        }

        when (val planState = stored) {
            ProtectedRead.Loading -> {
                item(key = "saved-loading", contentType = "state") {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            ProtectedRead.Unreadable -> {
                item(key = "saved-unreadable", contentType = "state") {
                    MessagePanel(
                        stringResource(R.string.local_data_unreadable),
                        true,
                    )
                }
            }
            is ProtectedRead.Ready -> {
                val plan = planState.value
                if (plan == null) {
                    item(key = "saved-empty", contentType = "state") {
                        QuietEmptyState(stringResource(R.string.night_plan_empty))
                    }
                } else {
                    item(key = "saved-plan", contentType = "plan") {
                        NightSavedPlanCard(
                            plan = plan,
                            busy = busy,
                            copy = {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE,
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        context.getString(R.string.night_plan_reference),
                                        "${plan.startSurah}:${plan.startAyah} — " +
                                            "${plan.endSurah}:${plan.endAyah} · " +
                                            "${plan.verseCount}",
                                    ),
                                )
                            },
                            record = { recordPlan = plan },
                            clear = {
                                busy = true
                                scope.launch {
                                    try {
                                        repository.clear(owner)
                                        savedFeedback = false
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        error = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        if (savedFeedback) {
            item(key = "saved-feedback", contentType = "state") {
                MessagePanel(stringResource(R.string.saved))
            }
        }
    }

    recordPlan?.let { plan ->
        RecordCompletedPlanDialog(
            plan = plan,
            busy = busy,
            dismiss = { recordPlan = null },
            save = { nightDate, zone, note ->
                busy = true
                scope.launch {
                    try {
                        val parsedDate = LocalDate.parse(nightDate)
                        val parsedZone = ZoneId.of(zone)
                        require(parsedDate.year >= 1900)
                        val body = JsonObject().apply {
                            addProperty("night_date", parsedDate.toString())
                            addProperty("timezone", parsedZone.id)
                            addProperty("boundary_source", "manual")
                            addProperty("entry_type", "range")
                            addProperty("start_index", plan.startIndex)
                            addProperty("end_index", plan.endIndex)
                            addProperty("note", note)
                        }
                        graph.nightPrayer.save(body, expectedOwner = owner)
                        recordPlan = null
                        savedFeedback = true
                        error = false
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
}

@Composable
private fun NightSavedPlanCard(
    plan: NightVersePlan,
    busy: Boolean,
    copy: () -> Unit,
    record: () -> Unit,
    clear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(
                "${plan.startSurah}:${plan.startAyah} — " +
                    "${plan.endSurah}:${plan.endAyah}",
                style = MaterialTheme.typography.titleLarge,
            )
            AtharStatusPill(
                stringResource(R.string.night_plan_verse_count, plan.verseCount),
            )
            Text(
                stringResource(R.string.night_plan_separate_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = record,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.night_record_completed))
            }
            OutlinedButton(
                onClick = copy,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.night_copy_reference))
            }
            TextButton(
                onClick = clear,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.night_clear_plan))
            }
        }
    }
}

@Composable
private fun RecordCompletedPlanDialog(
    plan: NightVersePlan,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (String, String, String) -> Unit,
) {
    var nightDate by rememberSaveable(plan.localVersion) { mutableStateOf("") }
    var zone by rememberSaveable(plan.localVersion) {
        mutableStateOf(NightRules.serverTimezone(ZoneId.systemDefault()))
    }
    var note by rememberSaveable(plan.localVersion) { mutableStateOf("") }
    var localError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.night_record_completed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                Text(
                    stringResource(R.string.night_record_date_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                DateField(
                    value = nightDate,
                    change = {
                        nightDate = it
                        localError = false
                    },
                    label = stringResource(R.string.night_maghrib_date),
                    latest = LocalDate.now(),
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = {
                        zone = it.take(80)
                        localError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.timezone)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.private_note)) },
                    maxLines = 4,
                )
                if (localError) {
                    Text(
                        stringResource(R.string.invalid_input),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        require(nightDate.isNotBlank())
                        val parsed = LocalDate.parse(nightDate)
                        require(parsed <= LocalDate.now())
                        ZoneId.of(zone)
                        save(parsed.toString(), zone, note)
                    } catch (_: Exception) {
                        localError = true
                    }
                },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.night_record_completed))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PlanNumberField(
    value: String,
    change: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { change(it.filter(Char::isDigit).take(6)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
