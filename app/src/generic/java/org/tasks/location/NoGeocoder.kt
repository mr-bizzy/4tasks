package org.tasks.location

import org.tasks.data.entity.Place
import javax.inject.Inject

/**
 * 4Tasks never looks a place up off the phone (privacy policy: CalDAV data goes only to the server the
 * user entered). A synced task's coordinates are kept as they arrive; no name or address is fetched.
 * Replaces GeocoderNominatim, which sent the coordinates to a Tasks.org server (0.1.15).
 */
class NoGeocoder @Inject constructor() : Geocoder {
    override suspend fun reverseGeocode(mapPosition: MapPosition): Place? = null
}
