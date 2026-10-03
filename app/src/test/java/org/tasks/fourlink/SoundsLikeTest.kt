package org.tasks.fourlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundsLikeTest {
    @Test fun `sounder and Sandra share a sound`() {
        assertEquals(setOf("SNTR"), SoundsLike.codes("sounder"))
        assertEquals(setOf("SNTR"), SoundsLike.codes("Sandra"))
        assertTrue(SoundsLike.matches("sounder", "Get back to Sandra Elaine about her reservation"))
    }

    @Test fun `every query word must match some word of the title`() {
        assertTrue(SoundsLike.matches("sounder reservation", "Get back to Sandra Elaine about her reservation"))
        assertFalse(SoundsLike.matches("sounder invoice", "Get back to Sandra Elaine about her reservation"))
    }

    @Test fun `words that sound different do not match`() {
        assertFalse(SoundsLike.matches("Sunday", "Call Sandra"))
        assertFalse(SoundsLike.matches("plumber", "Pay rent"))
    }

    @Test fun `case and punctuation are ignored`() {
        assertTrue(SoundsLike.matches("SOUNDER!", "call sandra, please"))
    }

    @Test fun `a word with no sound, a number, must match as text`() {
        assertTrue(SoundsLike.matches("room 204", "Book room 204 for Sandra"))
        assertFalse(SoundsLike.matches("room 205", "Book room 204 for Sandra"))
    }

    @Test fun `a query of only short words matches nothing`() {
        assertFalse(SoundsLike.matches("to do", "Anything"))
    }
}
