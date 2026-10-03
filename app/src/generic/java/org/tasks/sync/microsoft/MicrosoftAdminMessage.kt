package org.tasks.sync.microsoft

import android.content.Context
import org.tasks.BuildConfig
import org.tasks.R
import org.tasks.sync.SyncClients

/**
 * The plain-text message a user sends to their organisation's IT admin when Microsoft will not let them sign in to 4Tasks. It is
 * built from the same strings as the "What to tell your IT admin" screen (strings_sync_microsoft.xml), so the two cannot differ,
 * and from [SyncClients] for the application (client) ID and the redirect.
 */
object MicrosoftAdminMessage {
    fun subject(context: Context): String = context.getString(R.string.microsoft_admin_message_subject)

    /** [microsoftText] is what Microsoft said to the user, if anything; it is added at the end for the admin's sign-in logs. */
    fun body(
        context: Context,
        microsoftText: String?,
        clientId: String = SyncClients.MICROSOFT_CLIENT_ID,
        redirectUri: String = SyncClients.microsoftRedirectUri(BuildConfig.APPLICATION_ID),
    ): String {
        val message = context.getString(
            R.string.microsoft_admin_message,
            SyncClients.APP_NAME,
            SyncClients.PUBLISHER_NAME,
            SyncClients.PUBLISHER_DOMAIN,
            clientId,
            redirectUri,
            SyncClients.MICROSOFT_PERMISSION,
            context.getString(R.string.microsoft_admin_permission_what),
            context.getString(R.string.microsoft_admin_permission_why),
            context.getString(R.string.microsoft_admin_howto_body),
            SyncClients.ADMIN_HELP_URL_MICROSOFT,
        )
        val said = microsoftText?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.microsoft_admin_message_said, it) }
            ?: ""
        return message + said
    }
}
