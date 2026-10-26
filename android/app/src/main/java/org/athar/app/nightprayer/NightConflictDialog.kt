package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.JsonObject
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun NightConflictDialog(state:NightUiState,close:()->Unit,retry:()->Unit,choose:(NightConflictChoice)->Unit){
    val review=state.review
    val ready=review!=null&&!state.reviewLoading&&!state.reviewError&&!state.reviewChanged&&!state.busy
    val expanded=LocalDensity.current.fontScale>=1.8f||LocalConfiguration.current.screenHeightDp<600
    if(expanded){
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
            Surface(Modifier.fillMaxSize()){
                Column(Modifier.fillMaxSize().safeDrawingPadding()){
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Text(stringResource(R.string.night_review_conflict),Modifier.weight(1f).semantics{heading()},style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick=close,enabled=!state.busy){Icon(AtharIcons.Close,stringResource(R.string.close))}
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                        ReviewBody(state,retry)
                        ReviewActions(review,ready,choose)
                        TextButton(onClick=close,enabled=!state.busy){Text(stringResource(R.string.cancel))}
                    }
                }
            }
        }
        return
    }
    AlertDialog(onDismissRequest=close,title={Text(stringResource(R.string.night_review_conflict))},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
            ReviewBody(state,retry)
        }
    },confirmButton={ReviewActions(review,ready,choose)},dismissButton={TextButton(onClick=close,enabled=!state.busy){Text(stringResource(R.string.cancel))}})
}

@Composable private fun ReviewBody(state:NightUiState,retry:()->Unit){
    val review=state.review
    if(state.reviewLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.reviewError||state.reviewChanged){
        Text(stringResource(if(state.reviewLocalError)R.string.local_data_unreadable else if(state.reviewChanged)R.string.night_review_changed else R.string.network_error))
        TextButton(onClick=retry,enabled=!state.busy){Text(stringResource(R.string.retry))}
    }else if(review!=null){
        Text(stringResource(R.string.night_review_notice))
        VersionCard(stringResource(R.string.night_device_version),review.local.deleted,review.localBody)
        VersionCard(stringResource(R.string.night_account_version),review.account["deleted"].asBoolean,review.account)
        if(review.account["deleted"].asBoolean&&!review.local.deleted)Text(stringResource(R.string.night_copy_explanation))
    }
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable private fun ReviewActions(review:NightConflictReview?,ready:Boolean,choose:(NightConflictChoice)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        TextButton(onClick={choose(NightConflictChoice.DEVICE)},enabled=ready,modifier=Modifier.testTag("night-keep-device")){
            Text(stringResource(if(review!=null&&review.account["deleted"].asBoolean&&!review.local.deleted)R.string.night_save_copy else R.string.night_keep_device))
        }
        TextButton(onClick={choose(NightConflictChoice.ACCOUNT)},enabled=ready,modifier=Modifier.testTag("night-keep-account")){Text(stringResource(R.string.night_keep_account))}
    }
}

@Composable private fun VersionCard(title:String,deleted:Boolean,body:JsonObject?){
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            AtharSectionHeader(title)
            if(deleted)Text(stringResource(R.string.night_deleted_version))
            else if(body!=null){
                Text(body["night_date"].asString)
                Text(stringResource(R.string.night_session_count,body["verse_count"]?.asInt?: (body["end_index"].asInt-body["start_index"].asInt+1)))
                body["measured_seconds"]?.takeUnless{it.isJsonNull}?.asInt?.let{Text(stringResource(R.string.night_review_duration,it))}
                body["note"]?.takeUnless{it.isJsonNull}?.asString?.takeIf{it.isNotBlank()}?.let{Text(it)}
            }
        }
    }
}
