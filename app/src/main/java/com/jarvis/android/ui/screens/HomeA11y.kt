package com.jarvis.android.ui.screens

/**
 * TalkBack labels for the Home command center. Kept out of Compose so JVM
 * tests can assert copy without driving the UI.
 */
object HomeA11y {
    const val SCREEN = "JARVIS home"
    const val PROFILE_UNKNOWN = "Active model unknown. Not reported by server."
    const val APPROVALS_OWNER_ONLY = "Pending approvals are owner-only on this device."
    const val TASKS_EMPTY = "No running tasks in local session state."
    const val CONVERSATIONS_EMPTY = "No recent chats on this device."
    const val PROJECTS_NOT_LIVE = "Local fixtures. Not live-backed."
    const val PROJECTS_HIDDEN = "Projects are not shown. This build has no live projects contract."
    const val DIAGNOSTICS_UNKNOWN_PROTOCOL = "Protocol version unknown."
    const val QUICK_CHAT = "Open chat"
    const val QUICK_TASKS = "Open tasks"
    const val QUICK_APPROVALS = "Open approvals"
    const val QUICK_PROJECTS = "Open projects"
    const val QUICK_SETTINGS = "Open settings"

    fun visualState(label: String): String = "JARVIS visual state $label"

    fun runningTasks(count: Int): String = "$count running tasks from local session state"

    fun pendingApprovals(count: Int): String = "$count pending approvals for owner"
}
