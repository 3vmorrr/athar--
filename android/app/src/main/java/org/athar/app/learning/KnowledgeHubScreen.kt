package org.athar.app.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

data class KnowledgeContinuation(
    val lessonId: Int,
    val title: String?,
    val seriesId: Int?,
    val seriesTitle: String?,
    val scholarName: String?,
    val positionSeconds: Int,
    val durationSeconds: Int,
)

sealed interface KnowledgeContinuationRead {
    data object Loading : KnowledgeContinuationRead
    data object Unreadable : KnowledgeContinuationRead
    data class Ready(val value: KnowledgeContinuation?) : KnowledgeContinuationRead
}

object KnowledgeContinuationContract {
    fun from(
        retained: RetainedLessonProgress,
        detail: JsonElement?,
    ): KnowledgeContinuation {
        val progress = retained.progress
        require(progress.lessonId > 0 && !progress.complete && progress.positionSeconds > 0)
        if (detail == null || !detail.isJsonObject) {
            return KnowledgeContinuation(
                lessonId = progress.lessonId,
                title = null,
                seriesId = null,
                seriesTitle = null,
                scholarName = null,
                positionSeconds = progress.positionSeconds,
                durationSeconds = progress.durationSeconds,
            )
        }
        val value = detail.asJsonObject
        val returnedId = value["id"]?.asInt ?: error("invalid_lesson_detail")
        require(returnedId == progress.lessonId)
        val title = value.string("title")?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= 240)
        }
        val seriesId = value["series_id"]
            ?.takeUnless { it.isJsonNull }
            ?.asInt
            ?.takeIf { it > 0 }
        val seriesTitle = value.string("series_title")?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= 240)
        }
        val scholarName = value.string("scholar_name")?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= 240)
        }
        return KnowledgeContinuation(
            lessonId = progress.lessonId,
            title = title,
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            scholarName = scholarName,
            positionSeconds = progress.positionSeconds,
            durationSeconds = progress.durationSeconds,
        )
    }

    private fun com.google.gson.JsonObject.string(name: String): String? {
        val field = get(name) ?: return null
        if (field.isJsonNull) return null
        require(field.isJsonPrimitive)
        return field.asString
    }
}

data class KnowledgeCatalogItem(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val description: String?,
)

object KnowledgeCatalogContract {
    private val types = setOf("lessons","series","scholars")

    fun page(type: String, data: JsonElement): Pair<List<KnowledgeCatalogItem>, Boolean> {
        require(type in types)
        require(data.isJsonObject)
        val rows = data.asJsonObject["items"]
        require(rows != null && rows.isJsonArray)
        val items = rows.asJsonArray.map { item(type,it) }
        require(items.size <= 50)
        require(items.map { it.id }.toSet().size == items.size)
        val hasMore = data.asJsonObject["pagination"]
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("has_more")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: false
        return items to hasMore
    }

    private fun item(type: String, element: JsonElement): KnowledgeCatalogItem {
        require(element.isJsonObject)
        val value=element.asJsonObject
        val id=value["id"]?.asInt?.takeIf{it>0}?:error("invalid_learning_id")
        val titleKey=if(type=="scholars")"name" else "title"
        val title=value.string(titleKey)?.trim()?.takeIf{it.isNotEmpty()}?.also{require(it.length<=240)}
            ?:error("invalid_learning_title")
        val subtitle=when(type){
            "lessons"->value.string("series_title")?.trim()?.takeIf{it.isNotEmpty()}
                ?:value.string("scholar_name")?.trim()?.takeIf{it.isNotEmpty()}
            "series"->value.string("scholar_name")?.trim()?.takeIf{it.isNotEmpty()}
            else->null
        }?.also{require(it.length<=240)}
        val descriptionKey=if(type=="scholars")"biography" else "description"
        val description=value.string(descriptionKey)?.trim()?.takeIf{it.isNotEmpty()}?.also{
            require(it.length<=4000)
        }
        return KnowledgeCatalogItem(id,title,subtitle,description)
    }

    private fun com.google.gson.JsonObject.string(name:String):String?{
        val field=get(name)?:return null
        if(field.isJsonNull)return null
        require(field.isJsonPrimitive)
        return field.asString
    }
}

