package com.jarvis.android.ui.screens

/**
 * TalkBack labels for the debug Projects shell. JVM-testable, no Compose.
 * Fixture claims stay labeled as not live-backed.
 */
object ProjectsA11y {
    const val SCREEN = "Projects. Local fixtures. Not live-backed."
    const val NOT_LIVE = "Local fixtures. Not live-backed."
    const val RELEASE_HIDDEN = "Projects are not shown. This build has no live projects contract."
    const val EMPTY = "No local project fixtures on this device."
    const val LOADING = "Loading local project fixtures."
    const val ERROR = "Local project fixtures unavailable."
    const val OFFLINE = "Offline. Local project fixtures are not a live backend."
    const val SEARCH = "Search local project fixtures"
    const val FILTER_ALL = "Show all local fixtures"
    const val FILTER_ACTIVE = "Show active local fixtures"
    const val FILTER_ARCHIVED = "Show archived local fixtures"
    const val SORT_UPDATED = "Sort local fixtures by updated time"
    const val SORT_TITLE = "Sort local fixtures by title"
    const val DETAIL = "Local project fixture details"
    const val ACTIVITY_PLACEHOLDER = "No live activity. Fixture placeholder. Not live-backed."
    const val NO_CONVERSATIONS = "No conversations linked to this local fixture."
    const val EMPTY_SEARCH = "No local fixtures match this search on this device."

    fun card(title: String, state: String, chats: String): String =
        "$title. $state. $chats. Local fixture. Not live-backed."

    fun conversation(title: String): String = "$title. Linked on this device only."
}
