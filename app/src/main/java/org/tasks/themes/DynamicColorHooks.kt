package org.tasks.themes

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.DynamicColors
import org.tasks.preferences.Preferences

/**
 * "Wallpaper colour", as 4Dictate and 4Zones have it: from Android 12 the screens use the Material 3
 * dynamic scheme, so surfaces, cards and the accent all come from the user's wallpaper. When it is off
 * (the switch in Look and feel) or the phone cannot do it, the family palette is used instead.
 */
fun installDynamicColorHooks(preferences: Preferences) {
    ThemeHooks.dynamicEnabled = { isDynamicColorOn(preferences) }
    ThemeHooks.dynamicScheme = { isDark ->
        if (isDynamicColorOn(preferences)) {
            val context: Context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    }
}

private fun isDynamicColorOn(preferences: Preferences) =
    preferences.dynamicColor && DynamicColors.isDynamicColorAvailable()

/** The colour behind the screen in the current theme: the wallpaper's own, or the family navy / white. */
fun surfaceBackground(context: Context): Int =
    com.google.android.material.color.MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSurface,
        androidx.core.content.ContextCompat.getColor(context, org.tasks.R.color.content_background),
    )
