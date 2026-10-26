package org.athar.app.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.network.ApiException
import org.athar.app.core.data.CachedResult
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import java.time.Instant

data class UpcomingState(val loading:Boolean=true,val error:Boolean=false,val offline:Boolean=false,val unavailable:Boolean=false,val items:List<UpcomingLesson> = emptyList(),val page:Int=0,val hasMore:Boolean=false,val clockOffsetMillis:Long=0)
class UpcomingViewModel(
    private val lesson:Int?,
    private val pageSize:Int=20,
    private val fetch:suspend (String,Map<String,String>)->CachedResult,
):ViewModel() {
    constructor(graph:AppGraph,lesson:Int?,pageSize:Int=20):this(lesson,pageSize,{path,query->graph.content.get(path,query)})
    private val mutable=MutableStateFlow(UpcomingState())
    val state=mutable.asStateFlow()
    private var request:Job?=null
    init { refresh() }
    fun refresh(silent:Boolean=false)=load(reset=true,silent=silent)
    fun more(){if(!mutable.value.loading&&mutable.value.hasMore)load(reset=false)}
    private fun load(reset:Boolean,silent:Boolean=false) {
        if(silent&&request?.isActive==true)return
        request?.cancel()
        val previous=mutable.value;val page=if(reset)1 else previous.page+1
        mutable.value=previous.copy(loading=!silent,error=false,unavailable=false)
        request=viewModelScope.launch {
            try {
                val result=fetch(if(lesson==null)"upcoming-lessons" else "upcoming-lessons/$lesson",if(lesson==null)mapOf("page" to "$page","per_page" to "$pageSize") else emptyMap())
                val rows=if(lesson==null)result.data.array("items").map(UpcomingLesson::from) else listOf(UpcomingLesson.from(result.data))
                val items=if(reset)rows else (previous.items+rows).associateBy { it.id }.values.toList()
                val server=result.data.text("server_now").takeIf(String::isNotBlank)?.let(Instant::parse)
                val offset=if(!result.offline&&server!=null)server.toEpochMilli()-System.currentTimeMillis() else 0
                mutable.value=UpcomingState(loading=false,offline=result.offline,items=items,page=page,hasMore=lesson==null&&result.data.asJsonObject["pagination"].text("has_more")=="true",clockOffsetMillis=offset)
            } catch(failure:CancellationException){throw failure}
            catch(failure:Exception){
                val denied=failure is ApiException&&failure.status in listOf(401,403,404)
                val unavailable=failure is ApiException&&failure.status==404
                mutable.value=previous.copy(loading=false,error=!unavailable,offline=!denied,unavailable=unavailable,items=if(denied)emptyList() else previous.items,hasMore=if(denied)false else previous.hasMore)
            }
        }
    }
}
