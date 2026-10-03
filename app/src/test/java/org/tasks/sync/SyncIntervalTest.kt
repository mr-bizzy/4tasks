package org.tasks.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncIntervalTest {
    @Test fun `the three choices are kept`() {
        SyncInterval.CHOICES_MINUTES.forEach { assertEquals(it, SyncInterval.normalise(it)) }
        assertEquals(listOf(15, 30, 60), SyncInterval.CHOICES_MINUTES)
    }

    @Test fun `anything else becomes 15, the default and WorkManager's shortest period`() {
        listOf(0, 1, 14, 16, 45, 120, -5).forEach { assertEquals(15, SyncInterval.normalise(it)) }
        assertEquals(15, SyncInterval.DEFAULT_MINUTES)
    }
}
