package org.tasks.sync.google

import org.tasks.sync.SyncClients
import java.util.Locale

/**
 * The ready-made plain-text message a user sends to their Google Workspace admin when the organisation has not
 * allowed 4Tasks (docs/SYNC-PLAN.md section 3b). The wording lives in strings_sync_google.xml; this only fills it in,
 * so the facts can be tested on the JVM and so the app and the help page say the same thing.
 */
object GoogleAdminMessage {
    /**
     * Google's own page for admins, cited for the Admin console route: "In the Google Admin console, go to Menu >
     * Security > Access and data control > API controls", then "Click Manage App Access", then "Configure new app",
     * "Enter the app's name or client ID, then click Search", and the access levels Trusted, Limited, Specific Google data
     * and Blocked. Read from the live page (last updated 2026-10-01) on 2026-10-03; the CSV route with Type Android and the
     * package name is from https://knowledge.workspace.google.com/admin/apps/add-and-configure-third-party-apps-in-bulk.
     * "Limited" being enough rests on Tasks not being a restricted service, which that page does not state outright
     * (SYNC-PLAN 3b); to be confirmed with a test Workspace domain.
     */
    const val ADMIN_GUIDE_URL =
        "https://knowledge.workspace.google.com/admin/apps/control-which-apps-access-google-workspace-data"

    /** Everything that varies between users and builds. */
    data class Facts(
        /** The user's own Google account, so the admin knows whom to enable. May be blank. */
        val account: String,
        val packageName: String,
        /** Empty until the owner has registered the Android client (SyncClients.GOOGLE_ANDROID_CLIENT_ID). */
        val clientId: String = SyncClients.GOOGLE_ANDROID_CLIENT_ID,
        val scope: String = SyncClients.GOOGLE_TASKS_SCOPE,
        val scopeTitle: String = SyncClients.GOOGLE_TASKS_SCOPE_TITLE,
        val helpUrl: String = SyncClients.GOOGLE_ADMIN_HELP_URL,
        val guideUrl: String = ADMIN_GUIDE_URL,
    )

    /** The two strings the message is made from, so a test can supply the real ones from the resource file. */
    data class Texts(
        /** `google_admin_message` */
        val message: String,
        /** `google_admin_client_id_line`: a line shown only when there is a client ID. */
        val clientIdLine: String,
    )

    fun clientIdLine(texts: Texts, facts: Facts): String =
        if (facts.clientId.isBlank()) "" else format(texts.clientIdLine, facts.clientId)

    fun build(texts: Texts, facts: Facts): String =
        format(
            texts.message,
            facts.account.ifBlank { "-" },
            facts.packageName,
            clientIdLine(texts, facts),
            facts.scope,
            facts.scopeTitle,
            facts.helpUrl,
            facts.guideUrl,
        )

    private fun format(template: String, vararg args: Any): String = String.format(Locale.getDefault(), template, *args)
}
