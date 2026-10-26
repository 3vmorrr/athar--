package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.athar.app.R

/** One scrollable review/action surface works at large fonts and short landscape heights. */
@Composable fun NightRejectionDialog(review:NightRejectionReview,busy:Boolean,close:()->Unit,retry:()->Unit,repair:()->Unit){
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Text(stringResource(R.string.night_rejection_review),Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge)
                Text(stringResource(when(review.status){422,400->R.string.night_rejection_invalid;404,410->R.string.night_rejection_missing;else->R.string.night_rejection_other}))
                Text(stringResource(R.string.night_rejection_retained))
                if(review.local.deleted)Text(stringResource(R.string.night_pending_delete))
                else{
                    Text(review.local.nightDate)
                    Text(stringResource(R.string.night_session_count,review.local.verseCount))
                    review.body?.get("note")?.takeUnless{it.isJsonNull}?.asString?.takeIf{it.isNotBlank()}?.let{Text(it)}
                }
                Text(stringResource(R.string.night_rejection_retry_notice))
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick=retry,enabled=!busy,modifier=Modifier.testTag("night-rejection-retry")){Text(stringResource(R.string.night_rejection_retry))}
                if(canRepairNight(review))Button(onClick=repair,enabled=!busy,modifier=Modifier.testTag("night-rejection-repair")){Text(stringResource(R.string.night_rejection_repair))}
                else Text(stringResource(R.string.night_rejection_retry_only))
                TextButton(onClick=close,enabled=!busy){Text(stringResource(R.string.cancel))}
            }
        }
    }
}
