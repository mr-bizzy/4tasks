package org.tasks.caldav

import at.bitfire.dav4jvm.okhttp.BasicDigestAuthHandler
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

/**
 * The password a person types must reach the server byte for byte. A server (here a plain JDK one)
 * asks for Basic authentication; the client is built the way CaldavClientProvider builds it, and
 * the server records what it was sent. Passwords with symbols, spaces and non-ASCII letters.
 */
class BasicAuthPasswordTest {
    private lateinit var server: HttpServer
    private val received = mutableListOf<String>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val header = ex.requestHeaders.getFirst("Authorization")
            if (header == null) {
                ex.responseHeaders.add("WWW-Authenticate", "Basic realm=\"test\", charset=\"UTF-8\"")
                ex.sendResponseHeaders(401, -1)
            } else {
                received += String(Base64.getDecoder().decode(header.removePrefix("Basic ")), Charsets.UTF_8)
                ex.sendResponseHeaders(204, -1)
            }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun send(username: String, password: String): String? {
        received.clear()
        val auth = BasicDigestAuthHandler(null, username, password.toCharArray())
        val client = OkHttpClient.Builder().addNetworkInterceptor(auth).authenticator(auth).build()
        client.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/dav").build())
            .execute().use { assertEquals(204, it.code) }
        return received.lastOrNull()
    }

    @Test
    fun symbolHeavyPasswordIsSentUnchanged() {
        val password = "p@ss:w0rd!#\$%&*()+=/\\\"'<>~^|{}[];,.?-_`"
        assertEquals("biz@example.org:$password", send("biz@example.org", password))
    }

    @Test
    fun passwordWithSpacesIsSentUnchanged() {
        val password = " lead  mid trail "
        assertEquals("u:$password", send("u", password))
    }

    @Test
    fun nonAsciiPasswordIsSentAsUtf8() {
        val password = "Grüße-ñ-€-日本"
        assertEquals("u:$password", send("u", password))
    }

    @Test
    fun aMailcowStyleAppPasswordIsSentUnchanged() {
        val password = "AbCd1-EfGh2-IjKl3-MnOp4-QrSt5"
        val sent = send("biz@example.org", password)
        assertNotNull(sent)
        assertEquals("biz@example.org:$password", sent)
    }
}
