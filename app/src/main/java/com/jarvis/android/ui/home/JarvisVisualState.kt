package com.jarvis.android.ui.home

import com.jarvis.android.ui.components.OrbActivity

/**
 * UI-only JARVIS presence. Drives orb and ambient presentation. It is not a
 * reducer phase, transport state, or server request status.
 */
enum class JarvisVisualState {
    BOOT,
    IDLE,
    LISTENING,
    THINKING,
    RESPONDING,
    EXECUTING,
    AWAITING_APPROVAL,
    OFFLINE,
    ERROR,
}

fun JarvisVisualState.toOrbActivity(): OrbActivity = when (this) {
    JarvisVisualState.BOOT -> OrbActivity.THINKING
    JarvisVisualState.IDLE -> OrbActivity.IDLE
    JarvisVisualState.LISTENING -> OrbActivity.LISTENING
    JarvisVisualState.THINKING -> OrbActivity.THINKING
    JarvisVisualState.RESPONDING -> OrbActivity.SPEAKING
    JarvisVisualState.EXECUTING -> OrbActivity.PROCESSING
    JarvisVisualState.AWAITING_APPROVAL -> OrbActivity.PROCESSING
    JarvisVisualState.OFFLINE -> OrbActivity.OFFLINE
    JarvisVisualState.ERROR -> OrbActivity.ERROR
}

fun JarvisVisualState.hudLabel(): String = when (this) {
    JarvisVisualState.BOOT -> "BOOT"
    JarvisVisualState.IDLE -> "IDLE"
    JarvisVisualState.LISTENING -> "LISTENING"
    JarvisVisualState.THINKING -> "THINKING"
    JarvisVisualState.RESPONDING -> "RESPONDING"
    JarvisVisualState.EXECUTING -> "EXECUTING"
    JarvisVisualState.AWAITING_APPROVAL -> "AWAITING APPROVAL"
    JarvisVisualState.OFFLINE -> "OFFLINE"
    JarvisVisualState.ERROR -> "ERROR"
}
