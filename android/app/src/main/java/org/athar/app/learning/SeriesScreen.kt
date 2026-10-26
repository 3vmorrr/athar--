package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun SeriesScreen(graph:AppGraph,id:Int,open:(String)->Unit) {
    var page by rememberSaveable(id){mutableIntStateOf(1)}
    val snackbar=remember{SnackbarHostState()}
    Box(Modifier.fillMaxSize()){
    DataScreen(graph,"series/$id/overview"){series,_->
        DataScreen(graph,"series/$id/lessons",mapOf("page" to "$page")){data,_->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item(key="series-summary",contentType="summary") {
            AtharHeroSurface(Modifier.fillMaxWidth()) {
            Text(series.text("title"),style=MaterialTheme.typography.headlineSmall)
            MarkToggle("series-$id",series.text("is_favorite")=="true",true,snackbar){saved->graph.session.request(if(saved)"PUT" else "DELETE","me/favorites",mapOf("target_type" to "series","target_id" to id));graph.content.invalidate("me/favorites")}
            if(series.text("scholar_id").toIntOrNull()!=null)TextButton(onClick={open("scholars/${series.text("scholar_id")}")}){Text(series.text("scholar_name"))}
            val count=series.text("lesson_count").toIntOrNull()?:0;val complete=series.text("completed_count").toIntOrNull()?:0
            Text(stringResource(R.string.watch_series_progress,complete,count))
            LinearProgressIndicator(progress={if(count>0)complete.toFloat()/count else 0f},modifier=Modifier.fillMaxWidth())
            ExpandableDescription(series.text("description"))
            series.text("resume_lesson_id").toIntOrNull()?.let{resume->Button(onClick={open("lessons/$resume")}){Text(stringResource(R.string.watch_continue))}}
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
