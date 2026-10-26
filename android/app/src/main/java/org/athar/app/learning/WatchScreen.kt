package org.athar.app.learning

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import org.athar.app.core.data.ProtectedRead

@Composable fun WatchScreen(graph:AppGraph,initial:Int,open:(String)->Unit) {
    val vm:WatchViewModel=viewModel(key="watch:${graph.session.userId}:$initial",factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>):T=WatchViewModel(graph,initial) as T})
    val discussion:DiscussionViewModel=viewModel(key="discussion:${graph.session.userId}:$initial",factory=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>):T=DiscussionViewModel(graph) as T})
    val state by vm.state.collectAsStateWithLifecycle();val embed by vm.player.collectAsStateWithLifecycle();val comments by discussion.preview.collectAsStateWithLifecycle()
    val progressFailure by graph.lessons.failedUser.collectAsStateWithLifecycle()
    var commentsOpen by rememberSaveable{mutableStateOf(false)};var fullComments by rememberSaveable{mutableStateOf(false)};var playlistOpen by rememberSaveable{mutableStateOf(false)}
    var inlineComments by rememberSaveable{mutableStateOf(true)}
    val fontScale=LocalDensity.current.fontScale
    fun closeFullComments(){fullComments=false;if(!inlineComments)commentsOpen=false}
    var playlistPage by rememberSaveable{mutableIntStateOf(1)}
    val scope=rememberCoroutineScope();val snackbar=remember{SnackbarHostState()};val failed=stringResource(R.string.network_error)
    val uri=LocalUriHandler.current;val context=LocalContext.current
    val detailsScroll=rememberLazyListState()
    val lesson=state.lesson;val id=lesson?.text("id")?.toIntOrNull()
    LaunchedEffect(id){if(id!=null){discussion.select(id);commentsOpen=false;fullComments=false;playlistOpen=false;playlistPage=1;detailsScroll.requestScrollToItem(0)}}
    Box(Modifier.fillMaxSize()) {
        if(lesson==null){if(state.error)FailurePanel(null,{vm.select(initial)})else ContentLoadingSkeleton()}
        else if(state.localProgress !is ProtectedRead.Ready){
            Box(Modifier.padding(20.dp).testTag("watch-local-recovery")){
                if(state.localProgress==ProtectedRead.Unreadable)MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick=vm::retry,enabled=!state.loading){Text(stringResource(R.string.retry))}}
                else if(state.error)FailurePanel(null,vm::retry)else ContentLoadingSkeleton()
                if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide=maxWidth>=840.dp || (maxWidth>=600.dp && maxWidth>maxHeight*1.4f)
            val availableHeight=maxHeight
            val estimatedPlayerHeight=(maxWidth*(if(wide)0.65f else 1f)*9f/16f).coerceAtLeast(200.dp).coerceAtMost(if(wide)(availableHeight-80.dp).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Infinity)
            val inlineFits=availableHeight-estimatedPlayerHeight>=360.dp*fontScale
            LaunchedEffect(inlineFits){if(commentsOpen&&!inlineFits){inlineComments=false;fullComments=true}}
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(if(wide)20.dp else 0.dp)) {
                Column(Modifier.weight(if(wide)0.65f else 1f).fillMaxHeight()) {
                    val video=lesson.text("youtube_id")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val playerHeight=(maxWidth*9f/16f).coerceAtLeast(200.dp).coerceAtMost(if(wide)(availableHeight-80.dp).coerceAtLeast(200.dp) else androidx.compose.ui.unit.Dp.Infinity)
                        if(PlayerPolicy.validVideo(video))YouTubePlayer(video,state.resume,state.retry,fullComments||state.loading,Modifier.fillMaxWidth().height(playerHeight).testTag("youtube-player"),vm::event,vm::unavailable,vm::flush)
                        else Surface(Modifier.fillMaxWidth().height(playerHeight),color=MaterialTheme.colorScheme.surfaceContainerHighest){Box(Modifier.padding(24.dp),contentAlignment=Alignment.Center){Text(stringResource(R.string.watch_video_unavailable))}}
                    }
                    if(state.loading||(PlayerPolicy.validVideo(video)&&embed.status in listOf("loading","buffering")))LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(PlayerPolicy.validVideo(video)&&embed.status in listOf("ready","playing","paused","buffering"))Text(stringResource(when(embed.status){"playing"->R.string.watch_playing;"paused"->R.string.watch_paused;"buffering"->R.string.watch_buffering;else->R.string.watch_ready}),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
                    if(embed.status=="error"||state.error)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("youtube-error"),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.watch_player_error),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick=vm::retry){Text(stringResource(R.string.retry))}
                        if(PlayerPolicy.validVideo(video))TextButton(onClick={uri.openUri(PlayerPolicy.youtubeUrl(video,state.resume))}){Text(stringResource(R.string.open_youtube))}
                    }
                    if(commentsOpen&&!fullComments)Box(Modifier.weight(1f)){DiscussionPanel(discussion,false,{fullComments=true},{commentsOpen=false})}
                    else LazyColumn(Modifier.weight(1f).testTag("watch-details"),state=detailsScroll,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        item(key="information",contentType="metadata") {
                            Text(lesson.text("title"),Modifier.testTag("watch-title"),style=MaterialTheme.typography.headlineSmall)
                            val scholar=lesson.text("scholar_id").toIntOrNull()
                            if(scholar!=null)TextButton(onClick={vm.flush();open("scholars/$scholar")}){Text(lesson.text("scholar_name").ifBlank{stringResource(R.string.scholars)})}
                            lesson["series"]?.takeUnless{it.isJsonNull}?.let{series->TextButton(onClick={open("series/${series.text("id")}")}){Text(series.text("title"))}};if(lesson.text("lesson_number").isNotBlank())Text(stringResource(R.string.watch_position,lesson.text("lesson_number"),lesson["series"].text("lesson_count")),style=MaterialTheme.typography.bodySmall)
                        }
                        item(key="actions",contentType="actions") {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("watch-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                MarkToggle("lesson-$id",lesson.text("is_favorite")=="true",true,snackbar){saved->graph.session.request(if(saved)"PUT" else "DELETE","me/favorites",mapOf("target_type" to "lesson","target_id" to id));graph.content.invalidate("me/favorites")}
                                AssistChip(onClick={scope.launch{try{vm.complete()}catch(e:CancellationException){throw e}catch(_:Exception){snackbar.showSnackbar(failed)}}},enabled=!state.completed&&!state.loading,label={Text(stringResource(if(state.completed)R.string.watch_completed else R.string.watch_mark_complete))},leadingIcon={Icon(AtharIcons.CheckCircle,null,Modifier.size(20.dp))})
                                if(PlayerPolicy.validVideo(video)){
                                    AssistChip(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,lesson.text("title")+"\n"+PlayerPolicy.youtubeUrl(video))},null))},label={Text(stringResource(R.string.watch_share))},leadingIcon={Icon(AtharIcons.Share,null,Modifier.size(20.dp))})
                                    AssistChip(onClick={uri.openUri(PlayerPolicy.youtubeUrl(video))},label={Text(stringResource(R.string.open_youtube))})
                                }
                            }
                            Text(stringResource(R.string.watch_local_progress),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(state.offline)MessagePanel(stringResource(R.string.offline_notice))
                            if(progressFailure!=null&&progressFailure==graph.session.userId)Text(stringResource(R.string.watch_progress_error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        }
                        item(key="description",contentType="description"){ExpandableDescription(lesson.text("description"))}
                        item(key="comments",contentType="comments-preview") {
                            Surface(onClick={inlineComments=inlineFits;commentsOpen=true;fullComments=!inlineFits},modifier=Modifier.testTag("watch-comments"),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically){Icon(AtharIcons.ChatBubble,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(stringResource(R.string.watch_comments_count,comments.total),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);Icon(AtharIcons.ArrowForward,null)}
                                    val preview=comments.row
                                    if(preview!=null){Text(preview.text("display_name").ifBlank{preview.text("username")},style=MaterialTheme.typography.labelMedium);Text(preview.text("body"),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);if(preview.text("status")=="pending")Text(stringResource(R.string.watch_moderation_notice),style=MaterialTheme.typography.bodySmall)}else Text(stringResource(R.string.watch_add_comment),style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if(!wide&&lesson["series"]?.isJsonObject==true){
                            item(key="series-title",contentType="heading"){Text(lesson["series"].text("title"),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.watch_position,lesson.text("lesson_number"),lesson["series"].text("lesson_count")),style=MaterialTheme.typography.bodySmall)}
                            items(lesson.array("playlist_preview"),key={"lesson-${it.text("id")}"},contentType={"playlist"}){row->PlaylistLesson(row,row.text("id")=="$id"){vm.select(row.text("id").toInt())}}
                            item(key="open-playlist",contentType="action"){TextButton(onClick={playlistOpen=true}){Text(stringResource(R.string.watch_all_lessons))}}
                        }
                        if(state.ended)item(key="ended",contentType="next-action"){Text(stringResource(R.string.watch_finished));lesson.array("playlist_preview").getOrNull(1)?.let{next->Button(onClick={vm.select(next.text("id").toInt())}){Text(stringResource(R.string.watch_next))}}}
                    }
                }
                if(wide&&lesson["series"]?.isJsonObject==true)Column(Modifier.weight(0.35f).fillMaxHeight().padding(end=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(lesson["series"].text("title"),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.watch_position,lesson.text("lesson_number"),lesson["series"].text("lesson_count")))
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){items(lesson.array("playlist_preview"),key={it.text("id")},contentType={"playlist"}){row->PlaylistLesson(row,row.text("id")=="$id"){vm.select(row.text("id").toInt())}}}
                    TextButton(onClick={playlistOpen=true}){Text(stringResource(R.string.watch_all_lessons))}
                }
            }
        }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter))
    }
    if(fullComments)Dialog(onDismissRequest={closeFullComments()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){DiscussionPanel(discussion,true,{}, {closeFullComments()})}
    }
    if(playlistOpen&&lesson!=null)Dialog(onDismissRequest={playlistOpen=false},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f),shape=MaterialTheme.shapes.large){Column(Modifier.padding(16.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.watch_all_lessons),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={playlistOpen=false}){Icon(AtharIcons.Close,stringResource(R.string.close))}}
            DataScreen(graph,"series/${lesson.text("series_id")}/lessons",mapOf("page" to "$playlistPage")){page,_->LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(page.array("items"),key={it.text("id")},contentType={"playlist"}){row->PlaylistLesson(row,row.text("id")=="$id"){playlistOpen=false;vm.select(row.text("id").toInt())}}
                item(key="pages",contentType="controls"){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={playlistPage--},enabled=playlistPage>1){Text(stringResource(R.string.back))};TextButton(onClick={playlistPage++},enabled=page.asJsonObject["pagination"].text("has_more")=="true"){Text(stringResource(R.string.more))}}}
            }}
        }}
    }
}
