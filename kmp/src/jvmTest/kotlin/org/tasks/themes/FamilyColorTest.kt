package org.tasks.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.kmp.org.tasks.themes.ColorProvider

/** The default colour is the family's sky blue: #0284C7 in light mode, #38BDF8 in dark mode. */
class FamilyColorTest {
    @Test
    fun theStoredSeedIsTheLightAccent() {
        assertEquals(0xFF0284C7.toInt(), ColorProvider.FAMILY_SEED)
    }

    @Test
    fun theAccentIsExactInBothModes() {
        assertEquals(0xFF0284C7.toInt(), ColorProvider.familyAccent(isDark = false))
        assertEquals(0xFF38BDF8.toInt(), ColorProvider.familyAccent(isDark = true))
    }

    @Test
    fun getColorResolvesTheSeedPerModeWhetherOrNotItAdjusts() {
        for (adjust in listOf(true, false)) {
            assertEquals(0xFF0284C7.toInt(), ColorProvider.getColor(ColorProvider.FAMILY_SEED, isDark = false, adjust = adjust))
            assertEquals(0xFF38BDF8.toInt(), ColorProvider.getColor(ColorProvider.FAMILY_SEED, isDark = true, adjust = adjust))
        }
    }

    @Test
    fun theFamilyColourIsAFreePresetAndIsFirstInThePicker() {
        assertTrue(ColorProvider.isFreeColor(ColorProvider.FAMILY_SEED))
        assertTrue(ColorProvider.isPresetColor(ColorProvider.FAMILY_SEED))
        assertEquals(ColorProvider.FAMILY_SEED, ColorProvider.PRESET_COLORS.first())
    }

    @Test
    fun bothResolvedAccentsAreRecognisedAsTheFamilyColour() {
        assertTrue(ColorProvider.isFamilyColor(0xFF0284C7.toInt()))
        assertTrue(ColorProvider.isFamilyColor(0xFF38BDF8.toInt()))
        assertFalse(ColorProvider.isFamilyColor(ColorProvider.BLUE_500))
    }
}
