package org.athar.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import kotlinx.coroutines.*
import org.athar.app.core.sync.PrivateSyncSchedule
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class PrivateSyncScheduleTest {
    @Test fun maintenanceIsDurablyUniqueAndCancellationIsScopedToItsOwnName()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>()
        assertTrue(app.graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val schedule=PrivateSyncSchedule(app,app.graph.partition);val manager=WorkManager.getInstance(app)
        val actor=987654321L;val unrelatedActor=987654322L
        fun active(owner:Long)=manager.getWorkInfosForUniqueWork(schedule.name(owner,true)).get(10,TimeUnit.SECONDS).filter{!it.state.isFinished}
        try {
            schedule.maintain(actor).result.get(10,TimeUnit.SECONDS)
            val first=active(actor).single().id
            schedule.maintain(actor).result.get(10,TimeUnit.SECONDS)
            assertEquals(first,active(actor).single().id)
            schedule.maintain(unrelatedActor).result.get(10,TimeUnit.SECONDS)
            schedule.cancel(actor)
            withTimeout(10_000){while(active(actor).isNotEmpty())delay(50)}
            assertEquals(1,active(unrelatedActor).size)
        }finally{schedule.cancel(actor);schedule.cancel(unrelatedActor)}
    }
}
