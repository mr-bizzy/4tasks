package org.tasks.caldav

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.net.InetSocketAddress

/**
 * Creating a list on a server that does not behave like Radicale. The owner's SOGo (mailcow): MKCOL answers 201 but ignores the name and colour in its body
 * (the list keeps its numeric path as its name), a PROPPATCH of the 4Tasks icon answers 403, and a PROPPATCH of the name is accepted.
 */
class MakeCollectionTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()
    private var storedName = ""
    private var storedColor: String? = null

    private var appliesMkcolProps = false
    private var refusesIcon = true
    private var refusesName = false

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cal/") { ex -> handle(ex) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        requests += "${ex.requestMethod} ${ex.requestURI.path}"
        fun reply(code: Int, xml: String? = null) {
            val bytes = xml?.toByteArray()
            if (bytes != null) ex.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
            ex.sendResponseHeaders(code, if (bytes == null) -1 else bytes.size.toLong())
            bytes?.let { ex.responseBody.write(it) }
            ex.close()
        }
        when (ex.requestMethod) {
            "MKCOL" -> {
                storedName = ex.requestURI.path.trimEnd('/').substringAfterLast('/')
                if (appliesMkcolProps) {
                    storedName = Regex("<(?:\\w+:)?displayname>(.*?)</").find(body)?.groupValues?.get(1) ?: storedName
                    storedColor = Regex("<(?:\\w+:)?calendar-color[^>]*>(.*?)</").find(body)?.groupValues?.get(1)
                }
                reply(201)
            }
            "PROPPATCH" -> when {
                body.contains("x-calendar-icon") && refusesIcon -> reply(403)
                body.contains("displayname") && refusesName -> reply(403)
                else -> {
                    if (body.contains("displayname")) storedName = Regex("displayname>(.*?)</").find(body)!!.groupValues[1]
                    if (body.contains("calendar-color")) storedColor = Regex("calendar-color[^>]*>(.*?)</").find(body)!!.groupValues[1]
                    reply(207, """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>${ex.requestURI.path}</d:href><d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""")
                }
            }
            "PROPFIND" -> reply(207, """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:ic="http://apple.com/ns/ical/"><d:response><d:href>${ex.requestURI.path}</d:href><d:propstat><d:prop>
                <d:displayname>$storedName</d:displayname>${storedColor?.let { "<ic:calendar-color>$it</ic:calendar-color>" } ?: ""}</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""")
            else -> reply(405)
        }
    }

    private fun client() = CaldavClient(mock(), OkHttpClient.Builder().followRedirects(false).build(), "http://127.0.0.1:${server.address.port}/cal/".toHttpUrl())

    private fun create(name: String = "Shopping", color: Int = 0xFF3366, icon: String? = "cart") = runBlocking { client().makeCollection(name, color, icon) }

    @Test
    fun serverThatRefusesTheIconStillGetsItsList() {
        val url = create()
        assertTrue(url.startsWith("http://127.0.0.1:${server.address.port}/cal/"))
    }

    @Test
    fun serverThatIgnoresTheNameInMkcolGetsTheNameAndColourByProppatch() {
        create()
        assertEquals("Shopping", storedName)
        assertEquals("#FF3366", storedColor)
    }

    @Test
    fun serverThatAppliesMkcolIsNotPatchedForNameAndColour() {
        appliesMkcolProps = true
        refusesIcon = false
        create()
        assertEquals("Shopping", storedName)
        assertEquals(listOf("MKCOL", "PROPPATCH", "PROPFIND"), requests.map { it.substringBefore(' ') })   // the icon, then the read-back; nothing more
    }

    @Test
    fun serverThatRefusesTheNameDoesNotFailTheCreate() {
        refusesName = true
        val url = create()
        assertTrue(url.contains("/cal/"))
    }

    @Test
    fun noIconMeansNoIconRequest() {
        refusesIcon = true
        create(icon = null)
        assertEquals(listOf("MKCOL", "PROPFIND", "PROPPATCH", "PROPPATCH"), requests.map { it.substringBefore(' ') })
    }
}
