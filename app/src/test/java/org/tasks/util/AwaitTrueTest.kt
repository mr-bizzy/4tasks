package org.tasks.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitTrueTest {
    @Test fun `true at once is not waited for`() = runBlocking {
        var calls = 0
        assertTrue(awaitTrue(5, 10_000) { calls++; true })
        assertEquals(1, calls)
    }

    @Test fun `a condition that holds on the third look is found`() = runBlocking {
        var calls = 0
        assertTrue(awaitTrue(5, 1) { ++calls == 3 })
        assertEquals(3, calls)
    }

    @Test fun `a condition that never holds is looked at exactly the given number of times`() = runBlocking {
        var calls = 0
        assertFalse(awaitTrue(4, 1) { calls++; false })
        assertEquals(4, calls)
    }
}
