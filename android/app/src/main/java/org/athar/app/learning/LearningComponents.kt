package org.athar.app.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonElement
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable internal fun ExpandableDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }; val reduced = reducedMotion()
    if(text.isNotBlank()) Surface(onClick={expanded=!expanded},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(if(reduced)0 else AtharMotion.Normal)).padding(16.dp)) {
            Text(text,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(if(expanded)R.string.watch_show_less else R.string.more),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun PlaylistLesson(row: JsonElement,current: Boolean,select:()->Unit) {
    Surface(onClick=select,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=if(current)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            val video=row.text("youtube_id")
            if(PlayerPolicy.validVideo(video))AsyncImage("https://i.ytimg.com/vi/$video/hqdefault.jpg",null,Modifier.size(width=120.dp,height=70.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(AtharIcons.PlayArrow,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(row.text("lesson_number").isNotBlank())Text(stringResource(R.string.watch_lesson_number,row.text("lesson_number")),style=MaterialTheme.typography.labelMedium)
                Text(row.text("title"),style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                if(current)Text(stringResource(R.string.watch_current),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                else if(row.text("is_complete")=="true"||row.text("is_complete")=="1")Text(stringResource(R.string.watch_completed),style=MaterialTheme.typography.labelMedium)
                else if((row.text("position_seconds").toIntOrNull()?:0)>0)Text(stringResource(R.string.watch_in_progress),style=MaterialTheme.typography.labelMedium)
                val duration=row.text("duration_seconds").toIntOrNull()?:0
                if(duration>0)Text("${duration/60}:${(duration%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
