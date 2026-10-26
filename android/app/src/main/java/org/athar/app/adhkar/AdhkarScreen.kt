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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.ui.*

@Composable
fun AdhkarScreen(graph: AppGraph) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable(category) { mutableIntStateOf(1) }
    var position by rememberSaveable(category) { mutableIntStateOf(0) }
    var reachedEnd by rememberSaveable(category, page) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val entriesScroll = rememberLazyListState()
    val failed = stringResource(R.string.network_error)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        DataScreen(graph, "adhkar/categories") { data, _ ->
            val categories = data.asJsonArray.toList()
            val selected = category ?: categories.firstOrNull()?.text("category")
            if (selected == null) QuietEmptyState(stringResource(R.string.empty_state))
            else Column(Modifier.fillMaxSize()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.text("category") }, contentType = { "category" }) { row ->
                        FilterChip(selected == row.text("category"), { category = row.text("category") }, label = { Text(row.text("category")) })
                    }
                }
                Box(Modifier.weight(1f)) {
                    DataScreen(graph, "adhkar", mapOf("category" to selected, "page" to "$page", "per_page" to "20")) { catalog, _ ->
                        val rows = catalog.array("items")
                        val more = catalog.asJsonObject["pagination"].text("has_more") == "true"
                        val index = position.coerceAtMost((rows.size - 1).coerceAtLeast(0))
                        LaunchedEffect(selected, page, index) { entriesScroll.requestScrollToItem(0) }
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
                                        title = selected,
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
                                                var busy by remember(owner,id) { mutableStateOf(false) }
                                                if(stored==ProtectedRead.Unreadable)MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick={readAttempt++}){Text(stringResource(R.string.retry))}}
                                                else if(counter==null)LinearProgressIndicator(Modifier.fillMaxWidth())
                                                if(counter!=null){
                                                LinearProgressIndicator(
                                                    progress = { counter.count.toFloat() / target },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                AtharStatusPill(
                                                    stringResource(
                                                        R.string.progress_count,
                                                        counter.count,
                                                        target,
                                                    ),
                                                    emphasized = counter.count >= target,
                                                )
                                                Button(onClick = {
                                                    val originalPage = page
                                                    busy = true
                                                    scope.launch {
                                                        try {
                                                            val next = graph.counters.tap(id, target,expectedOwner=owner)
                                                            if (next.count == target && (category ?: categories.firstOrNull()?.text("category")) == selected && page == originalPage && position == index) advance()
                                                        } catch (failure: CancellationException) { throw failure }
                                                        catch (_: Exception) { snackbar.showSnackbar(failed) }
                                                        finally { busy = false }
                                                    }
                                                }, enabled = !busy && !counter.conflict && counter.count < target, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                                    Text(stringResource(if (counter.count >= target) R.string.completed else R.string.count_action))
                                                }
                                                if (counter.conflict) MessagePanel(stringResource(R.string.counter_conflict), true) {
                                                    TextButton(onClick = { scope.launch { try { graph.counters.useServer(id, target,owner) } catch (failure: CancellationException) { throw failure } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(R.string.use_server)) }
                                                }
                                                TextButton(onClick = { scope.launch { try { graph.counters.tap(id, target, reset = true,expectedOwner=owner); reachedEnd = false } catch (failure: CancellationException) { throw failure } catch (_: Exception) { snackbar.showSnackbar(failed) } } }, enabled = !busy && !counter.conflict) { Text(stringResource(R.string.reset_counter)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (reachedEnd) item(key = "end", contentType = "status") { MessagePanel(stringResource(R.string.adhkar_section_end)) }
                            item(key = "navigation", contentType = "controls") {
                                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { reachedEnd = false; if (index > 0) position = index - 1 else { page--; position = 19 } }, enabled = page > 1 || index > 0) { Text(stringResource(R.string.back)) }
                                    TextButton(onClick = { advance() }, enabled = rows.isNotEmpty() && !reachedEnd) { Text(stringResource(R.string.next_dhikr)) }
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
