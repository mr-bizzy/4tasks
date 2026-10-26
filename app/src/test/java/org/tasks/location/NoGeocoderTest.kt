package org.tasks.location

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.tasks.injection.FlavorModule
import java.io.File

/**
 * Privacy policy: CalDAV data goes only to the server the user entered. A synced task's coordinates must
 * not be sent anywhere to be turned into an address (0.1.14 sent them to nominatim.tasks.org).
 */
class NoGeocoderTest {
    private val moduleDir = File(".").absoluteFile.normalize().let { if (File(it, "src").isDirectory) it else File(it, "app") }

    @Test
    fun theBoundGeocoderIsNoGeocoder() {
        val provider = FlavorModule::class.java.methods.single { it.returnType == Geocoder::class.java }
        assertEquals(listOf(NoGeocoder::class.java), provider.parameterTypes.toList())
        assertEquals(NoGeocoder::class.java, FlavorModule().getGeocoder(NoGeocoder())::class.java)
    }

    @Test
    fun noGeocoderHasNothingToCallWith() {
        // One constructor, no parameters: no HTTP client, no server environment, nothing that can reach a network.
        val constructors = NoGeocoder::class.java.constructors
        assertEquals(1, constructors.size)
        assertEquals(0, constructors.single().parameterCount)
        // and no field holds anything either
        assertEquals(0, NoGeocoder::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) })
    }

    @Test
    fun noAddressIsLookedUp() = runBlocking {
        assertNull(NoGeocoder().reverseGeocode(MapPosition(51.5007, -0.1246)))
    }

    @Test
    fun noGeocoderSourceNamesNoNetworkClass() {
        val source = File(moduleDir, "src/generic/java/org/tasks/location/NoGeocoder.kt").readText()
        val network = Regex("okhttp|HttpClient|URL\\(|openConnection|https?://|ktor", RegexOption.IGNORE_CASE)
        assertFalse(network.containsMatchIn(source.substringAfter("class NoGeocoder")))
    }
}
