package org.tasks.compose.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.tasks.compose.settings.worksWithGroups
import org.tasks.injection.FlavorModule
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.gtasks_GPr_header
import tasks.kmp.generated.resources.microsoft

/** 0.1.14 (owner, 2026-10-18): the first Play release is CalDAV only; Microsoft To Do and Google Tasks are not offered. */
@RunWith(RobolectricTestRunner::class)
class CaldavOnlyTest {
    private val configuration = FlavorModule().getPlatformConfiguration()

    @Test
    fun theBuildIsCaldavOnly() {
        assertTrue(configuration.supportsCaldav)
        assertFalse(configuration.supportsGoogleTasks)
        assertFalse(configuration.supportsMicrosoft)
        assertFalse(configuration.supportsTasksOrg)
    }

    @Test
    fun addAccountListsCaldavOnly() {
        assertEquals(emptyList<Platform>(), freeAccountTypes(configuration))
        assertEquals(listOf(Platform.CALDAV), proAccountTypes(configuration))
    }

    @Test
    fun worksWithNamesNeitherMicrosoftNorGoogleTasks() {
        val titles = worksWithGroups(configuration).flatMap { it.entries }.map { it.title }
        assertFalse(Res.string.microsoft in titles)
        assertFalse(Res.string.gtasks_GPr_header in titles)
    }

    @Test
    fun withBothOnAddAccountWouldListThemFirst() {
        val both = configuration.copy(supportsGoogleTasks = true, supportsMicrosoft = true)
        assertEquals(listOf(Platform.MICROSOFT, Platform.GOOGLE_TASKS), freeAccountTypes(both))
        assertEquals(listOf(Platform.CALDAV), proAccountTypes(both))
    }
}
