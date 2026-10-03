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

    @Test fun `a changed task is pushed at once as expedited work, a metadata change still waits`() {
        assertEquals(false, SyncSource.TASK_CHANGE.waitsInWorkManager)
        assertEquals(true, SyncSource.TASK_CHANGE.expedited)
        assertEquals(true, SyncSource.METADATA_CHANGE.waitsInWorkManager)
        assertEquals(false, SyncSource.METADATA_CHANGE.expedited)
    }

    @Test fun `the pushes a person waits on are expedited`() {
        assertEquals(true, SyncSource.USER_INITIATED.expedited)
        assertEquals(true, SyncSource.APP_BACKGROUND.expedited)
        assertEquals(false, SyncSource.BACKGROUND.expedited)
    }
}
