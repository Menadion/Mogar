package io.github.menadion.magus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// capFamilyName (Family.kt) is the 16-character cap on a family name as it is typed (M, 2026-11-23).
// The rule that matters: typing never grows a name past the cap, but a name that is already longer
// (made before the cap) can be shortened by deleting and is never cut behind the user's back.
// satelliteCovers (Rain.kt) is the longitude check behind the "no weather coverage" message.
class FamilyNameCapTest {
    private val sixteen = "Garcia-Mendoza ab" .take(16)

    @Test fun typingUpToTheCapIsAllowed() {
        assertEquals(sixteen, Family.capFamilyName(sixteen.dropLast(1), sixteen))
    }

    @Test fun typingPastTheCapIsDropped() {
        assertEquals(sixteen, Family.capFamilyName(sixteen, sixteen + "x"))
    }

    @Test fun aLongPasteIntoAnEmptyFieldIsCutToTheCap() {
        assertEquals(sixteen, Family.capFamilyName("", sixteen + "xxxxxxxx"))
    }

    @Test fun anOldLongNameCanBeShortenedButNotGrown() {
        val old = "The Extremely Long Surname" // 26 characters, made before the cap
        assertEquals(old.dropLast(1), Family.capFamilyName(old, old.dropLast(1)))
        assertEquals(old, Family.capFamilyName(old, old + "x"))
    }

    @Test fun satelliteCoversThePhilippinesButNotLibya() {
        assertTrue(Rain.satelliteCovers(121.0))  // Manila
        assertFalse(Rain.satelliteCovers(13.2))  // Tripoli
        assertTrue(Rain.satelliteCovers(-150.0)) // past the date line, still in view
    }
}
