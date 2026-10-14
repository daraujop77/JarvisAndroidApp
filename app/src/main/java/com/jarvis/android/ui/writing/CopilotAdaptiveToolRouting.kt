package com.jarvis.android.ui.writing

/**
 * Only guards legacy Android command interceptors, never classifies
 * the model's tool intent. When adaptive Writing Room is enabled, the
 * request must reach the model instead of opening the old static card.
 */
fun shouldProbeAdaptiveCopilotTool(
    prompt: String,
    toolMode: Boolean = false,
    skipAdaptiveProbe: Boolean = false,
): Boolean =
    !toolMode && !skipAdaptiveProbe &&
        (looksLikeCopilotAppearanceCommand(prompt) ||
            looksLikeCopilotPortraitCommand(prompt))
