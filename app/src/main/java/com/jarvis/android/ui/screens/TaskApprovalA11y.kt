package com.jarvis.android.ui.screens

/**
 * TalkBack labels for tasks and approvals. JVM-testable, no Compose.
 */
object TaskApprovalA11y {
    const val TASKS_SCREEN = "Tasks"
    const val APPROVALS_SCREEN = "Approvals"
    const val OWNER_ONLY = "PC-action approvals are visible only to the owner on this device."
    const val EMPTY_TASKS = "No tasks in local session state."
    const val EMPTY_APPROVALS = "Nothing to approve. Requests appear here for the owner."
    const val RESOLVING_NOT_AUTHORITATIVE = "Resolving. Waiting for the server outcome. This tap is not the result."
    const val SERVER_OUTCOME = "Authoritative server outcome"
    const val EXPIRED_LOCAL = "Expired on this device clock. Waiting for the server outcome."
    const val DUPLICATE_TAP_BLOCKED = "Already submitted. Waiting for the server."
    const val BIOMETRIC_REQUIRED = "Biometric confirmation required for this approval. Not approved."
    const val TASK_DETAIL = "Task details"
    const val PROGRESS_HELD = "Progress from last known session state"

    fun group(name: String): String = "Task group $name"

    fun remaining(seconds: Long): String = "Expires in ${seconds}s on this device clock"

    fun taskRow(label: String, status: String): String = "$label. $status"
}
