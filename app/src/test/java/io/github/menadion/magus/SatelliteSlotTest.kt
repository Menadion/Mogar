package io.github.menadion.magus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// The satellite frame time that goes in the GIBS URL (Rain.kt, M 2026-11-23). GIBS publishes on a
// 10-minute grid and a slot not yet published is a 404, so the probe starts one slot back from
// the clock and walks older. These cover the pure half: which slots, in what order, spelled how.
class SatelliteSlotTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun theFirstCandidateIsOneSlotBackFromTheSlotTheClockIsIn() {
        // 12:54:32 is in the 12:50 slot, so the probe starts at 12:40.
        assertEquals(ms("2026-11-23T12:40:00Z"), Rain.slotCandidates(ms("2026-11-23T12:54:32Z")).first())
    }

    @Test fun candidatesWalkOlderOneSlotAtATime() {
        val slots = Rain.slotCandidates(ms("2026-11-23T12:54:32Z"))
        assertEquals(Rain.SLOT_WALK, slots.size)
        assertEquals(ms("2026-11-23T12:30:00Z"), slots[1])
        assertEquals(ms("2026-11-23T11:50:00Z"), slots.last())
        assertTrue(slots.zipWithNext().all { (a, b) -> a - b == Rain.SLOT_MS })
    }

    @Test fun anExactSlotBoundaryStillStartsOneBack() {
        assertEquals(ms("2026-11-23T12:40:00Z"), Rain.slotCandidates(ms("2026-11-23T12:50:00Z")).first())
    }

    @Test fun theWalkCrossesMidnightAndMonthEnd() {
        val slots = Rain.slotCandidates(ms("2026-12-01T00:05:00Z"))
        assertEquals("2026-11-30T23:50:00Z", Rain.slotTime(slots.first()))
    }

    @Test fun theTimeIsSpelledTheWayGibsTakesIt() {
        assertEquals("2026-11-23T12:30:00Z", Rain.slotTime(ms("2026-11-23T12:30:00Z")))
        assertEquals("2026-01-05T03:00:00Z", Rain.slotTime(ms("2026-01-05T03:00:00Z")))
    }

    @Test fun theUrlCarriesTheTimeAndKeepsTheTilePlaceholders() {
        val url = Rain.satelliteUrl(ms("2026-11-23T12:30:00Z"))
        assertTrue(url.contains("/default/2026-11-23T12:30:00Z/GoogleMapsCompatible_Level6/"))
        assertTrue(url.endsWith("{z}/{y}/{x}.png")) // y before x, GIBS order
        assertFalse(url.contains("default/default"))
    }

    @Test fun theFallbackUrlStillSaysDefault() {
        assertTrue(Rain.SATELLITE.contains("/default/default/GoogleMapsCompatible_Level6/"))
    }
}
