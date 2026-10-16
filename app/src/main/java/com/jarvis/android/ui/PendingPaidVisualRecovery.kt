package com.jarvis.android.ui

import com.jarvis.android.transport.live.JarvisAppSession
import com.jarvis.android.transport.live.verifyPaidCandidateIntegrity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val PAID_VISUAL_GLOBAL_GENERATION_ROUTE = "/api/app/images/generations"
internal const val PAID_VISUAL_UNRESOLVED_CAUSE = "Previous paid image request is unresolved. Recover it first."
internal const val PAID_VISUAL_UNRESOLVED_NOTICE = "Hay una imagen pendiente de confirmar. Revísala en el aviso superior."

internal enum class PaidVisualRecoveryStep { RELEASE_CANDIDATE, STAGE_ORIGINAL, KEEP }

/**
 * Recovery policy for one pending paid attempt. Unknown and unverifiable outcomes
 * are always kept: only an explicit user confirmation may release them.
 */
internal fun paidVisualRecoveryStep(state: JarvisAppSession.PendingPaidVisualState): PaidVisualRecoveryStep =
    when (state) {
        is JarvisAppSession.PendingPaidVisualState.Completed -> when {
            state.receipt["visual_asset"].let { it == null || it is JsonNull } ->
                PaidVisualRecoveryStep.STAGE_ORIGINAL
            isVerifiedStoredCandidate(state.receipt) -> PaidVisualRecoveryStep.RELEASE_CANDIDATE
            else -> PaidVisualRecoveryStep.KEEP
        }
        is JarvisAppSession.PendingPaidVisualState.Unknown,
        is JarvisAppSession.PendingPaidVisualState.Unverifiable -> PaidVisualRecoveryStep.KEEP
    }

/** Bytes of a receipt that never produced a stored candidate (global generation or candidate_error). */
internal fun paidVisualOriginalBytes(receipt: JsonObject): Pair<String, String>? {
    val data = receipt.text("data_base64")?.takeIf(String::isNotBlank) ?: return null
    val mime = receipt.text("mime_type")?.takeIf(String::isNotBlank) ?: return null
    return data to mime
}

/** Only the global generation route belongs in the active chat; Studio and Writing Room results do not. */
internal fun paidVisualRecordsInChat(receipt: JsonObject): Boolean =
    receipt.text("route") == PAID_VISUAL_GLOBAL_GENERATION_ROUTE

internal fun paidVisualUserMessage(message: String?): String? =
    if (message != null && message.contains(PAID_VISUAL_UNRESOLVED_CAUSE)) {
        PAID_VISUAL_UNRESOLVED_NOTICE
    } else {
        message
    }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun isVerifiedStoredCandidate(receipt: JsonObject): Boolean {
    val asset = receipt["visual_asset"] as? JsonObject ?: return false
    val storageState = (asset["storage"] as? JsonObject)?.text("state").orEmpty()
    val projectId = receipt.text("project_id").orEmpty()
    return projectId.isNotBlank() &&
        asset.text("project_id") == projectId &&
        !asset.text("asset_id").isNullOrBlank() &&
        asset.text("status") == "CANDIDATE" &&
        (storageState == "stored" || storageState.startsWith("pending")) &&
        runCatching { verifyPaidCandidateIntegrity(receipt, asset) }.isSuccess
}
