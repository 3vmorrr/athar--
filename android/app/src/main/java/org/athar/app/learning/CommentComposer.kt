package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.athar.app.R
import org.athar.app.core.ui.AtharIcons
import org.athar.app.core.ui.text

/** A scrolling native input surface keeps submit reachable with large text or landscape IME. */
@Composable
internal fun CommentComposer(vm: DiscussionViewModel, state: DiscussionState) {
    val focus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = { vm.composer(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize().testTag("comment-composer").windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.watch_add_comment), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.composer(false) }) { Icon(AtharIcons.Close, stringResource(R.string.close)) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.replyTo?.let { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.watch_reply_to, row.text("display_name").ifBlank { row.text("username") }), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { vm.reply(null) }) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = vm::draft,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        label = { Text(stringResource(R.string.watch_add_comment)) },
                        minLines = 3,
                        maxLines = 10,
                        enabled = !state.sending,
                        supportingText = { Text("${state.draft.codePointCount(0, state.draft.length)}/2000") }
                    )
                    Text(stringResource(R.string.watch_moderation_notice), style = MaterialTheme.typography.bodySmall)
                    state.sendError?.let { error ->
                        Text(if (error == "network_error") stringResource(R.string.network_error) else error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = vm::send,
                        enabled = !state.sending && state.draft.trim().let { it.codePointCount(0, it.length) } in 2..2000,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (state.sending) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(stringResource(R.string.send_comment))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
