package org.tasks.sync.microsoft

import org.json.JSONObject
import org.tasks.sync.SyncClients
import java.util.Base64

/**
 * Reads the claims of the ID token Microsoft returns at sign-in (not to trust it for access, only to name the account and to check
 * who issued it). The token comes straight from Microsoft's token endpoint over TLS, in exchange for our own authorisation code
 * and PKCE verifier; AppAuth still checks audience, expiry and nonce.
 *
 * Why this exists: with the `common` authority, AppAuth's own issuer check cannot work (see IdentityProvider.multiTenant), so the
 * issuer is checked here instead: it must be `https://login.microsoftonline.com/<the token's own tid>/v2.0`.
 */
object MicrosoftIdToken {
    fun claims(idToken: String?): JSONObject? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        return try {
            JSONObject(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: org.json.JSONException) {
            null
        }
    }

    /** True when `iss` is Microsoft's v2.0 issuer for the tenant named in the token's own `tid` claim. */
    fun issuerMatchesTenant(claims: JSONObject): Boolean {
        val iss = claims.optString("iss", "")
        val tid = claims.optString("tid", "")
        return GUID.matches(tid) && iss == "https://${SyncClients.MICROSOFT_LOGIN_HOST}/$tid/v2.0"
    }

    /** A tenant id is a GUID, so the document's literal `{tenantid}` template can never pass for one. */
    private val GUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /**
     * What to call the account: the address the userinfo endpoint gave, else the `email` claim, else `preferred_username` (a work or
     * school account need not have an `email` claim, so the sign-in must not fail for want of one).
     */
    fun accountName(claims: JSONObject?, userinfoEmail: String?): String? =
        userinfoEmail?.takeIf { it.isNotBlank() }
            ?: claims?.optString("email", "")?.takeIf { it.isNotBlank() }
            ?: claims?.optString("preferred_username", "")?.takeIf { it.isNotBlank() }
}
