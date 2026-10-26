package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

/** Bounded three-row discovery section. No worship history or notification consent is queried. */
@Composable fun UpcomingHomeSection(graph:AppGraph,open:(String)->Unit) {
    val language=LocalConfiguration.current.locales[0].language
    val vm:UpcomingViewModel=viewModel(key="home-upcoming:${graph.session.userId}:$language",factory=object:ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=UpcomingViewModel(graph,null,3) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm,lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        vm.refresh(silent=true)
        while(isActive){delay(60_000);vm.refresh(silent=true)}
    }}
    Column(Modifier.fillMaxWidth().testTag("home-upcoming"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(stringResource(R.string.upcoming_lessons),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={open("upcoming")},modifier=Modifier.testTag("home-upcoming-all")){Text(stringResource(R.string.view_all))}
        }
        if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.offline)Text(stringResource(R.string.offline_notice),style=MaterialTheme.typography.bodySmall)
        if(state.error)MessagePanel(stringResource(R.string.network_error),true){TextButton(onClick={vm.refresh()}){Text(stringResource(R.string.retry))}}
        if(!state.loading&&!state.error&&state.items.isEmpty())QuietEmptyState(stringResource(R.string.upcoming_empty))
        // The server limits this response to three rows; a nested lazy scrolling container is unnecessary.
        state.items.take(3).forEach { event ->
            val live=event.status==EventStatus.LIVE&&!state.offline
            Surface(onClick={open(if(live&&event.canWatch)"lessons/${event.id}" else "upcoming/${event.id}")},modifier=Modifier.fillMaxWidth().testTag("home-event-${event.id}"),shape=MaterialTheme.shapes.medium,color=if(live)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if(live)R.string.upcoming_live else if(event.status==EventStatus.LIVE)R.string.upcoming_last_live else R.string.upcoming_scheduled),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text(event.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(event.scholarName.isNotBlank())Text(event.scholarName,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(event.status==EventStatus.SCHEDULED&&event.start!=null)EventCountdownLabel(event.start,state.clockOffsetMillis)
                }
            }
        }
    }
}
