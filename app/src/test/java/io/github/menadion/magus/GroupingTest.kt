package io.github.menadion.magus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupingTest {
    // About 1 m of latitude, so metres can be written as a number of these.
    private val metre = 1.0 / 111_195.0
    private fun at(uid: String, northM: Double, x: Float? = null, y: Float? = null) = Grouping.Spot(uid, 14.6 + northM * metre, 121.0, x, y)
    private fun sets(r: Grouping.Result) = r.groups.map { it.toSet() }.toSet()

    @Test fun farApartStaySeparate() {
        val r = Grouping.group(listOf(at("a", 0.0), at("b", 500.0)), radiusM = 50.0, overlapPx = 0f)
        assertEquals(setOf(setOf("a"), setOf("b")), sets(r))
    }

    @Test fun insideTheRadiusGroup() {
        val r = Grouping.group(listOf(at("a", 0.0), at("b", 40.0)), radiusM = 50.0, overlapPx = 0f)
        assertEquals(setOf(setOf("a", "b")), sets(r))
    }

    @Test fun linksChain() {
        val r = Grouping.group(listOf(at("a", 0.0), at("b", 40.0), at("c", 80.0)), radiusM = 50.0, overlapPx = 0f)
        assertEquals(setOf(setOf("a", "b", "c")), sets(r))
    }

    @Test fun radiusZeroMeansOverlapOnly() {
        val apart = Grouping.group(listOf(at("a", 0.0), at("b", 1.0)), radiusM = 0.0, overlapPx = 40f)
        assertEquals(setOf(setOf("a"), setOf("b")), sets(apart))
        val touching = Grouping.group(listOf(at("a", 0.0, 100f, 100f), at("b", 500.0, 120f, 110f)), radiusM = 0.0, overlapPx = 40f)
        assertEquals(setOf(setOf("a", "b")), sets(touching))
    }

    @Test fun overlapGroupsWhateverTheRadius() {
        val r = Grouping.group(listOf(at("a", 0.0, 100f, 100f), at("b", 500.0, 110f, 100f)), radiusM = 50.0, overlapPx = 40f)
        assertEquals(setOf(setOf("a", "b")), sets(r))
    }

    @Test fun missingScreenPointsSkipOverlap() {
        val r = Grouping.group(listOf(at("a", 0.0, 100f, 100f), at("b", 500.0)), radiusM = 50.0, overlapPx = 40f)
        assertEquals(setOf(setOf("a"), setOf("b")), sets(r))
    }

    @Test fun aPairStaysJoinedPastTheRadiusUntilItReachesTheStickyLimit() {
        val spots = listOf(at("a", 0.0), at("b", 55.0))
        val fresh = Grouping.group(spots, radiusM = 50.0, overlapPx = 0f)
        assertEquals(setOf(setOf("a"), setOf("b")), sets(fresh))
        val sticky = Grouping.group(spots, radiusM = 50.0, overlapPx = 0f, joinedBefore = setOf("a|b"))
        assertEquals(setOf(setOf("a", "b")), sets(sticky))
        val gone = Grouping.group(listOf(at("a", 0.0), at("b", 70.0)), radiusM = 50.0, overlapPx = 0f, joinedBefore = setOf("a|b"))
        assertEquals(setOf(setOf("a"), setOf("b")), sets(gone))
    }

    @Test fun joinedHandsBackOnlyDistanceLinks() {
        val r = Grouping.group(listOf(at("a", 0.0, 100f, 100f), at("b", 500.0, 105f, 100f), at("c", 10.0)), radiusM = 50.0, overlapPx = 40f)
        assertEquals(setOf("a|c"), r.joined)
        assertTrue(sets(r).contains(setOf("a", "b", "c")))
    }

    @Test fun everyoneAppearsOnceInInputOrder() {
        val r = Grouping.group(listOf(at("z", 0.0), at("m", 900.0), at("k", 10.0)), radiusM = 50.0, overlapPx = 0f)
        assertEquals(listOf(listOf("z", "k"), listOf("m")), r.groups)
    }
}
