package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonElement
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable internal fun DiscussionPanel(vm:DiscussionViewModel,full:Boolean,expand:()->Unit,close:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.watch_comments_count,state.total),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(!full)IconButton(onClick=expand){Icon(AtharIcons.Fullscreen,stringResource(R.string.watch_expand_comments))}
            IconButton(onClick=close){Icon(AtharIcons.Close,stringResource(R.string.close))}
        }
        Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(state.sort=="newest",{vm.sort("newest")},label={Text(stringResource(R.string.watch_newest))})
            FilterChip(state.sort=="oldest",{vm.sort("oldest")},label={Text(stringResource(R.string.watch_oldest))})
        }
        LazyColumn(Modifier.weight(1f).testTag("discussion-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.loading)item(key="loading",contentType="status"){LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(state.error)item(key="failure",contentType="status"){FailurePanel(null,vm::refresh)}
            if(!state.loading&&state.rows.isEmpty())item(key="empty",contentType="status"){QuietEmptyState(stringResource(R.string.watch_no_comments))}
            state.rows.forEach { root ->
                val id=root.text("id").toInt();val thread=state.threads[id]
                item(key="comment-$id",contentType="comment"){CommentItem(root){vm.reply(root);expand()}}
                if(root.text("reply_count").toIntOrNull()?.let{it>0}==true||thread!=null)item(key="replies-$id",contentType="action") {
                    TextButton(onClick={vm.thread(id)},enabled=thread?.loading!=true){if(thread?.loading==true)Text(stringResource(R.string.loading))else Text(stringResource(if(thread?.expanded==true)R.string.watch_hide_replies else R.string.watch_view_replies,root.text("reply_count").toIntOrNull()?:0))}
                }
                if(thread?.expanded==true){
                    if(thread.loading)item(key="replies-loading-$id",contentType="status"){LinearProgressIndicator(Modifier.fillMaxWidth())}
                    if(thread.error)item(key="replies-error-$id",contentType="status"){FailurePanel(null,{vm.thread(id)})}
                    items(thread.rows,key={"reply-${it.text("id")}"},contentType={"reply"}){reply->Box(Modifier.padding(start=16.dp)){CommentItem(reply,null)}}
                    if(thread.more)item(key="replies-more-$id",contentType="action"){TextButton(onClick={vm.thread(id,true)}){Text(stringResource(R.string.more))}}
                }
            }
            if(state.more)item(key="more",contentType="action"){TextButton(onClick=vm::more,enabled=!state.loading){Text(stringResource(R.string.more))}}
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={vm.composer(true);if(!full)expand()},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.watch_add_comment)) }
            if(state.submitted)Text(stringResource(R.string.watch_comment_sent),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        }
    }
    if(full&&state.composerOpen)CommentComposer(vm,state)
}

@Composable private fun CommentItem(row:JsonElement,reply:(()->Unit)?) {
    val locale=LocalConfiguration.current.locales[0]
    val created=row.text("created_at")
    val date=remember(created,locale){runCatching {
        java.time.LocalDateTime.parse(created.replace(' ','T')).atOffset(java.time.ZoneOffset.UTC)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(created.take(16))}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(Modifier.size(36.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Text(row.text("display_name").ifBlank{row.text("username")}.take(1))}}
            Column(Modifier.weight(1f)){Text(row.text("display_name").ifBlank{row.text("username")},style=MaterialTheme.typography.labelLarge);Text(date,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Text(row.text("body"),style=MaterialTheme.typography.bodyMedium)
        if(row.text("status")=="pending")Text(stringResource(R.string.watch_moderation_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        if(reply!=null)TextButton(onClick=reply){Text(stringResource(R.string.watch_reply))}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
