package org.athar.app.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.network.ApiException
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import java.time.Instant

data class UpcomingState(val loading:Boolean=true,val error:Boolean=false,val offline:Boolean=false,val items:List<UpcomingLesson> = emptyList(),val page:Int=0,val hasMore:Boolean=false,val clockOffsetMillis:Long=0)
class UpcomingViewModel(private val graph:AppGraph,private val lesson:Int?):ViewModel() {
    private val mutable=MutableStateFlow(UpcomingState())
    val state=mutable.asStateFlow()
    private var request:Job?=null
    init { refresh() }
    fun refresh(silent:Boolean=false)=load(reset=true,silent=silent)
    fun more(){if(!mutable.value.loading&&mutable.value.hasMore)load(reset=false)}
    private fun load(reset:Boolean,silent:Boolean=false) {
        request?.cancel()
        val previous=mutable.value;val page=if(reset)1 else previous.page+1
        mutable.value=previous.copy(loading=!silent,error=false)
        request=viewModelScope.launch {
            try {
                val result=graph.content.get(if(lesson==null)"upcoming-lessons" else "upcoming-lessons/$lesson",if(lesson==null)mapOf("page" to "$page","per_page" to "20") else emptyMap())
                val rows=if(lesson==null)result.data.array("items").map(UpcomingLesson::from) else listOf(UpcomingLesson.from(result.data))
                val items=if(reset)rows else (previous.items+rows).associateBy { it.id }.values.toList()
                val server=result.data.text("server_now").takeIf(String::isNotBlank)?.let(Instant::parse)
                val offset=if(!result.offline&&server!=null)server.toEpochMilli()-System.currentTimeMillis() else 0
                mutable.value=UpcomingState(false,false,result.offline,items,page,lesson==null&&result.data.asJsonObject["pagination"].text("has_more")=="true",offset)
            } catch(failure:CancellationException){throw failure}
            catch(failure:Exception){mutable.value=previous.copy(loading=false,error=true,offline=true,items=if(failure is ApiException&&failure.status==404)emptyList() else previous.items)}
        }
    }
}
