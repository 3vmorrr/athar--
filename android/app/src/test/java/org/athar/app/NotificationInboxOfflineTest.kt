package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class NotificationInboxOfflineTest {
    private fun item(
        id:Int,
        read:Boolean=false,
        expires:String="2026-11-10T20:00:00Z",
    ):NotificationInboxItem =
        NotificationInboxContract.item(
            JsonParser.parseString(
                "{\"id\":" + id +
                    ",\"category\":\"lesson_reminders\"" +
                    ",\"source_type\":\"lesson_reminder\"" +
                    ",\"source_id\":\"" + id + "\"" +
                    ",\"title\":\"Item " + id + "\"" +
                    ",\"body\":\"Body\"" +
                    ",\"destination\":\"lesson:" + id + "\"" +
                    ",\"read\":" + read +
                    ",\"read_at\":null" +
                    ",\"expires_at\":\"" + expires + "\"" +
                    ",\"created_at\":\"2026-10-26T20:00:00Z\"}",
            ),
        )

    @Test fun offlineReadPersistsBadgeAndPendingMutationOnce() {
        val state=NotificationInboxLocalState(
            items=listOf(item(3),item(2),item(1)),
            unreadCount=3,
            updatedAtMillis=1,
        ).validated()
        val once=NotificationInboxLocalMerge.markRead(state,2,1000)
        assertEquals(2,once.unreadCount)
        assertEquals(setOf(2),once.pendingReads)
        assertTrue(once.items.first{it.id==2}.read)

        val twice=NotificationInboxLocalMerge.markRead(once,2,2000)
        assertEquals(2,twice.unreadCount)
        assertEquals(setOf(2),twice.pendingReads)
    }

    @Test fun offlineDeleteSupersedesPendingReadAndDoesNotDuplicate() {
        val state=NotificationInboxLocalState(
            items=listOf(item(3),item(2),item(1)),
            unreadCount=3,
            updatedAtMillis=1,
        ).validated()
        val read=NotificationInboxLocalMerge.markRead(state,2,1000)
        val deleted=NotificationInboxLocalMerge.delete(read,2,2000)
        assertEquals(2,deleted.unreadCount)
        assertFalse(deleted.items.any{it.id==2})
        assertFalse(2 in deleted.pendingReads)
        assertEquals(setOf(2),deleted.pendingDeletes)
    }

    @Test fun serverFirstPageReconcilesCoveredRangeWithoutDuplicates() {
        val current=NotificationInboxLocalState(
            items=listOf(item(6),item(5),item(4),item(3),item(2)),
            unreadCount=5,
            updatedAtMillis=1,
        ).validated()
        val page=NotificationInboxPage(
            items=listOf(item(7),item(6,true),item(4)),
            nextCursor="cursor",
            hasMore=true,
            unreadCount=4,
        )
        val merged=NotificationInboxLocalMerge.serverPage(current,page,true,1000)
        assertEquals(listOf(7,6,4,3,2),merged.items.map{it.id})
        assertEquals(merged.items.map{it.id}.toSet().size,merged.items.size)
        assertTrue(merged.items.first{it.id==6}.read)
        assertEquals(4,merged.unreadCount)
    }

    @Test fun encryptedPayloadCodecRoundTripsPendingStateAcrossRestart() {
        val state=NotificationInboxLocalState(
            items=listOf(item(5,true),item(4)),
            nextCursor="next",
            hasMore=true,
            unreadCount=1,
            pendingReads=setOf(5),
            pendingDeletes=setOf(3),
            updatedAtMillis=1234,
        ).validated()
        val restored=NotificationInboxLocalCodec.decode(NotificationInboxLocalCodec.encode(state))
        assertEquals(state,restored)
    }

    @Test fun expiredCachedUnreadItemStopsContributingToOfflineBadge() {
        val state=NotificationInboxLocalState(
            items=listOf(
                item(2,expires="2026-10-26T10:00:00Z"),
                item(1,expires="2026-11-10T10:00:00Z"),
            ),
            unreadCount=2,
            updatedAtMillis=1,
        ).validated()
        val visible=state.visibleAt(java.time.Instant.parse("2026-10-27T00:00:00Z").toEpochMilli())
        assertEquals(listOf(1),visible.items.map{it.id})
        assertEquals(1,visible.unreadCount)
    }
}
