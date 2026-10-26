package org.athar.app

import org.athar.app.core.data.*
import org.junit.Test
import org.junit.Assert.*

class ContentCachePolicyTest {
    private val policy=ContentCachePolicy(maxEntries=2,maxBytes=100,maxEntryBytes=80,maxAgeMillis=1000)
    @Test fun evictionNeverTargetsProgressOrSyncCursor(){
        val rows=listOf("adhkar:1","lesson-progress:7","reading-position","night-sync-cursor").map{ContentCacheEntry(it,0,1000)}
        assertTrue(policy.evictions(rows,2000).isEmpty())
    }
    @Test fun newestDocumentsAreRetainedWithinBothBounds(){
        val rows=listOf(ContentCacheEntry("ar:new",100,60),ContentCacheEntry("en:middle",90,50),ContentCacheEntry("ar:old",80,30))
        assertEquals(setOf("en:middle"),policy.evictions(rows,100))
        assertEquals(setOf("ar:c"),policy.evictions(listOf("a","b","c").mapIndexed{i,key->ContentCacheEntry("ar:$key",100-i.toLong(),10)},100))
    }
    @Test fun expiredFutureAndOversizedDocumentsCannotBecomeFallbacks(){
        val rows=listOf(ContentCacheEntry("ar:expired",0,10),ContentCacheEntry("ar:future",2001,10),ContentCacheEntry("en:large",2000,81))
        assertEquals(rows.map{it.cacheKey}.toSet(),policy.evictions(rows,2000))
        assertTrue(policy.fresh(1000,2000));assertFalse(policy.fresh(999,2000))
    }
    @Test fun configurationRejectsUnboundedOrInconsistentLimits(){
        assertTrue(runCatching{ContentCachePolicy(maxEntries=0)}.isFailure)
        assertTrue(runCatching{ContentCachePolicy(maxBytes=1,maxEntryBytes=2)}.isFailure)
    }
}
