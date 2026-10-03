package org.tasks.sync

enum class SyncSource(val showIndicator: Boolean, val immediate: Boolean = true) {
    NONE(false),
    USER_INITIATED(true),
    PUSH_NOTIFICATION(false),
    CONTENT_OBSERVER(true),
    BACKGROUND(false),
    TASK_CHANGE(showIndicator = true, immediate = false),
    METADATA_CHANGE(showIndicator = false, immediate = false),
    APP_BACKGROUND(false),
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
