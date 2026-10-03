package org.tasks.themes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme
import org.tasks.kmp.org.tasks.themes.ThemeColor
import org.tasks.kmp.org.tasks.themes.ColorProvider
import org.tasks.kmp.org.tasks.themes.ColorProvider.BLACK
import org.tasks.kmp.org.tasks.themes.ColorProvider.WHITE

const val BLUE = ColorProvider.FAMILY_SEED // the default seed is the family colour

@Composable
fun colorOn(color: Color) = colorOn(color.toArgb())

@Composable
fun colorOn(color: Int) = remember (color) { contentColorFor(color) }

val ThemeColorSpring: SpringSpec<Color> = spring(stiffness = Spring.StiffnessMedium)

private val LocalIsDarkTheme = compositionLocalOf<Boolean?> { null }

private val LocalThemeColor = compositionLocalOf { BLUE }

@Composable
@ReadOnlyComposable
private fun currentThemeColor(): Int = LocalThemeColor.current

@Composable
fun rememberThemeColor(filterTint: Int): ThemeColor {
    val isDark = isDarkTheme()
    val appColor = currentThemeColor()
    return remember(filterTint, isDark, appColor) {
        if (filterTint != 0) {
            ColorProvider.themeColor(seedColor = filterTint, isDark = isDark)
        } else {
            ColorProvider.themeColor(seedColor = appColor, isDark = isDark, adjust = false)
        }
    }
}

@Composable
@ReadOnlyComposable
fun isDarkTheme(): Boolean = LocalIsDarkTheme.current ?: isSystemInDarkTheme()

@Composable
fun isDarkTheme(theme: Int): Boolean = when (theme) {
    BaseTheme.LIGHT -> false
    BaseTheme.BLACK, BaseTheme.DARK, BaseTheme.WALLPAPER -> true
    else -> isSystemInDarkTheme()
}

/**
 * Where the platform supplies its own colours. On Android the app sets [dynamicScheme] to the Material 3
 * dynamic scheme (the wallpaper's own colours, exactly as 4Dictate and 4Zones use it) while the user has
 * "Wallpaper colour" on and the phone supports it; it answers null otherwise, and the family palette applies.
 */
object ThemeHooks {
    var dynamicScheme: @Composable (isDark: Boolean) -> ColorScheme? = { null }

    /** The same decision for code that is not Compose (the View screens). */
    var dynamicEnabled: () -> Boolean = { false }
}

/** True while the colours on screen come from the platform's dynamic scheme. */
val LocalDynamicColors = compositionLocalOf { false }

@Composable
fun TasksTheme(
    theme: Int = BaseTheme.DEFAULT,
    primary: Int = BLUE,
    content: @Composable () -> Unit,
) {
    val isDark = isDarkTheme(theme)
    val seedColor = if (primary == WHITE) BLACK else primary
    val dynamic = ThemeHooks.dynamicScheme(isDark)
    val generated = dynamic ?: dynamicColorScheme(
        seedColor = Color(seedColor),
        isDark = isDark,
    )
    val colorScheme = if (dynamic != null) dynamicVariant(dynamic, theme) else if (ColorProvider.isFamilyColor(seedColor)) familyScheme(generated, theme, isDark) else when (theme) {
        0 -> generated.copy(
            surface = Color(0xFFF0F0F0),
            background = Color.White,
            surfaceContainerLowest = Color.White,
        )
        1 -> generated.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color(0xFF121212),
        )
        2 -> generated.copy(
            surface = Color(0xFF0F1416),
            background = Color(0xFF0F1416),
            surfaceContainerLowest = Color(0xFF1B2023),
        )
        3 -> generated.copy(
            background = Color.Transparent,
            surface = Color(0x99000000),
        )
        else -> if (isDark) generated.copy(
            surface = Color(0xFF0F1416),
            background = Color(0xFF0F1416),
            surfaceContainerLowest = Color(0xFF1B2023),
        ) else generated.copy(
            surface = Color(0xFFF0F0F0),
            background = Color.White,
            surfaceContainerLowest = Color.White,
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalIsDarkTheme provides isDark,
            LocalThemeColor provides seedColor,
            LocalDynamicColors provides (dynamic != null),
        ) {
            content()
        }
    }
}

/** The platform's scheme as it is; only the two themes the user picked on purpose change the surfaces. */
private fun dynamicVariant(dynamic: ColorScheme, theme: Int): ColorScheme = when (theme) {
    1 -> dynamic.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color(0xFF121212),
    )
    3 -> dynamic.copy(
        background = Color.Transparent,
        surface = Color(0x99000000),
    )
    else -> dynamic
}

/**
 * The fallback palette of the family (mr-biz.uk): navy #0F172A background, #1E293B surfaces, muted
 * #94A3B8, accent #38BDF8 in dark and #0284C7 in light. Used when the user has not picked a colour
 * and wallpaper colour is off or unavailable.
 */
private fun familyScheme(generated: ColorScheme, theme: Int, isDark: Boolean): ColorScheme {
    if (!isDark) {
        return generated.copy(
            primary = Color(0xFF0284C7),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFBAE6FD),
            onPrimaryContainer = Color(0xFF0C4A6E),
            surface = Color(0xFFF0F0F0),
            background = Color.White,
            surfaceContainerLowest = Color.White,
        )
    }
    val base = generated.copy(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF0F172A),
        primaryContainer = Color(0xFF0C4A6E),
        onPrimaryContainer = Color(0xFFBAE6FD),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF475569),
        outlineVariant = Color(0xFF334155),
        surfaceContainerLow = Color(0xFF172033),
        surfaceContainer = Color(0xFF1E293B),
        surfaceContainerHigh = Color(0xFF243247),
        surfaceContainerHighest = Color(0xFF2B3A52),
    )
    return when (theme) {
        1 -> base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color(0xFF0B1220))
        3 -> base.copy(background = Color.Transparent, surface = Color(0x990F172A))
        else -> base.copy(
            surface = Color(0xFF0F172A),
            background = Color(0xFF0F172A),
            surfaceContainerLowest = Color(0xFF1E293B),
        )
    }
}

val WarningColor = Color(0xFFFF9800)

// Settings screen colors — referenced from ThemeBase.java for window background
const val SETTINGS_SURFACE_LIGHT = 0xFFEFECF6.toInt()
const val SETTINGS_SURFACE_DARK = 0xFF0F172A.toInt()
private const val SETTINGS_CARD_LIGHT = 0xFFF8F8FE.toInt()
private const val SETTINGS_CARD_DARK = 0xFF1E293B.toInt()

@Composable
fun TasksSettingsTheme(
    theme: Int = 5,
    primary: Int = BLUE,
    content: @Composable () -> Unit,
) {
    TasksTheme(
        theme = theme,
        primary = primary,
    ) {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val scheme = MaterialTheme.colorScheme
        MaterialTheme(
            // Cards are the stock Card colours. Under wallpaper colour the page is the scheme's own surface;
            // only the family fallback lays its own page colour.
            colorScheme = if (LocalDynamicColors.current) {
                scheme
            } else {
                scheme.copy(surface = Color(if (isDark) SETTINGS_SURFACE_DARK else SETTINGS_SURFACE_LIGHT))
            },
        ) {
            content()
        }
    }
}
