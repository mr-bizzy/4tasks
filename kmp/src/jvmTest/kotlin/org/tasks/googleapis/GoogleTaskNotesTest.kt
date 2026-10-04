package org.tasks.googleapis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A long dictated note in a Google Tasks list: Google holds 8,192 characters, the phone keeps the whole text, and a pull never replaces the whole text
 * with Google's cut copy (docs/SYNC-DESIGN.md, "Long notes").
 */
class GoogleTaskNotesTest {
    private val max = 8192
    private fun note(chars: Int) = (0..chars).joinToString(" ") { "word$it" }.take(chars)

    /** What Google would store and send back for a note we pushed. */
    private fun pushed(local: String) = GoogleTaskSynchronizer.truncate(local, max)

    @Test fun `the test notes are the length they claim`() {
        assertEquals(12_000, note(12_000).length)
        assertEquals(8000, note(8000).length)
    }

    @Test fun `an 8000 character note goes to Google whole and a pull keeps it`() {
        val local = note(8000)
        assertEquals(local, pushed(local))
        assertEquals(local, GoogleTaskSynchronizer.getTruncatedValue(local, pushed(local), max))
    }

    @Test fun `a note longer than Google allows is pushed cut and kept whole locally after the pull`() {
        val local = note(12_000)
        val onGoogle = pushed(local)!!
        assertEquals(max, onGoogle.length)
        assertEquals(local, GoogleTaskSynchronizer.getTruncatedValue(local, onGoogle, max))
    }

    @Test fun `it survives repeated syncs`() {
        var local = note(12_000)
        repeat(3) {
            val onGoogle = pushed(local)
            local = GoogleTaskSynchronizer.getTruncatedValue(local, onGoogle, max)!!
        }
        assertEquals(note(12_000), local)
    }

    @Test fun `a note changed on another device replaces the local text`() {
        val local = note(12_000)
        val edited = "rewritten elsewhere"
        assertEquals(edited, GoogleTaskSynchronizer.getTruncatedValue(local, edited, max))
        val shorter = pushed(local)!!.take(5000)
        assertEquals(shorter, GoogleTaskSynchronizer.getTruncatedValue(local, shorter, max))
        assertNotEquals(local, shorter)
    }

    @Test fun `a note emptied on Google is emptied here`() {
        assertEquals("", GoogleTaskSynchronizer.getTruncatedValue(note(12_000), "", max))
    }
}
