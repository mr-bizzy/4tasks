package org.tasks.api

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.tasks.caldav.CaldavClientProvider
import org.tasks.data.dao.CaldavDao
import org.tasks.data.db.Database
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.service.TaskDeleter
import org.tasks.sync.microsoft.MicrosoftClientProvider

/** EteSync is gone since 0.1.17. A row left from before has no server: its lists are handled as local ones, with no call out. */
class ApiListManagerOldEteSyncTest {
    private val db = Room.inMemoryDatabaseBuilder<Database>()
        .setDriver(BundledSQLiteDriver())
        .addCallback(Database.CALLBACK)
        .build()
    private val caldavDao: CaldavDao = db.caldavDao()
    private val taskDeleter: TaskDeleter = mock()
    private val caldav: CaldavClientProvider = mock()
    private val microsoft: MicrosoftClientProvider = mock()
    private val manager = ApiListManager(
        caldavDao = caldavDao,
        taskDeleter = taskDeleter,
        caldavClientProvider = caldav,
        microsoftClientProvider = microsoft,
        gtasksInvoker = { throw AssertionError("no server call") },
    )
    private val account = CaldavAccount(uuid = "old", accountType = CaldavAccount.TYPE_ETEBASE)

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aNewListIsLocal() = runBlocking {
        val list = manager.create(account, "New", 0, "")

        assertEquals("New", list.name)
        assertTrue(list.url.isNullOrEmpty())
        verifyNoInteractions(caldav, microsoft)
    }

    @Test
    fun renamingChangesOnlyTheRow() = runBlocking {
        val list = oldList()

        val renamed = manager.update(account, list, "Renamed", 0, "")

        assertEquals("Renamed", renamed.name)
        assertEquals("Renamed", caldavDao.getCalendarByUuid("list")?.name)
        verifyNoInteractions(caldav, microsoft)
    }

    @Test
    fun deletingCallsNoServer() = runBlocking {
        val list = oldList()

        manager.delete(account, list)

        verify(taskDeleter).delete(list)
        verifyNoInteractions(caldav, microsoft)
    }

    private suspend fun oldList(): CaldavCalendar {
        val list = CaldavCalendar(account = "old", uuid = "list", name = "Old", url = "collection-uid")
        caldavDao.insert(list)
        return caldavDao.getCalendarByUuid("list")!!
    }
}
