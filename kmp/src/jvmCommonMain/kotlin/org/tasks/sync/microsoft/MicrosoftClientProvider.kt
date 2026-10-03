package org.tasks.sync.microsoft

import org.tasks.data.entity.CaldavAccount

interface MicrosoftClientProvider {
    suspend fun getService(account: CaldavAccount): MicrosoftService

    suspend fun hasCredentials(account: CaldavAccount): Boolean = !account.password.isNullOrBlank()

    /**
     * A plainer text for an error this provider can explain (for example a work account with no mailbox, or one whose
     * organisation has switched Microsoft To Do off), or null to show the exception's own message.
     */
    fun describeError(e: Exception): String? = null
}
