package org.tasks.themes

import com.materialkolor.palettes.TonalPalette

fun darkModeColor(seedColor: Int): Int =
    TonalPalette.fromInt(seedColor).tone(80)

data class ChipColors(val backgroundColor: Int, val contentColor: Int)

fun chipColors(seedColor: Int, isDark: Boolean): ChipColors {
    val palette = TonalPalette.fromInt(seedColor)
    return if (isDark) {
        ChipColors(palette.tone(30), palette.tone(90))
    } else {
        ChipColors(palette.tone(90), palette.tone(10))
    }
}
