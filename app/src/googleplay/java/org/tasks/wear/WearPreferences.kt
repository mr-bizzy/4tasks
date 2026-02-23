package org.tasks.wear

import org.tasks.preferences.Preferences
import org.tasks.preferences.QueryPreferences

class WearPreferences(
    preferences: Preferences,
    private val wearShowHidden: Boolean,
    private val wearShowCompleted: Boolean,
): QueryPreferences by preferences {
    override val showHidden: Boolean
        get() = wearShowHidden

    override val showCompleted: Boolean
        get() = wearShowCompleted
}
