package org.tasks.fourlink

/**
 * Matching the list a person SAID against the lists they HAVE, forgivingly. People say "the shopping list" for a list called "Shopping" and "a note"
 * for "Notes". A match made this way is used only when exactly one list matches ([key]); a spelling that is merely close ([isClose]) is only ever
 * offered back to the user as a question, never used.
 */
object ListNames {
    private val LEADING = setOf("the", "my")
    private val TRAILING = setOf("list", "lists")

    /** The name stripped to what identifies it: no case, no punctuation or spaces, no leading "the"/"my", no trailing "list", one final "s" dropped. */
    fun key(name: String): String {
        var words = SoundsLike.words(name)
        while (words.size > 1 && words.first() in LEADING) words = words.drop(1)
        if (words.size > 1 && words.last() in TRAILING) words = words.dropLast(1)
        val last = words.lastOrNull() ?: return ""
        val singular = if (last.length > 1 && last.endsWith("s")) last.dropLast(1) else last
        return (words.dropLast(1) + singular).joinToString("")
    }

    /** "Notes" however it was said: the list a note goes to. */
    fun isNotes(name: String) = key(name) == key("Notes")

    /** A misspelling or mishearing of [wanted]: a letter or two off, or the same sound word for word. Not a match, only a candidate for a question. */
    fun isClose(wanted: String, title: String): Boolean {
        val a = key(wanted)
        val b = key(title)
        if (a.length < 3 || b.length < 3 || a == b) return false
        val allowed = if (maxOf(a.length, b.length) >= 8) 2 else 1
        return distance(a, b) <= allowed || SoundsLike.matches(a, b) && SoundsLike.matches(b, a)
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