class KnowledgeContinuationViewModel(
    private val graph: AppGraph,
    private val owner: Long,
):ViewModel(){
    private val mutable=MutableStateFlow<KnowledgeContinuationRead>(KnowledgeContinuationRead.Loading)
    val state=mutable.asStateFlow()

    init{refresh()}

    fun refresh(){
        viewModelScope.launch{
            mutable.value=KnowledgeContinuationRead.Loading
            try{
                graph.session.requireVerifiedOwner(owner)
                val retained=graph.lessons.latestIncomplete(owner)
                if(retained==null){
                    mutable.value=KnowledgeContinuationRead.Ready(null)
                    return@launch
                }
                val detail=try{
                    graph.content.get("lessons/"+retained.progress.lessonId).data
                }catch(failure:CancellationException){
                    throw failure
                }catch(_:Exception){
                    null
                }
                graph.session.requireVerifiedOwner(owner)
                mutable.value=KnowledgeContinuationRead.Ready(
                    KnowledgeContinuationContract.from(retained,detail),
                )
            }catch(failure:CancellationException){
                throw failure
            }catch(_:Exception){
                mutable.value=KnowledgeContinuationRead.Unreadable
            }
        }
    }
}

@Composable
fun KnowledgeHubScreen(
    graph: AppGraph,
    open: (String)->Unit,
    initialType: String="lessons",
){
    val owner=requireNotNull(graph.session.userId)
    val vm:KnowledgeContinuationViewModel=viewModel(
        key="knowledge-continuation:"+owner,
        factory=object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T=
                KnowledgeContinuationViewModel(graph,owner) as T
        },
    )
    val continuationRead by vm.state.collectAsStateWithLifecycle()
    val continuation=(continuationRead as? KnowledgeContinuationRead.Ready)?.value
    var type by rememberSaveable(initialType){mutableStateOf(initialType.takeIf{it in setOf("lessons","series","scholars")} ?: "lessons")}
    var input by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var page by rememberSaveable{mutableIntStateOf(1)}
    val listState=rememberLazyListState()
    val scope=rememberCoroutineScope()
    val reduced=reducedMotion()

    DataScreen(
        graph=graph,
        path=type,
        query=mapOf("q" to query,"page" to page.toString()),
    ){data,reload->
        val parsed=remember(type,data){runCatching{KnowledgeCatalogContract.page(type,data)}}
        val result=parsed.getOrNull()
        if(result==null){
            Box(Modifier.padding(AtharSpacing.Xl)){
                MessagePanel(stringResource(R.string.knowledge_catalog_invalid),true){
                    TextButton(onClick=reload){Text(stringResource(R.string.retry))}
                }
            }
            return@DataScreen
        }
        val (rows,hasMore)=result
        LazyColumn(
            modifier=Modifier.fillMaxSize(),
            state=listState,
            contentPadding=PaddingValues(bottom=AtharSpacing.Xxl),
            verticalArrangement=Arrangement.spacedBy(AtharSpacing.Md),
        ){
            item(key="knowledge-continuation",contentType="continuation"){
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AtharSpacing.Lg,
                            end = AtharSpacing.Lg,
                            top = AtharSpacing.Md,
                        ),
                ) {
                    KnowledgeContinuationCard(
                        read = continuationRead,
                        continuation = continuation,
                        onPrimary = {
                            if (continuation != null) {
                                open("lessons/" + continuation.lessonId)
                            } else {
                                scope.launch {
                                    if (reduced) listState.scrollToItem(1)
                                    else listState.animateScrollToItem(1)
                                }
                            }
                        },
                        onSecondary = {
                            if (continuation?.seriesId != null) {
                                open("series/" + continuation.seriesId)
                            } else {
                                open("upcoming")
                            }
                        },
                    )
                }
            }
            item(key="knowledge-controls",contentType="controls"){
                AtharLayeredContentSurface(Modifier.fillMaxWidth(),overlap=20.dp){
                    AtharSectionHeader(stringResource(R.string.explore_knowledge))
                    FlowRow(
                        horizontalArrangement=Arrangement.spacedBy(AtharSpacing.Sm),
                        verticalArrangement=Arrangement.spacedBy(AtharSpacing.Sm),
                    ){
                        listOf(
                            "lessons" to R.string.lessons,
                            "series" to R.string.series,
                            "scholars" to R.string.scholars,
                            "books" to R.string.books,
                        ).forEach{tab->
                            FilterChip(
                                selected=type==tab.first,
                                onClick={
                                    if(tab.first=="books"){
                                        open("catalog-books")
                                    }else{
                                        type=tab.first
                                        query=""
                                        input=""
                                        page=1
                                    }
                                },
                                label={Text(stringResource(tab.second))},
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(AtharSpacing.Sm),
                    ){
                        OutlinedTextField(
                            value=input,
                            onValueChange={input=it.take(100)},
                            modifier=Modifier.weight(1f),
                            singleLine=true,
                            label={Text(stringResource(R.string.search))},
                        )
                        IconButton(
                            onClick={query=input.trim();page=1},
                            modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),
                        ){
                            Icon(AtharIcons.Search,stringResource(R.string.search))
                        }
                    }
                }
            }

            if(rows.isEmpty()){
                item(key="knowledge-empty",contentType="empty"){
                    Box(Modifier.padding(horizontal=AtharSpacing.Lg)){
                        QuietEmptyState(
                            stringResource(
                                if(query.isBlank())R.string.knowledge_empty
                                else R.string.knowledge_no_match
                            ),
                        )
                    }
                }
            }

            items(rows,key={type+":"+it.id},contentType={type}){item->
                KnowledgeCatalogCard(
                    item=item,
                    onClick={open(type+"/"+item.id)},
                )
            }

            item(key="knowledge-pagination",contentType="pagination"){
                Row(
                    Modifier.fillMaxWidth().padding(horizontal=AtharSpacing.Lg),
                    horizontalArrangement=Arrangement.SpaceBetween,
                ){
                    TextButton(
                        onClick={page=(page-1).coerceAtLeast(1)},
                        enabled=page>1,
                        modifier=Modifier.heightIn(min=48.dp),
                    ){Text(stringResource(R.string.back))}
                    Text(page.toString(),Modifier.padding(AtharSpacing.Lg),style=MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick={page++},
                        enabled=hasMore,
                        modifier=Modifier.heightIn(min=48.dp),
                    ){Text(stringResource(R.string.more))}
                }
            }
        }
    }
}

