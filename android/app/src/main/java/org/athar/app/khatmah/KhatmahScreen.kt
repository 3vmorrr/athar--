package org.athar.app.khatmah

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.ui.*
import java.time.LocalDate
import java.time.ZoneOffset

private data class KhatmahOperationState(
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

private class KhatmahViewModel(
    private val graph: AppGraph,
    private val owner: Long,
) : ViewModel() {
    val repository = graph.khatmah
    val plan = repository
        .observe(owner)
        .protectedReads()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProtectedRead.Loading,
        )

    private val mutableOperation = MutableStateFlow(KhatmahOperationState())
    val operation = mutableOperation.asStateFlow()

    init {
        refresh()
    }

    fun clearFeedback() {
        mutableOperation.value = KhatmahOperationState()
    }

    fun refresh() = runOperation(markSaved = false) {
        val current = repository.saved(owner)
        if (current?.pending == true && !current.conflict) {
            repository.sync(owner)
        }
        repository.refresh(owner)
    }

    fun save(
        startDate: String,
        targetDate: String,
        completedPages: Int,
    ) = runOperation {
        repository.save(
            startDate = startDate,
            targetDate = targetDate,
            completedPages = completedPages,
            expectedOwner = owner,
        )
    }

    fun action(
        action: String,
        restartTargetDate: String? = null,
    ) = runOperation {
        repository.action(
            action = action,
            restartTargetDate = restartTargetDate,
            expectedOwner = owner,
        )
    }

    fun sync() = runOperation {
        repository.sync(owner)
    }

    fun useServer() = runOperation {
        repository.useServer(owner)
    }

    fun keepDevice() = runOperation {
        repository.keepDevice(owner)
    }

    private fun runOperation(
        markSaved: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (mutableOperation.value.busy) return
        viewModelScope.launch {
            mutableOperation.value = KhatmahOperationState(busy = true)
            try {
                graph.session.requireVerifiedOwner(owner)
                block()
                graph.session.requireVerifiedOwner(owner)
                mutableOperation.value = KhatmahOperationState(
                    saved = markSaved,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IllegalArgumentException) {
                mutableOperation.value = KhatmahOperationState(
                    error = "invalid",
                )
            } catch (failure: IllegalStateException) {
                mutableOperation.value = KhatmahOperationState(
                    error = "invalid",
                )
            } catch (_: Exception) {
                mutableOperation.value = KhatmahOperationState(
                    error = "network",
                )
            }
        }
    }
}

@Composable
fun KhatmahScreen(graph: AppGraph) {
    val owner = requireNotNull(graph.session.userId)
    val vm: KhatmahViewModel = viewModel(
        key = "khatmah:$owner",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                KhatmahViewModel(graph, owner) as T
        },
    )
    val protectedPlan by vm.plan.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()

    when (val state = protectedPlan) {
        ProtectedRead.Loading -> ContentLoadingSkeleton()
        ProtectedRead.Unreadable -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(AtharSpacing.Xl),
            ) {
                MessagePanel(
                    stringResource(R.string.local_data_unreadable),
                    true,
                ) {
                    TextButton(onClick = vm::refresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        is ProtectedRead.Ready -> {
            KhatmahContent(
                graph = graph,
                plan = state.value,
                repository = vm.repository,
                operation = operation,
                save = vm::save,
                action = vm::action,
                sync = vm::sync,
                useServer = vm::useServer,
                keepDevice = vm::keepDevice,
                refresh = vm::refresh,
                clearFeedback = vm::clearFeedback,
            )
        }
    }
}

@Composable
private fun KhatmahContent(
    graph: AppGraph,
    plan: KhatmahPlanState?,
    repository: KhatmahRepository,
    operation: KhatmahOperationState,
    save: (String, String, Int) -> Unit,
    action: (String, String?) -> Unit,
    sync: () -> Unit,
    useServer: () -> Unit,
    keepDevice: () -> Unit,
    refresh: () -> Unit,
    clearFeedback: () -> Unit,
) {
    val today = remember { LocalDate.now(ZoneOffset.UTC) }
    var editing by rememberSaveable(plan?.localVersion) {
        mutableStateOf(plan == null)
    }
    var startDate by rememberSaveable(plan?.localVersion) {
        mutableStateOf(plan?.startDate ?: today.toString())
    }
    var targetDate by rememberSaveable(plan?.localVersion) {
        mutableStateOf(
            plan?.targetDate ?: today.plusDays(30).toString(),
        )
    }
    var completedText by rememberSaveable(plan?.localVersion) {
        mutableStateOf(plan?.completedPages?.toString() ?: "0")
    }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var restartTarget by rememberSaveable {
        mutableStateOf(today.plusDays(30).toString())
    }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    val completed = completedText.toIntOrNull()
    val formValid = runCatching {
        val start = LocalDate.parse(startDate)
        val target = LocalDate.parse(targetDate)
        val unchangedOverdueTarget = plan != null &&
            targetDate == plan.targetDate
        completed != null &&
            completed in 0..604 &&
            !target.isBefore(start) &&
            (unchangedOverdueTarget || !target.isBefore(today)) &&
            !start.isBefore(today.minusYears(5)) &&
            !start.isAfter(today.plusYears(1)) &&
            !target.isAfter(start.plusYears(5))
    }.getOrDefault(false)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("khatmah-screen"),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "khatmah-hero", contentType = "hero") {
            AtharHeroSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
            ) {
                Text(
                    stringResource(R.string.khatmah),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    when {
                        plan == null -> stringResource(R.string.khatmah_empty_intro)
                        plan.status == "finished" ->
                            stringResource(R.string.khatmah_finished_summary)
                        plan.status == "paused" ->
                            stringResource(R.string.khatmah_paused_summary)
                        else -> stringResource(R.string.khatmah_active_summary)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (plan?.pending == true) {
                    AtharStatusPill(
                        stringResource(R.string.khatmah_pending),
                        emphasized = true,
                    )
                }
                if (plan?.conflict == true) {
                    AtharStatusPill(
                        stringResource(R.string.khatmah_conflict),
                        emphasized = true,
                    )
                }
            }
        }

        if (plan?.conflict == true) {
            item(key = "khatmah-conflict", contentType = "conflict") {
                KhatmahConflictPanel(
                    device = plan,
                    server = plan.serverConflict,
                    busy = operation.busy,
                    useServer = useServer,
                    keepDevice = keepDevice,
                )
            }
        }

        if (plan != null && !editing) {
            val metrics = remember(plan) { repository.metrics(plan) }

            item(key = "khatmah-workload", contentType = "metrics") {
                KhatmahWorkload(plan, metrics)
            }
            item(key = "khatmah-reminder", contentType = "reminder") {
                Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                    KhatmahReminderCard(graph, plan, metrics)
                }
            }

            item(key = "khatmah-actions", contentType = "actions") {
                Column(
                    Modifier.padding(horizontal = AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    if (!plan.conflict) {
                        Button(
                            onClick = { editing = true },
                            enabled = !operation.busy && plan.status != "finished",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.edit))
                        }

                        when (plan.status) {
                            "active" -> {
                                OutlinedButton(
                                    onClick = { action("pause", null) },
                                    enabled = !operation.busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.khatmah_pause))
                                }
                            }
                            "paused" -> {
                                OutlinedButton(
                                    onClick = { action("resume", null) },
                                    enabled = !operation.busy &&
                                        !metrics.overdue,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.khatmah_resume))
                                }
                            }
                        }

                        if (plan.completedPages == 604 && plan.status != "finished") {
                            OutlinedButton(
                                onClick = { confirmFinish = true },
                                enabled = !operation.busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) {
                                Text(stringResource(R.string.khatmah_finish))
                            }
                        }

                        OutlinedButton(
                            onClick = { confirmRestart = true },
                            enabled = !operation.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.khatmah_restart))
                        }
                    }

                    if (plan.pending && !plan.conflict) {
                        TextButton(
                            onClick = sync,
                            enabled = !operation.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.sync))
                        }
                    }

                    TextButton(
                        onClick = refresh,
                        enabled = !operation.busy && !plan.pending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        if (editing) {
            item(key = "khatmah-editor", contentType = "editor") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AtharSpacing.Lg),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
                    ) {
                        AtharSectionHeader(
                            if (plan == null) {
                                stringResource(R.string.khatmah_create)
                            } else {
                                stringResource(R.string.khatmah_edit)
                            },
                        )
                        DateField(
                            value = startDate,
                            change = {
                                startDate = it
                                clearFeedback()
                            },
                            label = stringResource(R.string.start_date),
                        )
                        DateField(
                            value = targetDate,
                            change = {
                                targetDate = it
                                clearFeedback()
                            },
                            label = stringResource(R.string.target_date),
                        )
                        OutlinedTextField(
                            value = completedText,
                            onValueChange = {
                                completedText = it.filter(Char::isDigit).take(3)
                                clearFeedback()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.completed_pages)) },
                            supportingText = {
                                Text(stringResource(R.string.khatmah_completed_hint))
                            },
                            singleLine = true,
                            isError = completedText.isNotBlank() &&
                                (completed == null || completed !in 0..604),
                            keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType =
                                        androidx.compose.ui.text.input.KeyboardType.Number,
                                ),
                        )

                        Button(
                            onClick = {
                                save(
                                    startDate,
                                    targetDate,
                                    requireNotNull(completed),
                                )
                                editing = false
                            },
                            enabled = formValid &&
                                !operation.busy &&
                                plan?.conflict != true &&
                                plan?.status != "finished",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.save))
                        }

                        if (plan != null) {
                            TextButton(
                                onClick = {
                                    startDate = plan.startDate
                                    targetDate = plan.targetDate
                                    completedText = plan.completedPages.toString()
                                    editing = false
                                    clearFeedback()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }

                        Text(
                            stringResource(R.string.khatmah_no_night_transfer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (operation.saved) {
            item(key = "khatmah-saved", contentType = "status") {
                Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                    MessagePanel(stringResource(R.string.saved))
                }
            }
        }
        operation.error?.let { error ->
            item(key = "khatmah-error", contentType = "status") {
                Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                    MessagePanel(
                        stringResource(
                            if (error == "invalid") {
                                R.string.khatmah_invalid
                            } else {
                                R.string.network_error
                            },
                        ),
                        true,
                    )
                }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.khatmah_restart)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                    Text(stringResource(R.string.khatmah_restart_confirm))
                    DateField(
                        value = restartTarget,
                        change = { restartTarget = it },
                        label = stringResource(R.string.target_date),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        action("restart", restartTarget)
                        confirmRestart = false
                    },
                    enabled = runCatching {
                        !LocalDate.parse(restartTarget).isBefore(today) &&
                            !LocalDate.parse(restartTarget).isAfter(today.plusYears(5))
                    }.getOrDefault(false),
                ) {
                    Text(stringResource(R.string.khatmah_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.khatmah_finish)) },
            text = { Text(stringResource(R.string.khatmah_finish_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        action("finish", null)
                        confirmFinish = false
                    },
                ) {
                    Text(stringResource(R.string.khatmah_finish))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun KhatmahWorkload(
    plan: KhatmahPlanState,
    metrics: KhatmahMetrics,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtharSpacing.Lg),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.khatmah_progress),
                    style = MaterialTheme.typography.titleLarge,
                )
                AtharStatusPill(
                    stringResource(
                        when (plan.status) {
                            "paused" -> R.string.paused
                            "finished" -> R.string.finished
                            else -> R.string.active
                        },
                    ),
                    emphasized = plan.status != "active",
                )
            }

            LinearProgressIndicator(
                progress = {
                    (metrics.progressPercent / 100f).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    R.string.khatmah_pages_progress,
                    plan.completedPages,
                    metrics.totalPages,
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                AtharMetric(
                    value = metrics.dailyPages.toString(),
                    label = stringResource(R.string.khatmah_daily_pages),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
                AtharMetric(
                    value = metrics.remainingPages.toString(),
                    label = stringResource(R.string.khatmah_remaining_pages),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
                AtharMetric(
                    value = metrics.daysRemaining.toString(),
                    label = stringResource(R.string.khatmah_days_remaining),
                    modifier = Modifier.weight(1f).widthIn(min = 100.dp),
                )
            }

            when {
                metrics.startsInDays > 0 -> {
                    AtharStatusPill(
                        stringResource(
                            R.string.khatmah_starts_in,
                            metrics.startsInDays,
                        ),
                    )
                }
                metrics.overdue -> {
                    AtharStatusPill(
                        stringResource(R.string.khatmah_overdue),
                        emphasized = true,
                    )
                }
                metrics.pagesBehind > 0 -> {
                    AtharStatusPill(
                        stringResource(
                            R.string.khatmah_behind,
                            metrics.pagesBehind,
                        ),
                        emphasized = true,
                    )
                }
                metrics.pagesAhead > 0 -> {
                    AtharStatusPill(
                        stringResource(
                            R.string.khatmah_ahead,
                            metrics.pagesAhead,
                        ),
                    )
                }
            }

            Text(
                stringResource(
                    R.string.khatmah_dates,
                    plan.startDate,
                    plan.targetDate,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KhatmahConflictPanel(
    device: KhatmahPlanState,
    server: KhatmahServerVersion?,
    busy: Boolean,
    useServer: () -> Unit,
    keepDevice: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtharSpacing.Lg),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
        ) {
            Text(
                stringResource(R.string.khatmah_conflict_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(R.string.khatmah_conflict_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            KhatmahVersionCard(
                title = stringResource(R.string.khatmah_device_version),
                start = device.startDate,
                target = device.targetDate,
                completed = device.completedPages,
                status = device.status,
                revision = device.revision,
            )

            if (server != null) {
                KhatmahVersionCard(
                    title = stringResource(R.string.khatmah_account_version),
                    start = server.startDate,
                    target = server.targetDate,
                    completed = server.completedPages,
                    status = server.status,
                    revision = server.revision,
                )
            } else {
                Text(
                    stringResource(R.string.khatmah_account_missing),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Button(
                onClick = useServer,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.khatmah_use_account))
            }
            OutlinedButton(
                onClick = keepDevice,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.khatmah_keep_device))
            }
        }
    }
}

@Composable
private fun KhatmahVersionCard(
    title: String,
    start: String,
    target: String,
    completed: Int,
    status: String,
    revision: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.khatmah_version_details,
                    completed,
                    start,
                    target,
                    status,
                    revision,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
