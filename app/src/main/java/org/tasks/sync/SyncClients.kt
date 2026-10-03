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

    /** The one Microsoft Graph permission that holds the user's data; the rest of the scope is the standard sign-in set. */
    const val MICROSOFT_PERMISSION = "Tasks.ReadWrite"

    /** Where Microsoft's sign-in service lives. The only Microsoft hosts 4Tasks talks to are this and graph.microsoft.com. */
    const val MICROSOFT_LOGIN_HOST = "login.microsoftonline.com"

    /** The OpenID Connect discovery document of [MICROSOFT_AUTHORITY]. */
    val microsoftDiscoveryUrl: String
        get() = "https://$MICROSOFT_LOGIN_HOST/$MICROSOFT_AUTHORITY/v2.0/.well-known/openid-configuration"

    /** The app's name as an organisation's admin will see it (the Entra registration's display name). */
    const val APP_NAME = "4Tasks"

    /** Who publishes 4Tasks, as an organisation's admin will see it. */
    const val PUBLISHER_NAME = "Mr-Bizzy"
    const val PUBLISHER_DOMAIN = "mr-biz.uk"

    /** The help page for IT admins (written separately); its Microsoft part is the `#microsoft` section. */
    const val ADMIN_HELP_URL_MICROSOFT = "https://mr-biz.uk/4tasks/admin/#microsoft"

    /**
     * Google Tasks needs no client ID in code: the phone's account manager identifies 4Tasks by package name plus the
     * SHA-1 of the signing certificate, registered as an Android OAuth client in the Google Cloud project.
     */
    const val GOOGLE_TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    /** Flipped to true by the owner once the Entra registration exists and [MICROSOFT_CLIENT_ID] holds its ID. */
    val microsoftConfigured: Boolean get() = MICROSOFT_CLIENT_ID != NOT_SET

    /**
     * The Android redirect exactly as Entra's Android platform setup shows it: the hash is URL-encoded
     * (`msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D`). This is the form sent as `redirect_uri` (Microsoft
     * compares it with the registered one after decoding the query once), as Microsoft's MSAL for Android sends it.
     */
    fun microsoftRedirectUri(applicationId: String): String =
        "msauth://$applicationId/" + java.net.URLEncoder.encode(MICROSOFT_SIGNATURE_HASH, "UTF-8")

    /**
     * The same redirect as Android's intent matching sees it. Android compares an intent filter's `android:path` with the DECODED
     * path of the redirect (`Uri.getPath()`), so the manifest holds the raw hash (see MSAL for Android's manifest example), here
     * `/XXD5Z/xW5wDyYUTXKmVbcimMDmo=`. A test keeps the manifest equal to this.
     */
    val microsoftRedirectPath: String get() = "/$MICROSOFT_SIGNATURE_HASH"
}
