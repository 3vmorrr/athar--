package org.athar.app

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.athar.app.core.data.CachedResult
import org.athar.app.core.network.ApiException
import org.athar.app.learning.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun teardown(){Dispatchers.resetMain()}
    private fun event(id:Int=5,status:String="live")="""{"id":$id,"title":"Fixture","description":"","status":"$status","revision":1,"can_watch":true}"""
    private fun detail(offline:Boolean=false)=CachedResult(JsonParser.parseString(event()),offline)
    private fun page(id:Int,more:Boolean)=CachedResult(JsonParser.parseString("""{"items":[${event(id)}],"pagination":{"has_more":$more}}"""),false)

    @Test fun cachedLiveIsExplicitlyOfflineAndRemovedOnAuthoritative404()=runTest(dispatcher) {
        var gone=false
        val vm=UpcomingViewModel(5){_,_->if(gone)throw ApiException(404,"not_found","") else detail(true)}
        runCurrent();assertTrue(vm.state.value.offline);assertEquals(EventStatus.LIVE,vm.state.value.items.single().status)
        gone=true;vm.refresh();runCurrent()
        assertTrue(vm.state.value.unavailable);assertFalse(vm.state.value.error);assertFalse(vm.state.value.offline)
        assertTrue(vm.state.value.items.isEmpty());assertFalse(vm.state.value.hasMore)
    }
    @Test fun offlineFailureRetainsExistingItemsButForbiddenNeverRetainsThem()=runTest(dispatcher) {
        var failure:Exception?=null
        val vm=UpcomingViewModel(5){_,_->failure?.let{throw it};detail()}
        runCurrent();failure=IOException("fixture offline");vm.refresh();runCurrent()
        assertTrue(vm.state.value.error);assertTrue(vm.state.value.offline);assertEquals(5,vm.state.value.items.single().id)
        failure=ApiException(403,"forbidden","");vm.refresh();runCurrent()
        assertTrue(vm.state.value.items.isEmpty());assertFalse(vm.state.value.offline)
    }
    @Test fun aCancelledOldRefreshCannotReplaceTheNewSchedule()=runTest(dispatcher) {
        val old=CompletableDeferred<CachedResult>();var calls=0
        val vm=UpcomingViewModel(null){_,_->if(++calls==1)old.await() else page(6,false)}
        runCurrent();vm.refresh();runCurrent();old.complete(page(5,true));runCurrent()
        assertEquals(listOf(6),vm.state.value.items.map{it.id});assertFalse(vm.state.value.hasMore)
    }
    @Test fun failedPaginationRetriesSamePageAndPreviewRequestsStayBounded()=runTest(dispatcher) {
        val pages=mutableListOf<String>();var fail=true
        val vm=UpcomingViewModel(null,3){_,query->
            assertEquals("3",query["per_page"]);pages.add(query.getValue("page"))
            if(query["page"]=="1")page(5,true) else if(fail)throw IOException("fixture") else page(6,false)
        }
        runCurrent();vm.more();runCurrent();assertEquals(1,vm.state.value.page);assertEquals(listOf(5),vm.state.value.items.map{it.id})
        fail=false;vm.more();runCurrent();assertEquals(listOf("1","2","2"),pages)
        assertEquals(listOf(5,6),vm.state.value.items.map{it.id});assertFalse(vm.state.value.error)
    }
}
