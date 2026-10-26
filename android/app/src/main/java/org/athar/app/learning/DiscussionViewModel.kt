package org.athar.app.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import java.util.UUID

data class CommentThread(val rows: List<JsonElement> = emptyList(), val loading: Boolean = false, val error: Boolean = false, val expanded: Boolean = true, val page: Int = 0, val more: Boolean = false)
data class DiscussionPreview(val row: JsonElement? = null, val total: Int = 0)
data class DiscussionState(val lesson: Int = 0, val rows: List<JsonElement> = emptyList(), val loading: Boolean = true, val error: Boolean = false, val page: Int = 0, val total: Int = 0, val more: Boolean = false, val sort: String = "newest", val threads: Map<Int, CommentThread> = emptyMap(), val draft: String = "", val replyTo: JsonElement? = null, val sending: Boolean = false, val sendError: String? = null, val submitted:Boolean=false, val composerOpen:Boolean=false)
class DiscussionViewModel(private val graph: AppGraph) : ViewModel() {
    private val owner=requireNotNull(graph.session.userId)
    private val mutable = MutableStateFlow(DiscussionState()); val state = mutable.asStateFlow()
    // Draft and reply-editor changes must not recompose the surrounding player.
    val preview = state.map { DiscussionPreview(it.rows.firstOrNull(), it.total) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscussionPreview())
    private var generation = 0; private var load: Job? = null
    private var pendingBody = ""; private var pendingId = ""
    fun select(id: Int) { if (mutable.value.lesson == id) return; generation++;load?.cancel();mutable.value=DiscussionState(lesson=id);refresh() }
    fun draft(value: String) { val end=value.offsetByCodePoints(0,value.codePointCount(0,value.length).coerceAtMost(2000));mutable.value=mutable.value.copy(draft=value.substring(0,end),sendError=null,submitted=false) }
    fun reply(row: JsonElement?) { mutable.value=mutable.value.copy(replyTo=row,sendError=null,composerOpen=row!=null) }
    fun composer(open: Boolean) { mutable.value=mutable.value.copy(composerOpen=open) }
    fun sort(value: String) { if(value!=mutable.value.sort){mutable.value=mutable.value.copy(sort=value);refresh()} }
    fun refresh() = roots(true)
    fun more() = roots(false)
    private fun roots(reset: Boolean) {
        val snapshot=mutable.value;if(snapshot.lesson<1||(!reset&&snapshot.loading))return
        val epoch=generation;val page=if(reset)1 else snapshot.page+1
        load?.cancel();load=viewModelScope.launch {
            mutable.value=mutable.value.copy(loading=true,error=false)
            try {
                val data=graph.session.request("GET","lessons/${snapshot.lesson}/discussion",query=mapOf("page" to "$page","sort" to snapshot.sort),expectedUser=owner)
                if(epoch!=generation)return@launch
                val pagination=data.asJsonObject["pagination"]
                mutable.value=mutable.value.copy(rows=if(reset)data.array("items") else (mutable.value.rows+data.array("items")).distinctBy{it.text("id")},loading=false,page=page,total=pagination.text("total").toInt(),more=pagination.text("has_more")=="true")
            } catch(e:CancellationException){throw e}catch(_:Exception){if(epoch==generation)mutable.value=mutable.value.copy(loading=false,error=true)}
        }
    }
    fun thread(parent: Int, more: Boolean = false) {
        val snapshot=mutable.value;val previous=snapshot.threads[parent]
        if(previous?.loading==true)return
        if(previous!=null&&!more&&!previous.error){mutable.value=snapshot.copy(threads=snapshot.threads+(parent to previous.copy(expanded=!previous.expanded)));return}
        val epoch=generation;val page=if(more)(previous?.page?:0)+1 else 1
        mutable.value=snapshot.copy(threads=snapshot.threads+(parent to (previous?:CommentThread()).copy(loading=true,error=false,expanded=true)))
        viewModelScope.launch {
            try {
                val result=graph.session.request("GET","lessons/${snapshot.lesson}/discussion",query=mapOf("parent_id" to "$parent","page" to "$page","sort" to "oldest"),expectedUser=owner)
                if(epoch!=generation)return@launch
                val rows=if(more)previous?.rows.orEmpty()+result.array("items") else result.array("items")
                val updated=CommentThread(rows.distinctBy{it.text("id")},page=page,more=result.asJsonObject["pagination"].text("has_more")=="true")
                mutable.value=mutable.value.copy(threads=mutable.value.threads+(parent to updated))
            } catch(e:CancellationException){throw e}catch(_:Exception){if(epoch==generation)mutable.value=mutable.value.copy(threads=mutable.value.threads+(parent to (previous?:CommentThread()).copy(loading=false,error=true)))}
        }
    }
    fun send() {
        val snapshot=mutable.value;val trimmed=snapshot.draft.trim();if(snapshot.sending||trimmed.codePointCount(0,trimmed.length) !in 2..2000)return
        val parent=snapshot.replyTo?.text("id")?.toInt();val body=snapshot.draft.trim();val fingerprint="${snapshot.lesson}:$parent:$body"
        if(pendingBody!=fingerprint){pendingBody=fingerprint;pendingId=UUID.randomUUID().toString()}
        val epoch=generation;mutable.value=snapshot.copy(sending=true,sendError=null)
        viewModelScope.launch {
            try {
                graph.session.request("POST","lessons/${snapshot.lesson}/discussion",mapOf("body" to body,"parent_id" to parent,"client_id" to pendingId),expectedUser=owner)
                if(epoch!=generation)return@launch
                mutable.value=mutable.value.copy(draft="",replyTo=null,sending=false,submitted=true,composerOpen=false)
                pendingId="";pendingBody=""
                if(parent!=null){mutable.value=mutable.value.copy(threads=mutable.value.threads-parent);thread(parent)}
                refresh()
            }catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==generation)mutable.value=mutable.value.copy(sending=false,sendError=if(e is org.athar.app.core.network.ApiException)e.message.takeIf{it.isNotBlank()}?:"network_error" else "network_error")}
        }
    }
}
