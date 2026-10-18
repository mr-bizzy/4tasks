package org.tasks.injection

import com.google.android.material.color.DynamicColors
import com.todoroo.andlib.utility.AndroidUtilities
import org.tasks.AppStore
import org.tasks.PlatformConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.tasks.billing.DesktopLinkService
import org.tasks.billing.QrScanner
import org.tasks.location.Geocoder
import org.tasks.location.GeocoderNominatim
import org.tasks.location.LocationService
import org.tasks.location.NoLocationService
import org.tasks.location.MapFragment
import org.tasks.location.OsmMapFragment
import org.tasks.wear.WearRefresher

@Module
@InstallIn(SingletonComponent::class)
class FlavorModule {
    @Provides
    // CalDAV only (any server; owner, 2026-10-18: the first Play release is free and CalDAV-only). Google Tasks and
    // Microsoft To Do are off and must not be named anywhere a user can reach; their code stays in the tree for later
    // (docs/SYNC-PLAN.md). Tasks.org's own account, Etebase, OpenTasks, geofences and calendar events stay off.
    fun getPlatformConfiguration() = PlatformConfiguration(
        supportsTasksOrg = false,
        supportsCaldav = true,
        supportsGoogleTasks = false,
        supportsMicrosoft = false,
        supportsOpenTasks = false,
        supportsEteSync = false,
        supportsBackupImport = true,
        supportsGeofences = false,
        supportsCalendarEvents = false,
        appStore = AppStore.FDROID,
        isAndroid = true,
        isLibre = true,
        supportsWidgets = true,
        supportsLogExport = true,
        supportsNotificationTroubleshooting = true,
        supportsSystemNotificationSettings = true,
        supportsOngoingNotifications = AndroidUtilities.preUpsideDownCake(),
        supportsBundledNotifications = true,
        supportsVoiceReminders = true,
        supportsCompletionSound = true,
        supportsSwipeToSnooze = true,
        supportsRingMode = true,
        supportsDynamicColor = DynamicColors.isDynamicColorAvailable(),
        supportsLauncherIcon = true,
        supportsMarkdownToggle = true,
        supportsWallpaperTheme = true,
        supportsAutoNightTheme = true,
        supportsLanguageSelection = true,
    )

    @Provides
    fun getLocationService(service: NoLocationService): LocationService = service

    @Provides
    fun getMapFragment(osm: OsmMapFragment): MapFragment = osm

    @Provides
    fun getGeocoder(nominatim: GeocoderNominatim): Geocoder = nominatim

    @Provides
    fun getWearRefresher(): WearRefresher = object : WearRefresher {
        override suspend fun refresh() = Unit
    }

    @Provides
    fun getQrScanner(): QrScanner = object : QrScanner {
        override suspend fun scan(): String? = null
    }

    @Provides
    fun getDesktopLinkService(): DesktopLinkService = object : DesktopLinkService {
        override suspend fun confirmLink(code: String) = false
    }
}
