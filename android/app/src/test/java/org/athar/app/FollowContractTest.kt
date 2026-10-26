package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.learning.FollowContract
import org.athar.app.learning.FollowSnapshot
import org.junit.Assert.*
import org.junit.Test

class FollowContractTest {
    @Test fun authoritativeScholarStateRoundTripsThroughPrivateCacheContract() {
        val parsed = FollowContract.parse(
            JsonParser.parseString(
                """{"target_type":"scholar","target_id":7,"following":true,"followed_at":"2026-10-26 20:00:00"}""",
            ),
            "scholar",
            7,
        )
        val decoded = FollowContract.decode(FollowContract.encode(parsed), "scholar", 7)
        assertEquals(parsed, decoded)
        assertTrue(decoded.following)
    }

    @Test fun mismatchedOrUnsupportedTargetsFailClosed() {
        assertTrue(runCatching {
            FollowContract.parse(
                JsonParser.parseString("""{"target_type":"series","target_id":7,"following":true}"""),
                "scholar",
                7,
            )
        }.isFailure)
        assertTrue(runCatching { FollowContract.validateTarget("lesson", 7) }.isFailure)
        assertTrue(runCatching { FollowContract.validateTarget("scholar", 0) }.isFailure)
    }

    @Test fun seriesStateUsesTheSameStrictContractWithoutBecomingFavoriteState() {
        val parsed = FollowContract.parse(
            JsonParser.parseString("""{"target_type":"series","target_id":9,"following":false,"followed_at":null}"""),
            "series",
            9,
        )
        assertFalse(parsed.following)
        assertEquals("series", parsed.targetType)
        assertEquals(9, parsed.targetId)
    }

    @Test fun optimisticSnapshotDoesNotInventFollowTimestamp() {
        val value = FollowSnapshot("scholar", 5, true, null)
        assertNull(value.followedAt)
    }
}
