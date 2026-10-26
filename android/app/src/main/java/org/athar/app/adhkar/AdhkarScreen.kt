package org.athar.app.adhkar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.CachedResult
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.ui.*

@Composable
fun AdhkarScreen(graph: AppGraph) {
    val allCategories = "__all__"
    var category by rememberSaveable { mutableStateOf(allCategories) }
    var searchInput by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable(category, searchQuery) { mutableIntStateOf(1) }
    var position by rememberSaveable(category, searchQuery) { mutableIntStateOf(0) }
    var reachedEnd by rememberSaveable(category, searchQuery, page) {
        mutableStateOf(false)
    }
    var statsRevision by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val entriesScroll = rememberLazyListState()
    val failed = stringResource(R.string.network_error)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        DataScreen(graph, "adhkar/categories") { data, _ ->
            val categories = data.asJsonArray.toList()
            if (categories.isEmpty()) {
                QuietEmptyState(stringResource(R.string.empty_state))
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AtharSpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        item(key = allCategories, contentType = "category") {
                            FilterChip(
                                selected = category == allCategories,
                                onClick = { category = allCategories },
                                label = { Text(stringResource(R.string.all_categories)) },
                            )
                        }
                        items(
                            categories,
                            key = { it.text("category") },
                            contentType = { "category" },
                        ) { row ->
                            val value = row.text("category")
                            FilterChip(
                                selected = category == value,
                                onClick = { category = value },
                                label = { Text(value) },
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AtharSpacing.Lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it.take(100) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(stringResource(R.string.search)) },
                        )
                        TextButton(
                            onClick = {
                                searchQuery = searchInput.trim()
                                reachedEnd = false
                            },
                        ) {
                            Text(stringResource(R.string.search))
                        }
                    }

                    AdhkarStatsPanel(
                        graph = graph,
                        revision = statsRevision,
                    )

                    Box(Modifier.weight(1f)) {
                        val parameters = buildMap {
                            put("page", "$page")
                            put("per_page", "20")
                            if (category != allCategories) put("category", category)
                            if (searchQuery.isNotBlank()) put("q", searchQuery)
                        }
                        DataScreen(graph, "adhkar", parameters) { catalog, _ ->
                        val rows = catalog.array("items")
                        val more = catalog.asJsonObject["pagination"].text("has_more") == "true"
                        val index = position.coerceAtMost((rows.size - 1).coerceAtLeast(0))
                        LaunchedEffect(category, searchQuery, page, index) {
                            entriesScroll.requestScrollToItem(0)
                        }
                        fun advance() {
                            if (index < rows.lastIndex) position = index + 1
                            else if (more) { page++; position = 0 }
                            else reachedEnd = true
                        }
                        LazyColumn(modifier = Modifier.testTag("adhkar-entries"), state = entriesScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            item(key = "heading", contentType = "heading") {
                                AtharImmersiveHero(
                                    presentation = AtharHeroPresentation(
                                        artwork = AtharHeroArtwork.Adhkar,
                                        eyebrow = stringResource(R.string.adhkar),
                                        title = if (category == allCategories) {
                                            stringResource(R.string.all_categories)
                                        } else {
                                            category
                                        },
                                        supportingText = stringResource(
                                            R.string.adhkar_item_position,
                                            (page - 1) * 20 + index + 1,
                                            catalog.asJsonObject["pagination"]
                                                .text("total")
                                                .toIntOrNull() ?: 0,
                                        ),
                                    ),
                                    modifier = Modifier
                                        .widthIn(max = 720.dp)
                                        .fillMaxWidth(),
                                )
                            }
                            if (rows.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
                            rows.getOrNull(index)?.let { dhikr ->
                                val id = dhikr.text("id").toInt()
                                val target = dhikr.text("repeat_count").toIntOrNull()?.takeIf { it in 1..65535 }
                                item(key = "dhikr-$id", contentType = "counter") {
                                    Surface(
                                        modifier = Modifier
                                            .widthIn(max = 720.dp)
                                            .fillMaxWidth(),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    ) {
                                        Column(
                                            Modifier.padding(AtharSpacing.Xxl),
                                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
                                        ) {
                                            Text(
                                                dhikr.text("title"),
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                            if (category == allCategories) {
                                                AtharStatusPill(dhikr.text("category"))
                                            }
                                            Text(
                                                dhikr.text("formula"),
                                                fontFamily = QuranFont,
                                                fontSize = 27.sp,
                                                lineHeight = 48.sp,
                                            )
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                            ) {
                                                Column(
                                                    Modifier.padding(AtharSpacing.Md),
                                                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                                                ) {
                                                    Text(
                                                        stringResource(R.string.source),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text(
                                                        dhikr.text("source_reference"),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (target == null) {
                                                AtharStatusPill(
                                                    stringResource(R.string.adhkar_no_fixed_count),
                                                )
                                            } else {
                                                val owner = requireNotNull(graph.session.userId)
                                                LaunchedEffect(owner,id,target) {
                                                    try { graph.counters.refresh(id,target,owner) }
                                                    catch(failure: CancellationException) { throw failure }
                                                    catch(_: Exception) { /* Existing progress remains retained; unreadable state is shown by the protected observer. */ }
                                                }
                                                var readAttempt by remember(owner,id,target){mutableIntStateOf(0)}
                                                val stored by remember(owner,id,target,readAttempt) { graph.counters.observe(owner, id, target).protectedReads() }.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
                                                val counter=(stored as? ProtectedRead.Ready)?.value
                                                val previousDayPending = counter != null &&
                                                    !graph.counters.isCurrentDay(counter) &&
                                                    (
                                                        counter.pending ||
                                                            counter.conflict ||
                                                            counter.events.isNotEmpty()
                                                    )
                                                var busy by remember(owner,id) { mutableStateOf(false) }
                                                if(stored==ProtectedRead.Unreadable)MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick={readAttempt++}){Text(stringResource(R.string.retry))}}
                                                else if(counter==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                                                if(counter!=null){
                                                if(previousDayPending) {
                                                    MessagePanel(
                                                        stringResource(
                                                            R.string.adhkar_previous_day_pending,
                                                        ),
                                                        true,
                                                    ) {
                                                        if(counter.conflict) {
                                                            TextButton(
                                                                onClick = {
                                                                    scope.launch {
                                                                        try {
                                                                            graph.counters.useServer(
                                                                                id,
                                                                                target,
                                                                                owner,
                                                                            )
                                                                            statsRevision++
                                                                        } catch (failure: CancellationException) {
                                                                            throw failure
                                                                        } catch (_: Exception) {
                                                                            snackbar.showSnackbar(failed)
                                                                        }
                                                                    }
                                                                },
                                                            ) {
                                                                Text(stringResource(R.string.use_server))
                                                            }
                                                        } else {
                                                            TextButton(
                                                                onClick = {
                                                                    scope.launch {
                                                                        try {
                                                                            graph.counters.sync(owner)
                                                                            statsRevision++
                                                                        } catch (failure: CancellationException) {
                                                                            throw failure
                                                                        } catch (_: Exception) {
                                                                            snackbar.showSnackbar(failed)
                                                                        }
                                                                    }
                                                                },
                                                            ) {
                                                                Text(stringResource(R.string.sync))
                                                            }
                                                        }
                                                    }
                                                }
                                                val progressDescription = stringResource(
                                                    R.string.progress_count,
                                                    counter.count,
                                                    target,
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .semantics(mergeDescendants = true) {
                                                            progressBarRangeInfo = ProgressBarRangeInfo(
                                                                current = counter.count.toFloat(),
                                                                range = 0f..target.toFloat(),
                                                                steps = (target - 1).coerceAtLeast(0),
                                                            )
                                                            stateDescription = progressDescription
                                                        },
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(
                                                        AtharSpacing.Sm,
                                                    ),
                                                ) {
                                                    Text(
                                                        "${counter.count}",
                                                        style = MaterialTheme.typography.displaySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                    LinearProgressIndicator(
                                                        progress = {
                                                            counter.count.toFloat() / target
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Text(
                                                        progressDescription,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        val originalPage = page
                                                        busy = true
                                                        scope.launch {
                                                            try {
                                                                val next = graph.counters.tap(
                                                                    id,
                                                                    target,
                                                                    expectedOwner = owner,
                                                                )
                                                                if (
                                                                    next.count == target &&
                                                                    page == originalPage &&
                                                                    position == index
                                                                ) {
                                                                    statsRevision++
                                                                    advance()
                                                                }
                                                            } catch (failure: CancellationException) {
                                                                throw failure
                                                            } catch (_: Exception) {
                                                                snackbar.showSnackbar(failed)
                                                            } finally {
                                                                busy = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !busy &&
                                                        !counter.conflict &&
                                                        !previousDayPending &&
                                                        counter.count < target,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 76.dp)
                                                        .testTag("adhkar-count-action"),
                                                    shape = MaterialTheme.shapes.extraLarge,
                                                ) {
                                                    Text(
                                                        stringResource(
                                                            if (counter.count >= target) {
                                                                R.string.completed
                                                            } else {
                                                                R.string.count_action
                                                            },
                                                        ),
                                                        style = MaterialTheme.typography.titleLarge,
                                                    )
                                                }
                                                if (counter.conflict) MessagePanel(stringResource(R.string.counter_conflict), true) {
                                                    TextButton(onClick = { scope.launch { try { graph.counters.useServer(id, target,owner) } catch (failure: CancellationException) { throw failure } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(R.string.use_server)) }
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                graph.counters.tap(
                                                                    id,
                                                                    target,
                                                                    reset = true,
                                                                    expectedOwner = owner,
                                                                )
                                                                reachedEnd = false
                                                            } catch (failure: CancellationException) {
                                                                throw failure
                                                            } catch (_: Exception) {
                                                                snackbar.showSnackbar(failed)
                                                            }
                                                        }
                                                    },
                                                    enabled = !busy &&
                                                        !counter.conflict &&
                                                        !previousDayPending,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 52.dp)
                                                        .testTag("adhkar-reset-action"),
                                                ) {
                                                    Text(stringResource(R.string.reset_counter))
                                                }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (reachedEnd) item(key = "end", contentType = "status") { MessagePanel(stringResource(R.string.adhkar_section_end)) }
                            item(key = "navigation", contentType = "controls") {
                                Row(
                                    Modifier
                                        .widthIn(max = 720.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            reachedEnd = false
                                            if (index > 0) {
                                                position = index - 1
                                            } else {
                                                page--
                                                position = 19
                                            }
                                        },
                                        enabled = page > 1 || index > 0,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp),
                                    ) {
                                        Text(stringResource(R.string.back))
                                    }
                                    OutlinedButton(
                                        onClick = { advance() },
                                        enabled = rows.isNotEmpty() && !reachedEnd,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp),
                                    ) {
                                        Text(stringResource(R.string.next_dhikr))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
private data class AdhkarStatsUiState(
    val loading: Boolean = true,
    val result: CachedResult? = null,
    val pendingCompletions: Int = 0,
    val error: Boolean = false,
)

private class AdhkarStatsViewModel(
    private val graph: AppGraph,
    private val owner: Long,
) : ViewModel() {
    private val mutable = MutableStateFlow(AdhkarStatsUiState())
    val state = mutable.asStateFlow()

    fun load() {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = false)
            try {
                graph.session.requireVerifiedOwner(owner)
                val result = graph.content.get("me/adhkar-stats")
                val pending = graph.counters.pendingCompletionCount(owner, owner)
                graph.session.requireVerifiedOwner(owner)
                mutable.value = AdhkarStatsUiState(
                    loading = false,
                    result = result,
                    pendingCompletions = pending,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    loading = false,
                    error = true,
                )
            }
        }
    }
}

@Composable
private fun AdhkarStatsPanel(
    graph: AppGraph,
    revision: Int,
) {
    val owner = requireNotNull(graph.session.userId)
    val vm: AdhkarStatsViewModel = viewModel(
        key = "adhkar-stats:$owner",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdhkarStatsViewModel(graph, owner) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(owner, revision) { vm.load() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AtharSpacing.Lg),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            AtharSectionHeader(
                title = stringResource(R.string.adhkar_statistics),
                actionLabel = stringResource(R.string.retry),
                onAction = vm::load,
            )

            when {
                state.loading && state.result == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.result != null -> {
                    val data = state.result!!.data
                    if (state.result!!.offline) {
                        AtharStatusPill(stringResource(R.string.offline_notice))
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharMetric(
                            value = data.text("today_completions").ifBlank { "0" },
                            label = stringResource(R.string.adhkar_today_completions),
                            modifier = Modifier.weight(1f).widthIn(min = 96.dp),
                        )
                        AtharMetric(
                            value = data.text("total_completions").ifBlank { "0" },
                            label = stringResource(R.string.adhkar_total_completions),
                            modifier = Modifier.weight(1f).widthIn(min = 96.dp),
                        )
                        AtharMetric(
                            value = data.text("active_days").ifBlank { "0" },
                            label = stringResource(R.string.adhkar_active_days),
                            modifier = Modifier.weight(1f).widthIn(min = 96.dp),
                        )
                    }

                    if (state.pendingCompletions > 0) {
                        AtharStatusPill(
                            stringResource(
                                R.string.adhkar_pending_completions,
                                state.pendingCompletions,
                            ),
                            emphasized = true,
                        )
                    }

                    val categories = data.array("categories")
                    if (categories.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                            categories.take(4).forEach { row ->
                                val current = row.text("current_count").toIntOrNull() ?: 0
                                val target = row.text("target_count").toIntOrNull() ?: 0
                                val progress = if (target > 0) {
                                    (current.toFloat() / target).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            row.text("category"),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            stringResource(
                                                R.string.adhkar_category_items,
                                                row.text("completed_items").toIntOrNull() ?: 0,
                                                row.text("item_count").toIntOrNull() ?: 0,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    MessagePanel(
                        stringResource(R.string.network_error),
                        true,
                    ) {
                        TextButton(onClick = vm::load) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}
