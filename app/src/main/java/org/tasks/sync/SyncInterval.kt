package org.tasks.sync

/**
 * How often 4Tasks checks its accounts for changes made on other devices when it is not open: the periodic background sync.
 * 15 minutes is the shortest period WorkManager allows; the owner's choice of 15, 30 or 60 (2026-10-03), default 15.
 */
object SyncInterval {
    const val DEFAULT_MINUTES = 15
    val CHOICES_MINUTES = listOf(15, 30, 60)

    /** Whatever is stored, as one of the choices (an older or hand-edited value falls back to the default). */
    fun normalise(minutes: Int): Int = if (minutes in CHOICES_MINUTES) minutes else DEFAULT_MINUTES
}
