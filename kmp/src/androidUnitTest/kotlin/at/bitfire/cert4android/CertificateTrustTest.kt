package at.bitfire.cert4android

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

/**
 * 4Tasks' two rulings on certificates (docs/SYNC-PLAN.md, section 8):
 * "Advanced: allow self-signed certificates" gates user trust (off by default), and a host name that does not match
 * is refused EVEN WHEN the user trusted the certificate. Real self-signed certificates, made with keytool.
 */
@RunWith(RobolectricTestRunner::class)
class CertificateTrustTest {
    private lateinit var store: CustomCertStore

    @Before fun setUp() {
        store = CustomCertStore.getInstance(RuntimeEnvironment.getApplication() as Context)
        store.clearUserDecisions()
    }

    // ---- the store: user trust is gated by the switch ----------------------------------------------------

    @Test(timeout = 20_000) fun `a certificate the user trusted is accepted when the switch is on`() {
        val cert = selfSigned("server.example")
        store.setTrustedByUser(cert)
        assertTrue(store.isTrusted(arrayOf(cert), "RSA", trustSystemCerts = false, appInForeground = null, allowUserTrust = true))
    }

    @Test(timeout = 20_000) fun `the same certificate is refused, with no prompt, when the switch is off`() {
        val cert = selfSigned("server.example")
        store.setTrustedByUser(cert)
        // appInForeground = true would open the trust screen and wait for the user; with the switch off the
        // store must answer at once. The timeout on this test fails it if a prompt is attempted.
        assertFalse(store.isTrusted(arrayOf(cert), "RSA", trustSystemCerts = false, appInForeground = true, allowUserTrust = false))
    }

    @Test(timeout = 20_000) fun `an unknown self-signed certificate is refused with no prompt when the switch is off`() {
        val cert = selfSigned("other.example")
        assertFalse(store.isTrusted(arrayOf(cert), "RSA", trustSystemCerts = true, appInForeground = true, allowUserTrust = false))
    }

    @Test(timeout = 20_000) fun `a changed certificate for the same name is not the one the user trusted`() {
        val trusted = selfSigned("server.example")
        val replacement = selfSigned("server.example")   // same name, new key
        store.setTrustedByUser(trusted)
        assertTrue(store.isTrustedByUser(trusted))
        assertFalse(store.isTrustedByUser(replacement))
        // in the background (non-interactive) an unknown certificate is refused rather than trusted silently
        assertFalse(store.isTrusted(arrayOf(replacement), "RSA", trustSystemCerts = false, appInForeground = null, allowUserTrust = true))
    }

    @Test(timeout = 20_000) fun `clearing user decisions forgets what the user trusted`() {
        val cert = selfSigned("server.example")
        store.setTrustedByUser(cert)
        store.clearUserDecisions()
        assertFalse(store.isTrustedByUser(cert))
    }

    // ---- the manager: the switch reaches the store; the host name always has to match -----------------------

    private class RecordingStore(private val answer: Boolean = true) : CertStore {
        var lastAllowUserTrust: Boolean? = null
        var calls = 0
        override fun isTrusted(chain: Array<X509Certificate>, authType: String, trustSystemCerts: Boolean, appInForeground: Boolean?, allowUserTrust: Boolean): Boolean {
            calls++; lastAllowUserTrust = allowUserTrust; return answer
        }
        override fun isTrustedByUser(cert: X509Certificate) = true
        override fun setTrustedByUser(cert: X509Certificate) {}
        override fun setUntrustedByUser(cert: X509Certificate) {}
        override fun clearUserDecisions() {}
    }

    private fun settings(allow: Boolean) = object : SettingsProvider {
        override val appInForeground: Boolean? = null
        override val trustSystemCerts = true
        override val allowUserTrust = allow
    }

    @Test fun `the manager passes the switch to the store`() {
        val cert = selfSigned("server.example")
        for (allow in listOf(true, false)) {
            val rec = RecordingStore()
            CustomCertManager(rec, settings(allow)).checkServerTrusted(arrayOf(cert), "RSA")
            assertEquals(allow, rec.lastAllowUserTrust)
        }
    }

    @Test fun `a trusted certificate is accepted under the name it carries`() {
        val rec = RecordingStore()
        val verifier = CustomCertManager(rec, settings(true)).HostnameVerifier { host, _ -> host == "server.example" }
        assertTrue(verifier.verify("server.example", session(selfSigned("server.example"))))
    }

    @Test fun `a trusted certificate is REFUSED under a wrong name, and the store is never asked`() {
        val rec = RecordingStore(answer = true)   // the store would say yes, as if the user had trusted the certificate
        val verifier = CustomCertManager(rec, settings(true)).HostnameVerifier { host, _ -> host == "server.example" }
        assertFalse(verifier.verify("10.0.2.2", session(selfSigned("server.example"))))
        assertFalse(verifier.verify("evil.example", session(selfSigned("server.example"))))
        assertEquals("user trust must not be consulted for a name check", 0, rec.calls)
    }

    @Test fun `with no platform name check nothing is verified`() {
        val verifier = CustomCertManager(RecordingStore(), settings(true)).HostnameVerifier()
        assertFalse(verifier.verify("server.example", session(selfSigned("server.example"))))
    }

    private fun session(cert: X509Certificate): SSLSession {
        val s = mock(SSLSession::class.java)
        `when`(s.peerCertificates).thenReturn(arrayOf(cert))
        return s
    }

    /** A real self-signed certificate for [cn] with a fresh key, made by the JDK's keytool. */
    private fun selfSigned(cn: String): X509Certificate {
        val dir = File.createTempFile("cert", "").also { it.delete(); it.mkdirs() }
        val ks = File(dir, "ks.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        fun run(vararg args: String) {
            val p = ProcessBuilder(keytool, *args).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "keytool failed: $out" }
        }
        run("-genkeypair", "-alias", "k", "-keyalg", "RSA", "-keysize", "2048", "-validity", "30", "-dname", "CN=$cn",
            "-ext", "san=dns:$cn", "-keystore", ks.path, "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit")
        val pem = File(dir, "c.pem")
        run("-exportcert", "-rfc", "-alias", "k", "-keystore", ks.path, "-storepass", "changeit", "-file", pem.path)
        return pem.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
    }
}
