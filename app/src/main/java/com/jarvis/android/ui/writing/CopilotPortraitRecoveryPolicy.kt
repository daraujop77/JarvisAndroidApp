package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.CopilotVisualCandidate
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Narrow, side-effect-free recovery selector. Only candidates originally
 * generated from Copilot with the explicitly requested model are displayed.
 * The registry is project-scoped by the authenticated API, not by this filter.
 */
fun recoverableCopilotPortraitCandidates(
    assets: List<CopilotVisualCandidate>,
    limit: Int = 6,
): List<CopilotVisualCandidate> = assets.asSequence()
    .filter { asset ->
        val provenance = asset.provenance
        asset.status == "CANDIDATE" &&
            asset.kind == "PRIMARY_REFERENCE" &&
            asset.perspective == "front" &&
            asset.character_ids.size == 1 &&
            asset.character_ids.single().startsWith("character:") &&
            asset.asset_id.startsWith("va_") &&
            Regex("^[A-Fa-f0-9]{64}$").matches(asset.sha256) &&
            (provenance["requested_from"] as? JsonPrimitive)?.contentOrNull == "copilot" &&
            (provenance["operation"] as? JsonPrimitive)?.contentOrNull == "generation" &&
            (provenance["model"] as? JsonPrimitive)?.contentOrNull == "gpt-image-2-medium" &&
            (provenance["fallback_used"] as? JsonPrimitive)?.contentOrNull == "false"
    }
    .take(limit.coerceIn(1, 12))
    .toList()
