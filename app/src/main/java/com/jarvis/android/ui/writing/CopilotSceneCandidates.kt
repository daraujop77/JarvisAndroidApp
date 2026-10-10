package com.jarvis.android.ui.writing

import com.jarvis.android.transport.live.VisualStudioAsset

/** Pure, project-scoped recovery; never guesses IDs or selects an unrelated portrait. */
fun selectCopilotSceneReviewAsset(
    projectId: String,
    rootAssetId: String,
    assets: List<VisualStudioAsset>,
): VisualStudioAsset? {
    val sha = Regex("^[A-Fa-f0-9]{64}$")
    val root = assets.singleOrNull {
        it.asset_id == rootAssetId && it.project_id == projectId &&
            it.kind == "SCENE_ART" && sha.matches(it.sha256)
    } ?: return null
    val family = mutableMapOf(root.asset_id to root.sha256)
    val eligible = mutableListOf(root)
    repeat(16) {
        val descendants = assets.filter { candidate ->
            candidate.kind == "SCENE_ART" &&
                candidate.project_id == projectId &&
                candidate.status in setOf("CANDIDATE", "APPROVED") &&
                candidate.storage.state == "stored" &&
                sha.matches(candidate.sha256) &&
                candidate.parent_asset_id in family &&
                candidate.parent_sha256.equals(
                    family[candidate.parent_asset_id], ignoreCase = true,
                ) && !family.containsKey(candidate.asset_id)
        }
        descendants.forEach {
            family[it.asset_id] = it.sha256
            eligible.add(it)
        }
    }
    return eligible.filter {
        it.status in setOf("CANDIDATE", "APPROVED") &&
            it.storage.state == "stored"
    }.maxWithOrNull(
        compareBy<VisualStudioAsset> { it.created_utc }.thenBy { it.asset_id }
    )
}
