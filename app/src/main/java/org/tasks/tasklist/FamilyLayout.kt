package org.tasks.tasklist

/**
 * How the list groups its tasks. PLAIN is the original look (sentence-case headers, rows on the page).
 * LABEL_ABOVE_CARD puts a small-capital label above one rounded card per group (4Dictate's way).
 * TITLE_IN_CARD puts the group's title inside the card (4Zones' way). The owner chooses the family
 * standard. Shipped for now: LABEL_ABOVE_CARD (owner/PM, 2026-10-03: ship what is built; may change).
 */
enum class GroupStyle { PLAIN, LABEL_ABOVE_CARD, TITLE_IN_CARD }

object FamilyLayout {
    val groupStyle: GroupStyle = GroupStyle.LABEL_ABOVE_CARD
}
