package org.tasks.sync

/**
 * THE ONE PLACE the sync providers' registrations are written down (docs/SYNC-PLAN.md section 5). The values the owner
 * creates in the Microsoft Entra and Google Cloud consoles drop in here and nowhere else.
 *
 * Both registrations are tied to 4Tasks' own release key (CN=4Tasks, SHA-1 5D:70:F9:67:FC:56:E7:00:F2:61:44:D7:2A:65:5B:72:29:8C:0E:6A).
 */
object SyncClients {
    /** Shown until the real Entra application (client) ID is put in [MICROSOFT_CLIENT_ID]. */
    const val NOT_SET = "00000000-0000-0000-0000-000000000000"

    /** Microsoft Entra application (client) ID: any organisation's accounts and personal ones. */
    const val MICROSOFT_CLIENT_ID = NOT_SET

    /** Base64 of the SHA-1 of the release certificate; the last part of the Android redirect `msauth://<package>/<hash>`. */
    const val MICROSOFT_SIGNATURE_HASH = "XXD5Z/xW5wDyYUTXKmVbcimMDmo="

    /**
     * Sign-in authority: `common`, so personal accounts and every organisation's work or school accounts can sign in
     * (it used to be `consumers`, personal accounts only).
     */
    const val MICROSOFT_AUTHORITY = "common"

    const val MICROSOFT_SCOPE = "user.read Tasks.ReadWrite openid offline_access email"

    /**
     * Google Tasks needs no client ID in code: the phone's account manager identifies 4Tasks by package name plus the
     * SHA-1 of the signing certificate, registered as an Android OAuth client in the Google Cloud project.
     */
    const val GOOGLE_TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    /** Flipped to true by the owner once the Entra registration exists and [MICROSOFT_CLIENT_ID] holds its ID. */
    val microsoftConfigured: Boolean get() = MICROSOFT_CLIENT_ID != NOT_SET

    /** The Android redirect Entra's Android platform setup gives, for example in the help page for IT admins. */
    fun microsoftRedirectUri(applicationId: String): String =
        "msauth://$applicationId/" + java.net.URLEncoder.encode(MICROSOFT_SIGNATURE_HASH, "UTF-8")
}
