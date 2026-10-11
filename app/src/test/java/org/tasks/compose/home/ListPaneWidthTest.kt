package org.tasks.compose.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ListPaneWidthTest {
    @Test fun `a medium window keeps the standard 360 dp`() {
        assertEquals(360.dp, listPaneWidth(600.dp))
        assertEquals(360.dp, listPaneWidth(900.dp))
    }

    @Test fun `a wide window gives the list about 40 percent`() {
        assertEquals(830.8.dp.value, listPaneWidth(2077.dp).value, 0.01f)
        assertEquals(1536f, listPaneWidth(3840.dp).value, 0.01f)
    }
}
