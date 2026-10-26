package org.athar.app.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable fun UpcomingScreen(graph:AppGraph,lesson:Int?=null,open:(String)->Unit) {
    val language=LocalConfiguration.current.locales[0].language
    val vm:UpcomingViewModel=viewModel(key="upcoming:${graph.session.userId}:$language:$lesson",factory=object:ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=UpcomingViewModel(graph,lesson) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(isActive){delay(60_000);vm.refresh(silent=true)}}}
    UpcomingContent(state,lesson!=null,{vm.refresh()},vm::more,open)
}

@Composable internal fun UpcomingContent(state:UpcomingState,detail:Boolean,refresh:()->Unit,more:()->Unit,open:(String)->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max=800.dp).fillMaxSize().testTag("upcoming-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item(key="controls",contentType="controls") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick=refresh,enabled=!state.loading){Text(stringResource(R.string.upcoming_refresh))}
                }
            }
            if(state.loading)item(key="loading",contentType="state"){LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(state.offline)item(key="offline",contentType="state"){MessagePanel(stringResource(R.string.offline_notice))}
            if(state.error)item(key="error",contentType="state"){MessagePanel(stringResource(R.string.network_error),true){TextButton(onClick=refresh){Text(stringResource(R.string.retry))}}}
            if(state.unavailable)item(key="unavailable",contentType="state"){QuietEmptyState(stringResource(R.string.upcoming_unavailable))}
            if(!state.loading&&!state.error&&!state.unavailable&&state.items.isEmpty())item(key="empty",contentType="state"){QuietEmptyState(stringResource(R.string.upcoming_empty))}
            items(state.items,key={it.id},contentType={"event"}){event->UpcomingCard(event,state.offline,state.clockOffsetMillis,detail,open)}
            if(state.hasMore)item(key="more",contentType="controls"){OutlinedButton(onClick=more,enabled=!state.loading,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.more))}}
        }
    }
}

@Composable private fun UpcomingCard(event:UpcomingLesson,offline:Boolean,clockOffset:Long,detail:Boolean,open:(String)->Unit) {
    val locale=LocalConfiguration.current.locales[0]
    val zone=ZoneId.systemDefault()
    val date=remember(event.start,locale,zone){event.start?.atZone(zone)?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withLocale(locale))}
    var expanded by rememberSaveable(event.id){mutableStateOf(false)}
    var previewFailed by remember(event.youtubeId){mutableStateOf(false)}
    var previewLoading by remember(event.youtubeId){mutableStateOf(true)}
    val reduced=reducedMotion()
    ElevatedCard(Modifier.fillMaxWidth().testTag("upcoming-event-${event.id}")) {
        Column(Modifier.padding(20.dp).animateContentSize(tween(if(reduced)0 else AtharMotion.Normal)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(detail&&event.youtubeId.matches(Regex("[A-Za-z0-9_-]{11}"))){
                if(previewFailed)Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(AtharIcons.CalendarMonth,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.upcoming_preview_unavailable),style=MaterialTheme.typography.bodySmall)
                }else Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(MaterialTheme.colorScheme.surfaceContainer),contentAlignment=Alignment.Center){
                    if(previewLoading)CircularProgressIndicator(Modifier.size(24.dp))
                    AsyncImage(model="https://i.ytimg.com/vi/${event.youtubeId}/hqdefault.jpg",contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onSuccess={previewLoading=false},onError={previewLoading=false;previewFailed=true})
                }
            }
            val status=when(event.status){EventStatus.LIVE->if(offline)R.string.upcoming_last_live else R.string.upcoming_live;EventStatus.SCHEDULED->R.string.upcoming_scheduled;EventStatus.PUBLISHED->R.string.upcoming_published;EventStatus.CANCELLED->R.string.upcoming_cancelled;else->R.string.upcoming_draft}
            Surface(shape=MaterialTheme.shapes.small,color=if(event.status==EventStatus.LIVE&&!offline)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh){Text(stringResource(status),Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge)}
            Text(event.title,style=MaterialTheme.typography.headlineSmall,maxLines=if(detail)6 else 3,overflow=TextOverflow.Ellipsis)
            if(event.scholarName.isNotBlank())TextButton(onClick={event.scholarId?.let{open("scholars/$it")}},enabled=event.scholarId!=null,contentPadding=PaddingValues(0.dp)){Text(event.scholarName)}
            if(event.seriesName.isNotBlank())TextButton(onClick={event.seriesId?.let{open("series/$it")}},enabled=event.seriesId!=null,contentPadding=PaddingValues(0.dp)){Text(event.seriesName)}
            if(detail&&event.courseTitle.isNotBlank())Text(stringResource(R.string.upcoming_course,event.courseTitle),style=MaterialTheme.typography.titleMedium)
            if(detail&&event.topics.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                event.topics.forEach{topic->Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.secondaryContainer){Text(topic,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)}}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(AtharIcons.CalendarMonth,null,Modifier.size(20.dp));Column{Text(date?:stringResource(R.string.upcoming_time_unset),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.upcoming_device_time),style=MaterialTheme.typography.bodySmall)}}
            if(event.status==EventStatus.SCHEDULED&&event.start!=null)EventCountdownLabel(event.start,clockOffset)
            event.durationMinutes?.let{Text(pluralStringResource(R.plurals.upcoming_duration,it,it),style=MaterialTheme.typography.bodySmall)}
            if(event.status==EventStatus.CANCELLED)MessagePanel(stringResource(R.string.upcoming_cancelled_body))
            if(detail&&event.status==EventStatus.PUBLISHED&&!event.canWatch)MessagePanel(stringResource(R.string.upcoming_replay_pending))
            if(detail&&event.description.isNotBlank()){
                Text(event.description,maxLines=if(expanded)Int.MAX_VALUE else 5,overflow=TextOverflow.Ellipsis)
                TextButton(onClick={expanded=!expanded}){Text(stringResource(if(expanded)R.string.watch_show_less else R.string.more))}
            }
            if(detail)event.relatedBook?.let{book->
                Column(Modifier.fillMaxWidth().testTag("event-related-book")){
                    Text(stringResource(R.string.upcoming_related_book),style=MaterialTheme.typography.labelLarge)
                    TextButton(onClick={open("books/${book.id}")},contentPadding=PaddingValues(0.dp)){Text(book.title)}
                    if(book.pageStart!=null&&book.pageEnd!=null)Text(stringResource(R.string.upcoming_book_pages,book.pageStart,book.pageEnd),style=MaterialTheme.typography.bodyMedium)
                }
            }
            if(!detail)OutlinedButton(onClick={open("upcoming/${event.id}")},modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.upcoming_details))}
            else if(event.canWatch&&!offline)Button(onClick={open("lessons/${event.id}")},modifier=Modifier.fillMaxWidth()){Icon(AtharIcons.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.play))}
        }
    }
}

@Composable internal fun EventCountdownLabel(start:Instant,clockOffset:Long) {
    var now by remember(start,clockOffset){mutableStateOf(Instant.ofEpochMilli(System.currentTimeMillis()+clockOffset))}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(start,clockOffset,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(isActive){now=Instant.ofEpochMilli(System.currentTimeMillis()+clockOffset);delay(30_000)}}}
    val count=eventCountdown(start,now)
    val resource=when(count.unit){CountdownUnit.MINUTES->R.plurals.upcoming_minutes;CountdownUnit.HOURS->R.plurals.upcoming_hours;CountdownUnit.DAYS->R.plurals.upcoming_days;else->null}
    Text(if(resource==null)stringResource(R.string.upcoming_awaiting) else pluralStringResource(resource,count.amount,count.amount),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
}
