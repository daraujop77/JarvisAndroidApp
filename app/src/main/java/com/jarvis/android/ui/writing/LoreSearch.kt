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

    /**
     * What a character is allowed to see. Scope is enforced before anything is
     * rendered, so the screen cannot accidentally show an author secret or a
     * locked future event on a character's page. When the control plane is
     * connected this decision moves server-side; the rule stays the same.
     */
    fun visibleFacts(facts: List<LoreFact>, character: String): List<LoreFact> =
        facts.filter { fact ->
            when (fact.scope) {
                "author" -> false
                "locked" -> false
                "character" -> fact.knownTo.contains(character)
                else -> true
            }
        }
}

/** A fact and who may see it. `knownTo` only matters for character scope. */
data class LoreFact(
    val claim: String,
    val scope: String,
    val knownTo: Set<String> = emptySet(),
)
