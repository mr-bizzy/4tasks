package org.tasks.fourlink

import org.apache.commons.codec.language.DoubleMetaphone
import java.util.Locale

/**
 * Matching a misheard word by how it sounds, word by word, with Double Metaphone ("sounder" and
 * "Sandra" both encode as SNTR). Used ONLY to offer a suggestion the user confirms; a sound match
 * never completes anything by itself, and "center" is also SNTR.
 */
object SoundsLike {
    /** Query words shorter than this carry too little sound to compare ("of", "to", "a"). */
    const val MIN_WORD = 3

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    fun words(text: String): List<String> = WORD.findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()

    /** The primary and alternate codes of one word, or empty when it has no sound (digits). */
    fun codes(word: String): Set<String> {
        val dm = DoubleMetaphone()
        return setOf(dm.doubleMetaphone(word), dm.doubleMetaphone(word, true)).filter { it.isNotEmpty() }.toSet()
    }

    /**
     * True when EVERY word of [query] (at least [MIN_WORD] letters) has a word in [title] with the
     * same sound, or, for a word with no sound, the same text. A query with no such word matches nothing.
     */
    fun matches(query: String, title: String): Boolean {
        val wanted = words(query).filter { it.length >= MIN_WORD }
        if (wanted.isEmpty()) return false
        val have = words(title)
        val haveCodes = have.map { codes(it) }
        return wanted.all { w ->
            val c = codes(w)
            if (c.isEmpty()) w in have else haveCodes.any { it.any(c::contains) }
        }
    }
}
