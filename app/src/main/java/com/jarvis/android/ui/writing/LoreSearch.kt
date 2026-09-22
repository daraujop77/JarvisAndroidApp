package com.jarvis.android.ui.writing

/**
 * Filtering for the Writing Room wiki.
 *
 * Local and literal. It matches the entries the room already holds and never
 * reaches a server, so it cannot surface canon that has not been imported or
 * knowledge a character should not have. Retrieval belongs to the control
 * plane; this is a list filter.
 */
object LoreSearch {

    /**
     * Entries whose name or role contains [query], case-insensitively. A blank
     * query keeps the given order rather than guessing a ranking.
     */
    fun filter(entries: List<Pair<String, String>>, query: String): List<Pair<String, String>> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        return entries.filter { (name, role) ->
            name.contains(needle, ignoreCase = true) || role.contains(needle, ignoreCase = true)
        }
    }
}
