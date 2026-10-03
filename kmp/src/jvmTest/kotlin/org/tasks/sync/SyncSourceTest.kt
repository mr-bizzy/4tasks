package org.tasks.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSourceTest {
    @Test fun `with nothing requested yet, the first request of any kind is kept`() {
        SyncSource.entries.filter { it != SyncSource.NONE }.forEach {
            assertEquals(it.name, it, SyncSource.NONE.upgrade(it))
        }
    }

    @Test fun `a request that shows an indicator beats one that does not, and an immediate one beats a delayed one`() {
        assertEquals(SyncSource.USER_INITIATED, SyncSource.APP_RESUME.upgrade(SyncSource.USER_INITIATED))
        assertEquals(SyncSource.APP_RESUME, SyncSource.TASK_CHANGE.upgrade(SyncSource.APP_RESUME))
        assertEquals(SyncSource.USER_INITIATED, SyncSource.TASK_CHANGE.upgrade(SyncSource.USER_INITIATED))
    }
}
