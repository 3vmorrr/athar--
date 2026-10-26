package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

@Composable
fun NightRejectionDialog(
    state:NightUiState,
    close:()->Unit,
    reload:()->Unit,
    retryMutation:()->Unit,
    useAccount:()->Unit,
    repair:()->Unit,
){
    val review=state.rejectionReview
    val ready=review!=null&&!state.rejectionReviewLoading&&!state.rejectionReviewError&&!state.rejectionReviewChanged&&!state.busy
    val expanded=LocalDensity.current.fontScale>=1.8f||LocalConfiguration.current.screenHeightDp<600
    if(expanded){
        Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
            Surface(Modifier.fillMaxSize()){
                Column(Modifier.fillMaxSize().safeDrawingPadding()){
                    Row(
                        Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp),
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                    ){
                        Text(
                            stringResource(R.string.night_review_rejection),
                            Modifier.weight(1f).semantics{heading()},
                            style=MaterialTheme.typography.titleLarge,
                            maxLines=2,
                            overflow=TextOverflow.Ellipsis,
                        )
                        IconButton(onClick=close,enabled=!state.busy){
                            Icon(AtharIcons.Close,stringResource(R.string.close))
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(16.dp),
                    ){
                        RejectionReviewBody(state,reload)
                        RejectionActions(review,ready,retryMutation,useAccount,repair)
                        TextButton(onClick=close,enabled=!state.busy){Text(stringResource(R.string.cancel))}
                    }
                }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest=close,
        title={Text(stringResource(R.string.night_review_rejection))},
        text={
            Column(
                Modifier.heightIn(max=390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(16.dp),
            ){RejectionReviewBody(state,reload)}
        },
        confirmButton={RejectionActions(review,ready,retryMutation,useAccount,repair)},
        dismissButton={TextButton(onClick=close,enabled=!state.busy){Text(stringResource(R.string.cancel))}},
    )
}

@Composable
private fun RejectionReviewBody(state:NightUiState,reload:()->Unit){
    val review=state.rejectionReview
    if(state.rejectionReviewLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(state.rejectionReviewError||state.rejectionReviewChanged){
        Text(
            stringResource(
                if(state.rejectionReviewLocalError)R.string.local_data_unreadable
                else if(state.rejectionReviewChanged)R.string.night_rejection_changed
                else R.string.network_error
            )
        )
        TextButton(onClick=reload,enabled=!state.busy,modifier=Modifier.testTag("night-rejection-reload")){
            Text(stringResource(R.string.retry))
        }
    }else if(review!=null){
        val reason=when(review.rejection.status){
            422->R.string.night_rejection_invalid
            404->R.string.night_rejection_missing
            else->R.string.night_rejection_generic
        }
        Text(stringResource(reason))
        Text(stringResource(R.string.night_rejection_review_notice))
        RejectionVersionCard(stringResource(R.string.night_device_version),review.rejection.local.deleted,review.rejection.body)
        val account=review.account
        if(account==null){
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    AtharSectionHeader(stringResource(R.string.night_account_version))
                    Text(stringResource(R.string.night_account_missing))
                }
            }
        }else{
            RejectionVersionCard(stringResource(R.string.night_account_version),account["deleted"].asBoolean,account)
        }
    }
    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun RejectionActions(
    review:NightRejectionReview?,
    ready:Boolean,
    retryMutation:()->Unit,
    useAccount:()->Unit,
    repair:()->Unit,
){
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        TextButton(
            onClick=retryMutation,
            enabled=ready,
            modifier=Modifier.testTag("night-rejection-retry"),
        ){Text(stringResource(R.string.night_rejection_retry))}
        TextButton(
            onClick=repair,
            enabled=ready&&(review?.let{!it.rejection.local.deleted&&it.rejection.body!=null}==true),
            modifier=Modifier.testTag("night-rejection-repair"),
        ){Text(stringResource(R.string.night_rejection_repair))}
        TextButton(
            onClick=useAccount,
            enabled=ready,
            modifier=Modifier.testTag("night-rejection-account"),
        ){Text(stringResource(R.string.night_rejection_use_account))}
    }
}

@Composable
private fun RejectionVersionCard(title:String,deleted:Boolean,body:JsonObject?){
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp),
        ){
            AtharSectionHeader(title)
            if(deleted){
                Text(stringResource(R.string.night_deleted_version))
            }else if(body!=null){
                body["night_date"]?.takeUnless{it.isJsonNull}?.asString?.let{Text(it)}
                val count=body["verse_count"]?.takeUnless{it.isJsonNull}?.asInt
                    ?:if(body.has("start_index")&&body.has("end_index"))body["end_index"].asInt-body["start_index"].asInt+1 else null
                count?.let{Text(stringResource(R.string.night_session_count,it))}
                body["measured_seconds"]?.takeUnless{it.isJsonNull}?.asInt?.let{
                    Text(stringResource(R.string.night_review_duration,it))
                }
            }
        }
    }
}
