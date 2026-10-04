package org.tasks.fourlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListNamesTest {
    @Test fun `case, punctuation and spaces do not matter`() {
        assertEquals(ListNames.key("Shopping"), ListNames.key("  shopping!! "))
        assertEquals(ListNames.key("To-do"), ListNames.key("to do"))
    }

    @Test fun `a leading the or my, a trailing list and one final s go`() {
        assertEquals(ListNames.key("Shopping"), ListNames.key("the shopping list"))
        assertEquals(ListNames.key("Shopping"), ListNames.key("my Shopping Lists"))
        assertEquals(ListNames.key("Notes"), ListNames.key("note"))
        assertEquals("task", ListNames.key("My tasks"))
    }

    @Test fun `a name that is only list or the keeps its word`() {
        assertEquals("list", ListNames.key("list"))
        assertEquals("the", ListNames.key("the"))
        assertEquals("", ListNames.key("!!!"))
    }

    @Test fun `only one final s goes`() {
        assertEquals("ass", ListNames.key("asss"))
    }

    @Test fun `Notes is recognised however it is said`() {
        assertTrue(ListNames.isNotes("Notes") && ListNames.isNotes("my note list") && ListNames.isNotes("note"))
        assertFalse(ListNames.isNotes("Notebooks"))
    }

    @Test fun `close means a letter or two off, or the same sound, and never the same key`() {
        assertTrue(ListNames.isClose("Shoping", "Shopping"))
        assertTrue(ListNames.isClose("Grocerys", "Groceries"))
        assertFalse(ListNames.isClose("Shopping", "the shopping list"))
        assertFalse(ListNames.isClose("Garden", "Shopping"))
        assertFalse(ListNames.isClose("ab", "ac"))
    }
}
