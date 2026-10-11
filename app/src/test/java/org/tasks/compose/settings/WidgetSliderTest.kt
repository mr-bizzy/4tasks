package org.tasks.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSliderTest {
    @Test fun `opacity snaps to steps of 5`() {
        assertEquals(0, snapToStep(2.4f, 0..100, 5))
        assertEquals(5, snapToStep(2.6f, 0..100, 5))
        assertEquals(100, snapToStep(99f, 0..100, 5))
    }

    @Test fun `font size snaps to whole points from 10`() {
        assertEquals(10, snapToStep(10.2f, 10..22, 1))
        assertEquals(17, snapToStep(16.6f, 10..22, 1))
    }
}
