package com.jarvis.android.voice

/**
 * Runtime RECORD_AUDIO gate. First tap without permission requests once.
 * Denied stays failed until the OS grant is already present. Never loops.
 */
class VoiceMicPermission {
    enum class Action { START, REQUEST, FAIL }

    var denied: Boolean = false
        private set
    var pendingRequest: Boolean = false
        private set

    fun onMicTapped(hasPermission: Boolean): Action {
        if (hasPermission) {
            pendingRequest = false
            return Action.START
        }
        if (denied) return Action.FAIL
        if (pendingRequest) return Action.FAIL
        pendingRequest = true
        return Action.REQUEST
    }

    fun onRequestResult(granted: Boolean): Action {
        pendingRequest = false
        if (granted) return Action.START
        denied = true
        return Action.FAIL
    }
}
