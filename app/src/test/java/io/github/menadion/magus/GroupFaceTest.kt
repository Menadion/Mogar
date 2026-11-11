package io.github.menadion.magus

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

// groupFace picks who stands for a group on the map (People.kt). Its three rules: the freshest
// sharer wins; paused and quiet people stand only when nobody in the group is sharing; it is never
// passed me, so an empty list is a caller's error and not a case.
class GroupFaceTest {
    private val now = 1_700_000_000_000L
    private fun minutes(n: Int) = n * 60_000L

    // A member with a position, seen agoMin minutes ago. sharing = false is a paused phone; sharing
    // with agoMin past QUIET_AFTER_MS (15 min) is a quiet one.
    private fun seen(uid: String, agoMin: Int, sharing: Boolean = true) =
        Member(uid, uid, lat = 14.6, lng = 121.0, battery = 50, updatedAtMillis = now - minutes(agoMin), sharing = sharing)

    @Test fun freshestSharerWins() {
        assertEquals("new", groupFace(listOf(seen("old", 10), seen("new", 2)), now).uid)
    }

    @Test fun aSharerBeatsAFresherPausedPhone() {
        assertEquals("sharing", groupFace(listOf(seen("sharing", 10), seen("paused", 1, sharing = false)), now).uid)
    }

    @Test fun aSharerBeatsAQuietPhone() {
        assertEquals("sharing", groupFace(listOf(seen("quiet", 20), seen("sharing", 12)), now).uid)
    }

    @Test fun nobodySharingFallsBackToTheFreshest() {
        // One quiet, two paused: the freshest of them stands, quiet or not.
        val others = listOf(seen("older", 30, sharing = false), seen("fresher", 20), seen("oldest", 40, sharing = false))
        assertEquals("fresher", groupFace(others, now).uid)
    }

    @Test fun emptyGroupIsAnError() {
        try {
            groupFace(emptyList(), now)
            fail("an empty group should throw")
        } catch (expected: IllegalStateException) {
        }
    }
}
