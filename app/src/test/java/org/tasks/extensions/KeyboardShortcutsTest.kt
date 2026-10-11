package org.tasks.extensions

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_ESCAPE
import android.view.KeyEvent.KEYCODE_F
import android.view.KeyEvent.KEYCODE_N
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardShortcutsTest {
    private fun key(code: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, action: Int = ACTION_DOWN, repeat: Int = 0) =
        keyboardShortcut(action, code, repeat, ctrl, alt, shift)

    @Test fun `ctrl N is a new task, ctrl F is search, esc is back`() {
        assertEquals(KeyboardShortcut.NEW_TASK, key(KEYCODE_N, ctrl = true))
        assertEquals(KeyboardShortcut.SEARCH, key(KEYCODE_F, ctrl = true))
        assertEquals(KeyboardShortcut.BACK, key(KEYCODE_ESCAPE))
    }

    @Test fun `plain letters are typing, not shortcuts`() {
        assertNull(key(KEYCODE_N))
        assertNull(key(KEYCODE_F))
    }

    @Test fun `other modifiers, key up and held keys do nothing`() {
        assertNull(key(KEYCODE_N, ctrl = true, shift = true))
        assertNull(key(KEYCODE_F, ctrl = true, alt = true))
        assertNull(key(KEYCODE_ESCAPE, ctrl = true))
        assertNull(key(KEYCODE_N, ctrl = true, action = ACTION_UP))
        assertNull(key(KEYCODE_ESCAPE, repeat = 1))
    }
}