@Composable
private fun KnowledgeContinuationCard(
    read: KnowledgeContinuationRead,
    continuation: KnowledgeContinuation?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        AtharIcons.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(AtharSpacing.Md),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                ) {
                    Text(
                        stringResource(
                            if (continuation != null) R.string.continue_learning
                            else R.string.knowledge,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        continuation?.title ?: stringResource(R.string.knowledge_intro),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val supporting = when (read) {
                        KnowledgeContinuationRead.Unreadable ->
                            stringResource(R.string.local_data_unreadable)
                        KnowledgeContinuationRead.Loading ->
                            stringResource(R.string.knowledge_intro)
                        is KnowledgeContinuationRead.Ready ->
                            continuation?.seriesTitle
                                ?: continuation?.scholarName
                                ?: stringResource(R.string.knowledge_intro)
                    }
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (read == KnowledgeContinuationRead.Unreadable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            continuation?.takeIf { it.durationSeconds > 0 }?.let { saved ->
                val progress = (
                    saved.positionSeconds.toFloat() / saved.durationSeconds.toFloat()
                ).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (continuation != null) R.string.continue_learning
                            else R.string.explore_lessons,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (continuation?.seriesId != null) R.string.open_series
                            else R.string.upcoming_lessons,
                        ),
                    )
                }
            }
        }
    }
}
@Composable
private fun KnowledgeCatalogCard(
    item:KnowledgeCatalogItem,
    onClick:()->Unit,
){
    ElevatedCard(
        modifier=Modifier.padding(horizontal=AtharSpacing.Lg).fillMaxWidth().clickable(onClick=onClick),
        shape=MaterialTheme.shapes.extraLarge,
    ){
        Row(
            Modifier.fillMaxWidth().heightIn(min=104.dp).padding(AtharSpacing.Lg),
            horizontalArrangement=Arrangement.spacedBy(AtharSpacing.Md),
        ){
            Surface(
                modifier=Modifier.size(48.dp),
                shape=MaterialTheme.shapes.medium,
                color=MaterialTheme.colorScheme.primaryContainer,
                border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=0.18f)),
            ){
                Icon(
                    AtharIcons.School,
                    contentDescription=null,
                    tint=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.padding(AtharSpacing.Md),
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(AtharSpacing.Xs),
            ){
                Text(item.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                item.subtitle?.let{
                    Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                item.description?.let{
                    Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            Icon(AtharIcons.ArrowForward,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
