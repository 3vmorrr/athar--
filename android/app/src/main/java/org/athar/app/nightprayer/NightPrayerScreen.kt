package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.LocalNightSession
import org.athar.app.core.ui.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import java.time.*

data class NightUiState(val rows: List<Pair<LocalNightSession, JsonObject>> = emptyList(), val busy: Boolean = false, val error: Boolean = false,
    val conflictRows:List<NightConflict> = emptyList(),val rejectionRows:List<NightRejection> = emptyList(),val loading:Boolean=true,val unreadable:Boolean=false,
    val reviewId:String?=null,val review:NightConflictReview?=null,val reviewLoading:Boolean=false,val reviewError:Boolean=false,val reviewChanged:Boolean=false,val reviewLocalError:Boolean=false,
    val rejectionReviewId:String?=null,val rejectionReview:NightRejectionReview?=null,val rejectionReviewLoading:Boolean=false,val rejectionReviewError:Boolean=false,val rejectionReviewChanged:Boolean=false,val rejectionReviewLocalError:Boolean=false){
    val conflicts:Int get()=conflictRows.size
    val rejections:Int get()=rejectionRows.size
}
class NightViewModel(val graph: AppGraph) : ViewModel() {
    private val user = requireNotNull(graph.session.userId)
    suspend fun exemptions()=graph.nightPrayer.localExemptions(user)
    private val mutable = MutableStateFlow(NightUiState())
    val state = mutable.asStateFlow()
    private var observation:Job?=null
    private var reviewJob:Job?=null
    init { retryLocal() }
    fun retryLocal(){
        observation?.cancel()
        mutable.value=mutable.value.copy(loading=true,unreadable=false)
        observation=viewModelScope.launch { combine(graph.nightPrayer.observe(user),graph.nightPrayer.observeConflicts(user),graph.nightPrayer.observeRejections(user)){rows,conflicts,rejections->Triple(rows,conflicts,rejections)}.protectedReads().collect {
            when(it){
                is ProtectedRead.Ready->mutable.value=mutable.value.copy(rows=it.value.first,conflictRows=it.value.second,rejectionRows=it.value.third,loading=false,unreadable=false)
                ProtectedRead.Unreadable->{reviewJob?.cancel();mutable.value=mutable.value.copy(rows=emptyList(),conflictRows=emptyList(),rejectionRows=emptyList(),reviewId=null,review=null,rejectionReviewId=null,rejectionReview=null,loading=false,unreadable=true)}
                ProtectedRead.Loading->Unit
            }
        } }
    }
    fun sync() = operation { graph.nightPrayer.sync(user) }
    fun save(payload: JsonObject, id: String?, done: () -> Unit) = operation { if (id == null) graph.nightPrayer.save(payload,expectedOwner=user) else graph.nightPrayer.save(payload, id,user); done() }
    fun delete(id: String) = operation { graph.nightPrayer.delete(id,user) }
    fun review(id:String){
        if(mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user)return
        reviewJob?.cancel()
        mutable.value=mutable.value.copy(reviewId=id,review=null,reviewLoading=true,reviewError=false,reviewChanged=false,reviewLocalError=false)
        reviewJob=viewModelScope.launch{
            try{val result=graph.nightPrayer.reviewConflict(id,user);if(mutable.value.reviewId==id)mutable.value=mutable.value.copy(review=result,reviewLoading=false)}
            catch(failure:CancellationException){throw failure}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(reviewLoading=false,reviewError=true,reviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(reviewLoading=false,reviewError=true)}
        }
    }
    fun closeReview(){if(!mutable.value.busy){reviewJob?.cancel();mutable.value=mutable.value.copy(reviewId=null,review=null,reviewLoading=false,reviewError=false,reviewChanged=false,reviewLocalError=false)}}
    fun resolve(choice:NightConflictChoice){
        val selected=mutable.value.review?:return
        if(mutable.value.busy||mutable.value.unreadable||mutable.value.loading||graph.session.userId!=user)return
        viewModelScope.launch{
            mutable.value=mutable.value.copy(busy=true,reviewError=false,reviewChanged=false,reviewLocalError=false,error=false)
            try{
                graph.nightPrayer.resolveConflict(selected,choice)
                mutable.value=mutable.value.copy(reviewId=null,review=null)
                try{graph.nightPrayer.sync(user)}catch(failure:CancellationException){throw failure}catch(_:Exception){mutable.value=mutable.value.copy(error=true)}
            }catch(failure:CancellationException){throw failure}
            catch(_:NightConflictChanged){mutable.value=mutable.value.copy(review=null,reviewChanged=true)}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(reviewError=true,reviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(reviewError=true)}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun reviewRejection(id:String){
        if(mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user)return
        reviewJob?.cancel()
        mutable.value=mutable.value.copy(rejectionReviewId=id,rejectionReview=null,rejectionReviewLoading=true,rejectionReviewError=false,rejectionReviewChanged=false,rejectionReviewLocalError=false)
        reviewJob=viewModelScope.launch{
            try{val result=graph.nightPrayer.reviewRejection(id,user);if(mutable.value.rejectionReviewId==id)mutable.value=mutable.value.copy(rejectionReview=result,rejectionReviewLoading=false)}
            catch(failure:CancellationException){throw failure}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(rejectionReviewLoading=false,rejectionReviewError=true,rejectionReviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(rejectionReviewLoading=false,rejectionReviewError=true)}
        }
    }
    fun closeRejectionReview(){if(!mutable.value.busy){reviewJob?.cancel();mutable.value=mutable.value.copy(rejectionReviewId=null,rejectionReview=null,rejectionReviewLoading=false,rejectionReviewError=false,rejectionReviewChanged=false,rejectionReviewLocalError=false)}}
    private fun resolveRejection(block:suspend(NightRejectionReview)->Unit){
        val selected=mutable.value.rejectionReview?:return
        if(mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user)return
        viewModelScope.launch{
            mutable.value=mutable.value.copy(busy=true,rejectionReviewError=false,rejectionReviewChanged=false,rejectionReviewLocalError=false,error=false)
            try{
                block(selected)
                mutable.value=mutable.value.copy(rejectionReviewId=null,rejectionReview=null)
                try{graph.nightPrayer.sync(user)}catch(failure:CancellationException){throw failure}catch(_:Exception){mutable.value=mutable.value.copy(error=true)}
            }catch(failure:CancellationException){throw failure}
            catch(_:NightRejectionChanged){mutable.value=mutable.value.copy(rejectionReview=null,rejectionReviewChanged=true)}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(rejectionReviewError=true,rejectionReviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(rejectionReviewError=true)}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun retryRejection()=resolveRejection{graph.nightPrayer.retryRejection(it)}
    fun acceptAccountRejection()=resolveRejection{graph.nightPrayer.acceptAccountRejection(it)}
    fun repairRejection(review:NightRejectionReview,payload:JsonObject,done:()->Unit){
        if(mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user)return
        viewModelScope.launch{
            mutable.value=mutable.value.copy(busy=true,rejectionReviewError=false,rejectionReviewChanged=false,rejectionReviewLocalError=false,error=false)
            try{
                graph.nightPrayer.repairRejection(review,payload)
                mutable.value=mutable.value.copy(rejectionReviewId=null,rejectionReview=null)
                done()
                try{graph.nightPrayer.sync(user)}catch(failure:CancellationException){throw failure}catch(_:Exception){mutable.value=mutable.value.copy(error=true)}
            }catch(failure:CancellationException){throw failure}
            catch(_:NightRejectionChanged){mutable.value=mutable.value.copy(rejectionReview=null,rejectionReviewChanged=true)}
            catch(_:NightRetainedUnreadable){mutable.value=mutable.value.copy(rejectionReviewError=true,rejectionReviewLocalError=true)}
            catch(_:Exception){mutable.value=mutable.value.copy(rejectionReviewError=true)}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy||mutable.value.loading||mutable.value.unreadable||graph.session.userId!=user) return
        viewModelScope.launch {
            if(graph.session.userId!=user)return@launch
            mutable.value = mutable.value.copy(busy = true, error = false)
            try { block() } catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}

@Composable fun NightPrayerScreen(graph: AppGraph) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("tonight") }
    var addRequest by remember { mutableIntStateOf(0) }
    val owner = requireNotNull(graph.session.userId)
    val vm: NightViewModel = viewModel(
        key = "night:${graph.session.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NightViewModel(graph) as T
        },
    )
    val timerVm: NightTimerViewModel = viewModel(
        key = "night-timer:$owner",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NightTimerViewModel(graph, owner) as T
        },
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, timerVm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                timerVm.pauseForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        AtharImmersiveHero(
            presentation = AtharHeroPresentation(
                artwork = AtharHeroArtwork.NightPrayer,
                eyebrow = stringResource(R.string.night_prayer),
                title = stringResource(R.string.night_prayer),
                supportingText = stringResource(R.string.night_intro),
                statusText = when {
                    state.unreadable -> stringResource(R.string.local_data_unreadable)
                    state.rejections > 0 -> stringResource(R.string.night_rejection)
                    state.conflicts > 0 -> stringResource(R.string.night_conflict)
                    state.rows.any { it.first.dirty } -> stringResource(R.string.night_pending)
                    else -> null
                },
                statusTone = if (state.unreadable || state.rejections > 0 || state.conflicts > 0) {
                    AtharHeroStatusTone.Warning
                } else {
                    AtharHeroStatusTone.Neutral
                },
                primaryActionLabel = if (!state.loading && !state.unreadable) {
                    stringResource(R.string.night_add)
                } else {
                    null
                },
            ),
            onPrimaryAction = {
                section = "history"
                addRequest++
            },
        )

        FlowRow(
            Modifier.padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            FilterChip(
                section == "tonight",
                { section = "tonight" },
                label = { Text(stringResource(R.string.night_tonight)) },
            )
            FilterChip(
                section == "plan",
                { section = "plan" },
                label = { Text(stringResource(R.string.night_plan_title)) },
            )
            FilterChip(
                section == "history",
                { section = "history" },
                label = { Text(stringResource(R.string.night_history)) },
            )
            FilterChip(
                section == "calendar",
                { section = "calendar" },
                label = { Text(stringResource(R.string.night_calendar)) },
            )
            FilterChip(
                section == "insights",
                { section = "insights" },
                label = { Text(stringResource(R.string.night_insights)) },
            )
            FilterChip(
                section == "tools",
                { section = "tools" },
                label = { Text(stringResource(R.string.night_tools)) },
            )
        }

        AtharPageTransition(section, Modifier.weight(1f)) {
            when (it) {
                "tonight" -> NightTonightScreen(graph, vm, timerVm)
                "plan" -> NightPlanScreen(graph, vm)
                "insights" -> NightInsightsScreen(graph, vm)
                "tools" -> NightToolsScreen(graph)
                "calendar" -> NightCalendarScreen(vm)
                else -> NightHistoryScreen(graph, vm, addRequest)
            }
        }
    }
}

@Composable private fun NightHistoryScreen(
    graph: AppGraph,
    vm: NightViewModel,
    addRequest: Int,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading) {
        Box(Modifier.padding(20.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    if (state.unreadable) {
        Box(Modifier.padding(20.dp)) {
            MessagePanel(
                stringResource(R.string.local_data_unreadable),
                true,
            ) {
                TextButton(onClick = vm::retryLocal) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        return
    }

    var editing by remember {
        mutableStateOf<Pair<LocalNightSession, JsonObject>?>(null)
    }
    var repairReview by remember { mutableStateOf<NightRejectionReview?>(null) }
    var form by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val pageSize = 20
    val pageCount = maxOf(1, (state.rows.size + pageSize - 1) / pageSize)

    LaunchedEffect(pageCount) {
        if (page > pageCount) page = pageCount
    }
    LaunchedEffect(addRequest) {
        if (addRequest > 0 && !state.loading && !state.unreadable) {
            editing = null
            form = true
        }
    }

    val totals = remember(state.rows) {
        state.rows
            .groupBy { it.first.nightDate }
            .mapValues { (_, list) -> list.sumOf { it.first.verseCount } }
    }
    val conflictedIds = remember(state.conflictRows) {
        state.conflictRows.map { it.local.id }.toSet()
    }
    val rejectedIds = remember(state.rejectionRows) {
        state.rejectionRows.map { it.local.id }.toSet()
    }
    val visibleRows = remember(state.rows, page) {
        state.rows
            .drop((page - 1) * pageSize)
            .take(pageSize)
    }

    LazyColumn(
        Modifier.testTag("night-history-list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.night_history))
        }

        item(key = "summary", contentType = "summary") {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.night_totals,
                            totals.size,
                            totals.values.sum(),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.night_self_report),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val pace = NightRules.paceInsight(
                        state.rows.mapNotNull { row ->
                            row.first.measuredSeconds?.let {
                                row.first.verseCount to it
                            }
                        },
                    )
                    if (pace != null) {
                        Text(
                            stringResource(
                                R.string.night_measured_pace_samples,
                                pace.secondsPerVerse,
                                pace.usedSampleCount,
                            ),
                        )
                        if (pace.usedSampleCount < pace.sampleCount) {
                            Text(
                                stringResource(
                                    R.string.night_measured_pace_outliers,
                                    pace.sampleCount - pace.usedSampleCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.night_measured_pace_insufficient),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                editing = null
                                form = true
                            },
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(R.string.night_add))
                        }
                        OutlinedButton(
                            onClick = vm::sync,
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(R.string.sync))
                        }
                    }
                }
            }
        }

        if (state.busy) {
            item(key = "busy", contentType = "progress") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.error) {
            item(key = "error", contentType = "feedback") {
                FailurePanel(null, vm::sync)
            }
        }
        if (state.conflicts > 0) {
            item(key = "conflict", contentType = "feedback") {
                MessagePanel(stringResource(R.string.night_conflict), true)
            }
        }
        if (state.rejections > 0) {
            item(key = "rejection", contentType = "feedback") {
                MessagePanel(stringResource(R.string.night_rejection_notice), true)
            }
        }

        items(
            state.rejectionRows.filter { it.local.deleted },
            key = { "deleted-rejection:${it.local.id}" },
            contentType = { "deleted-rejection" },
        ) { rejection ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.night_pending_delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (rejection.status == 404) R.string.night_rejection_missing
                            else R.string.night_rejection_generic,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { vm.reviewRejection(rejection.local.id) },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("night-rejection-${rejection.local.id}"),
                    ) {
                        Text(stringResource(R.string.night_review_rejection))
                    }
                }
            }
        }

        items(
            state.conflictRows.filter { it.local.deleted },
            key = { "deleted-conflict:${it.local.id}" },
            contentType = { "deleted-conflict" },
        ) { conflict ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.night_pending_delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = { vm.review(conflict.local.id) },
                        enabled = !state.busy,
                        modifier = Modifier.testTag(
                            "night-conflict-${conflict.local.id}",
                        ),
                    ) {
                        Text(stringResource(R.string.night_review_conflict))
                    }
                }
            }
        }

        if (state.rows.isEmpty() && state.rejectionRows.isEmpty() && state.conflictRows.none { it.local.deleted }) {
            item(key = "empty", contentType = "empty") {
                QuietEmptyState(stringResource(R.string.night_empty))
            }
        }

        items(
            visibleRows,
            key = { it.first.id },
            contentType = { "session" },
        ) { (local, body) ->
            val conflicted = local.id in conflictedIds
            val rejected = local.id in rejectedIds
            val expanded = expandedId == local.id

            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                local.nightDate,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.night_session_count,
                                    local.verseCount,
                                ),
                            )
                        }
                        AtharStatusPill(
                            stringResource(
                                if (local.dirty) {
                                    R.string.night_pending
                                } else {
                                    R.string.night_synced
                                },
                            ),
                            emphasized = local.dirty,
                        )
                    }

                    body.text("entry_type")
                        .takeIf { it == "range" }
                        ?.let {
                            Text(
                                stringResource(
                                    R.string.night_range_indices,
                                    body.text("start_index"),
                                    body.text("end_index"),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                    local.measuredSeconds?.let { seconds ->
                        AtharStatusPill(
                            stringResource(
                                R.string.night_measured_duration_value,
                                seconds,
                            ),
                        )
                    }

                    TextButton(
                        onClick = {
                            expandedId = if (expanded) null else local.id
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(
                                if (expanded) {
                                    R.string.night_hide_details
                                } else {
                                    R.string.night_show_details
                                },
                            ),
                        )
                    }

                    if (expanded) {
                        NightSessionDetailFields(body)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = {
                                editing = local to body
                                form = true
                            },
                            enabled = !state.busy && !conflicted && !rejected,
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(
                            onClick = { deleting = local.id },
                            enabled = !state.busy && !conflicted && !rejected,
                        ) {
                            Text(
                                stringResource(R.string.delete_action),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (rejected) {
                            TextButton(
                                onClick = { vm.reviewRejection(local.id) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("night-rejection-${local.id}"),
                            ) {
                                Text(stringResource(R.string.night_review_rejection))
                            }
                        } else if (conflicted) {
                            TextButton(
                                onClick = { vm.review(local.id) },
                                enabled = !state.busy,
                                modifier = Modifier.testTag(
                                    "night-conflict-${local.id}",
                                ),
                            ) {
                                Text(stringResource(R.string.night_review_conflict))
                            }
                        }
                    }
                }
            }
        }

        if (state.rows.isNotEmpty()) {
            item(key = "history-pagination", contentType = "controls") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            page--
                            expandedId = null
                        },
                        enabled = page > 1,
                    ) {
                        Text(stringResource(R.string.back))
                    }
                    Text(
                        stringResource(
                            R.string.night_history_page,
                            page,
                            pageCount,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = {
                            page++
                            expandedId = null
                        },
                        enabled = page < pageCount,
                    ) {
                        Text(stringResource(R.string.more))
                    }
                }
            }
        }

        item(key = "source", contentType = "source") {
            Text(
                stringResource(R.string.night_source),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (form) {
        NightEntryDialog(
            graph = graph,
            editing = editing,
            busy = state.busy,
            conflicted = editing?.first?.id in conflictedIds || editing?.first?.id in rejectedIds,
            dismiss = { form = false },
            save = { payload ->
                vm.save(payload, editing?.first?.id) {
                    form = false
                }
            },
        )
    }
    if (state.reviewId != null) {
        NightConflictDialog(
            state,
            vm::closeReview,
            { state.reviewId?.let(vm::review) },
            vm::resolve,
        )
    }
    if (state.rejectionReviewId != null && repairReview == null) {
        NightRejectionDialog(
            state = state,
            close = vm::closeRejectionReview,
            reload = { state.rejectionReviewId?.let(vm::reviewRejection) },
            retryMutation = vm::retryRejection,
            useAccount = vm::acceptAccountRejection,
            repair = { state.rejectionReview?.let { repairReview = it } },
        )
    }
    repairReview?.let { review ->
        review.rejection.body?.let { body ->
            NightEntryDialog(
                graph = graph,
                editing = review.rejection.local to body,
                busy = state.busy,
                conflicted = false,
                dismiss = { repairReview = null },
                save = { payload ->
                    vm.repairRejection(review, payload) { repairReview = null }
                },
            )
        }
    }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_action)) },
            text = { Text(stringResource(R.string.night_delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting?.let(vm::delete)
                        deleting = null
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun NightSessionDetailFields(body: JsonObject) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
    ) {
        body.text("timezone").takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.night_timezone_value, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        body.text("boundary_source").takeIf { it.isNotBlank() }?.let { source ->
            val sourceLabel = stringResource(
                if (source == "calculated") {
                    R.string.night_boundary_calculated
                } else {
                    R.string.night_boundary_manual
                },
            )
            val method = body.text("calculation_method")
            val methodLabel = when (method) {
                "EGYPTIAN" -> stringResource(R.string.method_egyptian)
                "MUSLIM_WORLD_LEAGUE" -> stringResource(R.string.method_mwl)
                "UMM_AL_QURA" -> stringResource(R.string.method_umm_al_qura)
                "KARACHI" -> stringResource(R.string.method_karachi)
                "DUBAI" -> stringResource(R.string.method_dubai)
                "MOON_SIGHTING_COMMITTEE" ->
                    stringResource(R.string.method_moonsighting)
                else -> "—"
            }
            Text(
                stringResource(
                    R.string.night_boundary_context,
                    sourceLabel,
                    methodLabel,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        body.text("started_at").takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.night_started_at, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        body.text("ended_at").takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.night_ended_at, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        body.text("note").takeIf { it.isNotBlank() }?.let {
            HorizontalDivider()
            Text(
                stringResource(R.string.private_note),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable internal fun NightEntryDialog(
    graph: AppGraph,
    editing: Pair<LocalNightSession, JsonObject>?,
    busy: Boolean,
    conflicted: Boolean,
    dismiss: () -> Unit,
    save: (JsonObject) -> Unit,
    measuredTimer: NightTimerSnapshot? = null,
) {
    val initial = editing?.second
    var date by remember(initial, measuredTimer) {
        mutableStateOf(
            measuredTimer?.nightDate
                ?: initial?.text("night_date")
                ?: LocalDate.now().toString(),
        )
    }
    var zone by remember(initial, measuredTimer) {
        mutableStateOf(
            measuredTimer?.timezone
                ?: initial?.text("timezone")
                ?: NightRules.serverTimezone(ZoneId.systemDefault()),
        )
    }
    var range by remember { mutableStateOf(initial?.text("entry_type") == "range") }
    var count by remember { mutableStateOf(initial?.text("verse_count").orEmpty()) }
    var startSurah by remember { mutableStateOf("1") }; var startAyah by remember { mutableStateOf("1") }
    var endSurah by remember { mutableStateOf("1") }; var endAyah by remember { mutableStateOf("7") }
    var note by remember { mutableStateOf(initial?.text("note").orEmpty()) }
    var error by remember { mutableStateOf(false) }; var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initial) { if (initial?.text("entry_type") == "range") {
        val index = graph.quran.data().index
        val start = index.position(initial["start_index"].asInt); val end = index.position(initial["end_index"].asInt)
        startSurah = "${start.surah}"; startAyah = "${start.ayah}"; endSurah = "${end.surah}"; endAyah = "${end.ayah}"
    } }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (editing == null) R.string.night_add else R.string.edit)) }, text = {
        LazyColumn(Modifier.testTag("night-entry-fields"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(conflicted)item{Text(stringResource(R.string.night_edit_conflict),color=MaterialTheme.colorScheme.error)}
            if (measuredTimer != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.padding(AtharSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                        ) {
                            Text(
                                stringResource(R.string.night_measured_context),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    R.string.night_measured_context_details,
                                    measuredTimer.nightDate,
                                    measuredTimer.timezone,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.night_timer_recorded_duration,
                                    measuredTimer.accumulatedMillis / 1000L,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(stringResource(R.string.night_date_explanation))
                    DateField(
                        date,
                        { date = it },
                        stringResource(R.string.night_date),
                        latest = LocalDate.now(),
                    )
                }
                item {
                    OutlinedTextField(
                        zone,
                        { zone = it.take(80) },
                        label = { Text(stringResource(R.string.timezone)) },
                        singleLine = true,
                    )
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!range, { range = false }, label = { Text(stringResource(R.string.night_manual_count)) }); FilterChip(range, { range = true }, label = { Text(stringResource(R.string.night_range)) }) } }
            if (range) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField(startSurah, { startSurah = it }, stringResource(R.string.start_surah), Modifier.weight(1f)); NumberField(startAyah, { startAyah = it }, stringResource(R.string.start_ayah), Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberField(endSurah, { endSurah = it }, stringResource(R.string.end_surah), Modifier.weight(1f)); NumberField(endAyah, { endAyah = it }, stringResource(R.string.end_ayah), Modifier.weight(1f)) } }
            } else item { NumberField(count, { count = it }, stringResource(R.string.verse_count), Modifier.fillMaxWidth()) }
            item { OutlinedTextField(note, { note = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.private_note)) }, maxLines = 4) }
            if (error) item { Text(stringResource(R.string.invalid_input), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && !submitting&&!conflicted, onClick = {
        submitting = true; scope.launch {
            try {
                val parsed = LocalDate.parse(date); val tz = ZoneId.of(zone); require(parsed <= LocalDate.now(tz) && parsed.year >= 1900)
                val body = JsonObject().apply {
                    addProperty("night_date", parsed.toString())
                    addProperty("timezone", tz.id)

                    val preservedSource = initial?.text("boundary_source")
                        ?.takeIf { it in setOf("manual", "calculated") }
                    addProperty(
                        "boundary_source",
                        when {
                            measuredTimer != null -> "calculated"
                            preservedSource != null -> preservedSource
                            else -> "manual"
                        },
                    )

                    if (measuredTimer != null) {
                        addProperty(
                            "calculation_method",
                            measuredTimer.calculationMethod,
                        )
                        addProperty("maghrib_at", measuredTimer.maghribAt)
                        addProperty("fajr_at", measuredTimer.fajrAt)
                        addProperty("started_at", measuredTimer.startedAt)
                        addProperty(
                            "ended_at",
                            requireNotNull(measuredTimer.endedAt),
                        )
                        addProperty(
                            "measured_seconds",
                            (measuredTimer.accumulatedMillis / 1000L)
                                .toInt()
                                .coerceAtLeast(1),
                        )
                    } else if (initial != null) {
                        listOf(
                            "calculation_method",
                            "maghrib_at",
                            "fajr_at",
                            "started_at",
                            "ended_at",
                            "measured_seconds",
                        ).forEach { field ->
                            initial[field]?.takeUnless { it.isJsonNull }?.let {
                                add(field, it.deepCopy())
                            }
                        }
                    }

                    addProperty("entry_type", if (range) "range" else "manual")
                    addProperty("note", note)
                    if (range) {
                        val index = graph.quran.data().index
                        val selected = index.range(
                            startSurah.toInt(),
                            startAyah.toInt(),
                            endSurah.toInt(),
                            endAyah.toInt(),
                        )
                        addProperty("start_index", selected.start.globalIndex)
                        addProperty("end_index", selected.end.globalIndex)
                    } else {
                        val total = count.toInt()
                        require(total in 1..100000)
                        addProperty("verse_count", total)
                    }
                }
                save(body)
            } catch (failure: CancellationException) { throw failure } catch (_: Exception) { error = true } finally { submitting = false }
        }
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun NumberField(value: String, change: (String) -> Unit, label: String, modifier: Modifier) { OutlinedTextField(value, { change(it.filter(Char::isDigit).take(6)) }, modifier, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
