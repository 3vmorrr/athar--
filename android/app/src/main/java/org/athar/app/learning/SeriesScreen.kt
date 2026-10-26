package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.core.data.ProtectedRead
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun SeriesScreen(graph:AppGraph,id:Int,open:(String)->Unit) {
    val owner=requireNotNull(graph.session.userId)
    var page by rememberSaveable(id){mutableIntStateOf(1)}
    val snackbar=remember{SnackbarHostState()}
    Box(Modifier.fillMaxSize()){
    DataScreen(graph,"series/$id/overview"){series,_->
        DataScreen(graph,"series/$id/lessons",mapOf("page" to "$page")){data,_->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item(key="series-summary",contentType="summary") {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp),
                    ) {
                        Text(series.text("title"),style=MaterialTheme.typography.headlineSmall)
                        SeriesFollowControl(graph,owner,id)
                        HorizontalDivider()
                        Text(stringResource(R.string.series_save_label),style=MaterialTheme.typography.labelLarge)
                        MarkToggle(
                            "series-$id",
                            series.text("is_favorite")=="true",
                            true,
                            snackbar,
                        ){saved->
                            graph.session.request(
                                if(saved)"PUT" else "DELETE",
                                "me/favorites",
                                mapOf("target_type" to "series","target_id" to id),
                                expectedUser=owner,
                            )
                            graph.content.invalidate("me/favorites",owner)
                        }
                        if(series.text("scholar_id").toIntOrNull()!=null) {
                            TextButton(onClick={open("scholars/${series.text("scholar_id")}")}) {
                                Text(series.text("scholar_name"))
                            }
                        }
                        val count=series.text("lesson_count").toIntOrNull()?:0
                        val complete=series.text("completed_count").toIntOrNull()?:0
                        Text(stringResource(R.string.watch_series_progress,complete,count))
                        LinearProgressIndicator(
                            progress={if(count>0)complete.toFloat()/count else 0f},
                            modifier=Modifier.fillMaxWidth(),
                        )
                        ExpandableDescription(series.text("description"))
                        series.text("resume_lesson_id").toIntOrNull()?.let{resume->
                            Button(onClick={open("lessons/$resume")}) {
                                Text(stringResource(R.string.watch_continue))
                            }
                        }
                    }
                }
            }
                items(data.array("items"),key={it.text("id")},contentType={"lesson"}){row->PlaylistLesson(row,false){open("lessons/${row.text("id")}")}}
                item(key="pages",contentType="controls"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={page--},enabled=page>1){Text(stringResource(R.string.back))};TextButton(onClick={page++},enabled=data.asJsonObject["pagination"].text("has_more")=="true"){Text(stringResource(R.string.more))}}}
        }
        }
    }
    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter))
    }
}


@Composable
private fun SeriesFollowControl(
    graph: AppGraph,
    owner: Long,
    seriesId: Int,
) {
    val flow = remember(owner, seriesId) { graph.follows.observe(owner, "series", seriesId) }
    val read by flow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable(owner, seriesId) { mutableStateOf(false) }
    var refreshFailed by rememberSaveable(owner, seriesId) { mutableStateOf(false) }
    var mutationFailed by rememberSaveable(owner, seriesId) { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            try {
                graph.follows.refresh(owner, "series", seriesId)
                refreshFailed = false
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                refreshFailed = true
            }
        }
    }

    LaunchedEffect(owner, seriesId) { refresh() }

    when (val current = read) {
        ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
        is ProtectedRead.Ready -> {
            val state = current.value
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state == null) {
                    Text(
                        stringResource(R.string.follow_state_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { refresh() }) { Text(stringResource(R.string.retry)) }
                } else {
                    if (state.following) {
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                busy = true
                                mutationFailed = false
                                scope.launch {
                                    try {
                                        graph.follows.set(owner, "series", seriesId, false)
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        mutationFailed = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.following_series)) }
                    } else {
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                mutationFailed = false
                                scope.launch {
                                    try {
                                        graph.follows.set(owner, "series", seriesId, true)
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        mutationFailed = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.follow_series)) }
                    }
                    if (refreshFailed) {
                        Text(
                            stringResource(R.string.follow_saved_state_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mutationFailed) {
                        Text(
                            stringResource(R.string.follow_change_failed_rollback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
