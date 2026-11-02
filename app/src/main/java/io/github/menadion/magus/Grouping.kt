package io.github.menadion.magus

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

// Who stands together on the map (M's rule, 2026-11-02): two people are linked when they are within
// the chosen radius of each other, or when their dots would touch on the screen at the current zoom.
// Links chain, so if A is near B and B is near C, all three are one group even when A and C are
// not near each other.
//
// Plain numbers in, plain numbers out, so it can be tested without a phone.
object Grouping {
    // A pair that was linked by distance stays linked out to this many times the radius, so a GPS
    // fix that wobbles around the edge does not flip two people in and out of a group every update.
    const val STICKY = 1.25

    private const val EARTH_M = 6_371_000.0

    // x and y are the dot's place on the screen in pixels, or null when it is not known.
    data class Spot(val uid: String, val lat: Double, val lng: Double, val x: Float? = null, val y: Float? = null)

    // groups: everyone, each in exactly one group, in the order they came in; a group of one is a
    // plain dot. joined: the pairs linked by distance, to hand back in as joinedBefore next time.
    data class Result(val groups: List<List<String>>, val joined: Set<String>)

    // radiusM of 0 means "overlap only": no distance links at all.
    fun group(spots: List<Spot>, radiusM: Double, overlapPx: Float, joinedBefore: Set<String> = emptySet()): Result {
        val parent = IntArray(spots.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var c = i
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }

        val joined = mutableSetOf<String>()
        for (i in spots.indices) for (j in i + 1 until spots.size) {
            val a = spots[i]
            val b = spots[j]
            val key = pairKey(a.uid, b.uid)
            val reach = if (key in joinedBefore) radiusM * STICKY else radiusM
            val near = radiusM > 0 && metres(a.lat, a.lng, b.lat, b.lng) <= reach
            val touching = a.x != null && a.y != null && b.x != null && b.y != null &&
                hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) <= overlapPx
            if (near) joined += key
            if (near || touching) parent[find(i)] = find(j)
        }

        val byRoot = LinkedHashMap<Int, MutableList<String>>()
        spots.indices.forEach { byRoot.getOrPut(find(it)) { mutableListOf() } += spots[it].uid }
        return Result(byRoot.values.toList(), joined)
    }

    fun metres(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val p1 = Math.toRadians(aLat)
        val p2 = Math.toRadians(bLat)
        val dp = p2 - p1
        val dl = Math.toRadians(bLng - aLng)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_M * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun pairKey(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"
}
