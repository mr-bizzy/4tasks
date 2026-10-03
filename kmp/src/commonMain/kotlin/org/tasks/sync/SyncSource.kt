package org.tasks.sync

/**
 * @param immediate how the request ranks against others (see [upgrade]): a changed task is a debounced request, not an immediate one.
 * @param waitsInWorkManager whether the work is also held back by an initial delay once it is handed to the background scheduler.
 * @param expedited whether it is run as expedited work, so that it starts at once with the app's own priority.
 */
enum class SyncSource(
    val showIndicator: Boolean,
    val immediate: Boolean = true,
    val waitsInWorkManager: Boolean = !immediate,
    val expedited: Boolean = false,
) {
    NONE(false),
    USER_INITIATED(true, expedited = true),
    PUSH_NOTIFICATION(false),
    CONTENT_OBSERVER(true),
    BACKGROUND(false),
    // A changed task is pushed as soon as SyncAdapters' 1 s debounce ends, while the app is still in front, as expedited work: a
    // delayed background job can be held for ever once the app is cached (Android blocks the UID's network, so the job's
    // CONNECTIVITY never holds; seen on a Galaxy S25, Android 17).
    TASK_CHANGE(showIndicator = true, immediate = false, waitsInWorkManager = false, expedited = true),
    METADATA_CHANGE(showIndicator = false, immediate = false),
    APP_BACKGROUND(false, expedited = true),
    APP_RESUME(false),
    ACCOUNT_ADDED(true),
    PURCHASE_COMPLETED(true),
    SHARING_CHANGE(true),
    ;

    fun upgrade(other: SyncSource): SyncSource = when {
        // Nothing requested yet: whatever comes first is what is requested. (Before, NONE kept itself against sources that show
        // no indicator and are not delayed, APP_RESUME among them, so opening the app never started a sync.)
        this == NONE -> other
        other.showIndicator && !this.showIndicator -> other
        other.immediate && !this.immediate -> other
        else -> this
    }

    companion object {
        fun fromString(value: String?): SyncSource =
            try {
                valueOf(value ?: NONE.name)
            } catch (_: IllegalArgumentException) {
                NONE
            }
    }
}
