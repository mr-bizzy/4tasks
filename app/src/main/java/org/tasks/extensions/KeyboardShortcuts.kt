package org.tasks.extensions

import android.view.KeyEvent
import androidx.activity.ComponentActivity

/**
 * A hardware keyboard (Samsung DeX, a tablet's keyboard): what a key press asks for, or null for anything the
 * screen should handle as usual. Only the first press counts, not the repeats of a held key.
 */
enum class KeyboardShortcut { NEW_TASK, SEARCH, BACK }

fun keyboardShortcut(action: Int, keyCode: Int, repeatCount: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): KeyboardShortcut? {
    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return null
    val plain = !alt && !shift
    return when {
        keyCode == KeyEvent.KEYCODE_ESCAPE && !ctrl && plain -> KeyboardShortcut.BACK
        keyCode == KeyEvent.KEYCODE_N && ctrl && plain -> KeyboardShortcut.NEW_TASK
        keyCode == KeyEvent.KEYCODE_F && ctrl && plain -> KeyboardShortcut.SEARCH
        else -> null
    }
}

val KeyEvent.shortcut: KeyboardShortcut?
    get() = keyboardShortcut(action, keyCode, repeatCount, isCtrlPressed, isAltPressed, isShiftPressed)

/** Esc does exactly what Back does on this screen (closes a sub-page, a search, the task being edited). */
fun ComponentActivity.escapeIsBack(event: KeyEvent): Boolean {
    if (event.shortcut != KeyboardShortcut.BACK) return false
    onBackPressedDispatcher.onBackPressed()
    return true
}
