package org.tasks.location

import org.tasks.data.MergedGeofence
import org.tasks.data.dao.LocationDao
import org.tasks.data.entity.Place
import org.tasks.preferences.AppPreferences
import javax.inject.Inject

/**
 * 4Tasks has no location permission and no location reminders, so geofences are
 * never registered. This replaces the Android location service, which needs
 * receivers and permissions this app does not declare.
 */
class NoLocationService @Inject constructor(
    override val locationDao: LocationDao,
    override val appPreferences: AppPreferences,
) : LocationService {
    override suspend fun currentLocation(): MapPosition? = null

    override fun addGeofences(geofence: MergedGeofence) = Unit

    override fun removeGeofences(place: Place) = Unit
}
