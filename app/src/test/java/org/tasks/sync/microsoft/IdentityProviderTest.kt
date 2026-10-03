package org.tasks.sync.microsoft

import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.tasks.BuildConfig
import org.tasks.auth.IdentityProvider
import org.tasks.sync.SyncClients
import java.io.File

@RunWith(RobolectricTestRunner::class)
class IdentityProviderTest {
    private val idp = IdentityProvider.MICROSOFT

    @Test
    fun usesTheCommonAuthorityAndOurValues() {
        assertEquals(
            "https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration",
            idp.discoveryEndpoint.toString(),
        )
        assertEquals(SyncClients.MICROSOFT_CLIENT_ID, idp.clientId)
        assertEquals(SyncClients.MICROSOFT_SCOPE, idp.scope)
        assertEquals(SyncClients.microsoftRedirectUri(BuildConfig.APPLICATION_ID), idp.redirectUri.toString())
        assertEquals("msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D", idp.redirectUri.toString())
        assertTrue(idp.multiTenant)
        assertFalse(idp.discoveryEndpoint.toString().contains("consumers"))
    }

    @Test
    fun theRedirectReachesMicrosoftInTheFormEntraRegisters() {
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"),
        )
        val request = AuthorizationRequest.Builder(config, idp.clientId, ResponseTypeValues.CODE, idp.redirectUri)
            .setScope(idp.scope)
            .build()
        val sent = request.toUri()
        // Microsoft decodes the query once and compares: that must give exactly what Entra shows.
        assertEquals("msauth://uk.mr_biz.fourtasks/XXD5Z%2FxW5wDyYUTXKmVbcimMDmo%3D", sent.getQueryParameter("redirect_uri"))
        assertEquals(idp.clientId, sent.getQueryParameter("client_id"))
        assertNotNull(sent.getQueryParameter("code_challenge")) // PKCE
        assertEquals("S256", sent.getQueryParameter("code_challenge_method"))
    }

    private fun discovered(): AuthorizationServiceConfiguration {
        val json = File("src/test/resources/microsoft/openid_configuration_common.json").readText()
        return AuthorizationServiceConfiguration(AuthorizationServiceDiscovery(JSONObject(json)))
    }

    @Test
    fun theCommonDiscoveryDocumentNamesAnIssuerNoTokenCanCarry() {
        // Recorded from https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration on 2026-10-03.
        // AppAuth rejects an ID token whose issuer is not equal to this one ("Issuer mismatch"), and the real issuer holds a tenant id.
        val doc = discovered().discoveryDoc!!
        assertEquals("https://login.microsoftonline.com/{tenantid}/v2.0", doc.issuer)
        assertEquals("https://login.microsoftonline.com/common/oauth2/v2.0/token", doc.tokenEndpoint.toString())
        assertEquals("https://graph.microsoft.com/oidc/userinfo", doc.userinfoEndpoint.toString())
    }

    @Test
    fun theRequestConfigurationKeepsTheEndpointsButNotTheIssuerCheck() {
        val discovered = discovered()
        val request = idp.requestConfig(discovered)
        assertNull("no discovery document, so AppAuth makes no issuer comparison", request.discoveryDoc)
        assertEquals(discovered.authorizationEndpoint, request.authorizationEndpoint)
        assertEquals(discovered.tokenEndpoint, request.tokenEndpoint)
        // a provider that is not multi-tenant keeps the document
        assertNotNull(idp.copy(multiTenant = false).requestConfig(discovered).discoveryDoc)
    }

    @Test
    fun noTraceOfTasksOrgsClientIdRemains() {
        // Tasks.org's Microsoft client ID starts with this; ours must be the only one anywhere in the sources.
        val theirs = "9d4b" + "abd5"
        val root = File(".").absoluteFile.normalize().let { if (File(it, "kmp").isDirectory) it else it.parentFile }
        val offenders = listOf("app/src", "kmp/src", "data/src", "app/build.gradle.kts", "gradle", "docs/SYNC-PLAN.md")
            .map { File(root, it) }.filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.length() < 2_000_000 && !f.path.contains("/build/") }.toList() }
            .filter { f -> f.extension in setOf("kt", "java", "xml", "kts", "json", "properties", "md", "toml", "txt", "pro") }
            .filter { f -> f.readText().contains(theirs) }
            // the plan names it once, to say it must go
            .filter { it.name != "SYNC-PLAN.md" }
        assertEquals("Tasks.org's client ID is still in: $offenders", emptyList<File>(), offenders)
        assertFalse(idp.clientId.startsWith(theirs))
    }
}
