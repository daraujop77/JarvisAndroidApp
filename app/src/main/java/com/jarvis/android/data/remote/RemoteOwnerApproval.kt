package com.jarvis.android.data.remote

/**
 * POST_BASELINE_FUTURE_WORK.
 *
 * Client-only shell for a future remote owner check. Not a wire contract.
 * PC-A has not published the challenge schema, so this model is local UI
 * state only. Do not serialize it onto `/api/v1` or `/api/app`.
 *
 * Future path, when a contract exists: phone → authenticated JARVIS Gateway.
 * Never phone → Hermes.
 */
enum class RemoteApprovalPhase {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CONSUMED,
    DISCONNECTED,
    ERROR,
}

data class RemoteApprovalChallenge(
    val actionId: String,
    val operation: String,
    val targetIdentity: String,
    val capturedAtMs: Long,
    val expiresAtMs: Long,
    val screenshotPresent: Boolean,
    val metadataValid: Boolean,
)

data class RemoteApprovalUiModel(
    val challenge: RemoteApprovalChallenge?,
    val phase: RemoteApprovalPhase,
    val linkDown: Boolean = false,
    /** One-shot latch. A second APPROVE/DENY must not change phase. */
    val decisionConsumed: Boolean = false,
)

object RemoteApprovalPolicy {

    fun displayedPhase(model: RemoteApprovalUiModel, nowMs: Long): RemoteApprovalPhase {
        if (model.decisionConsumed && model.phase != RemoteApprovalPhase.ERROR) {
            return RemoteApprovalPhase.CONSUMED
        }
        if (model.linkDown && !model.phase.isTerminalDecision) return RemoteApprovalPhase.DISCONNECTED
        val challenge = model.challenge ?: return RemoteApprovalPhase.ERROR
        if (!challenge.metadataValid || challenge.actionId.isBlank()) return RemoteApprovalPhase.ERROR
        if (!challenge.screenshotPresent) return RemoteApprovalPhase.ERROR
        if (challenge.expiresAtMs <= challenge.capturedAtMs) return RemoteApprovalPhase.ERROR
        if (nowMs >= challenge.expiresAtMs && !model.phase.isTerminalDecision) {
            return RemoteApprovalPhase.EXPIRED
        }
        return model.phase
    }

    fun remainingMs(model: RemoteApprovalUiModel, nowMs: Long): Long {
        val exp = model.challenge?.expiresAtMs ?: return 0L
        return (exp - nowMs).coerceAtLeast(0L)
    }

    fun canApproveOnce(model: RemoteApprovalUiModel, nowMs: Long): Boolean =
        displayedPhase(model, nowMs) == RemoteApprovalPhase.PENDING && !model.decisionConsumed

    fun canDeny(model: RemoteApprovalUiModel, nowMs: Long): Boolean = canApproveOnce(model, nowMs)

    /** Stop stays available while a challenge is on screen, including after expiry. */
    fun canEmergencyStop(model: RemoteApprovalUiModel): Boolean =
        model.challenge != null && model.phase != RemoteApprovalPhase.ERROR

    fun approveOnce(model: RemoteApprovalUiModel, nowMs: Long): RemoteApprovalUiModel {
        if (!canApproveOnce(model, nowMs)) return model
        return model.copy(phase = RemoteApprovalPhase.APPROVED, decisionConsumed = true)
    }

    fun deny(model: RemoteApprovalUiModel, nowMs: Long): RemoteApprovalUiModel {
        if (!canDeny(model, nowMs)) return model
        return model.copy(phase = RemoteApprovalPhase.DENIED, decisionConsumed = true)
    }
}

private val RemoteApprovalPhase.isTerminalDecision: Boolean
    get() = this == RemoteApprovalPhase.APPROVED ||
        this == RemoteApprovalPhase.DENIED ||
        this == RemoteApprovalPhase.CONSUMED

/**
 * Future emergency stop goes through the authenticated Gateway seam.
 * No endpoint name is frozen here.
 */
interface RemoteEmergencyStop {
    suspend fun requestStop(actionId: String): RemoteStopResult
}

enum class RemoteStopResult { ACCEPTED, ALREADY_STOPPED, REJECTED, UNAVAILABLE }

class FakeRemoteEmergencyStop : RemoteEmergencyStop {
    private val stopped = mutableSetOf<String>()
    val calls = mutableListOf<String>()

    override suspend fun requestStop(actionId: String): RemoteStopResult {
        calls += actionId
        if (actionId.isBlank()) return RemoteStopResult.REJECTED
        if (!stopped.add(actionId)) return RemoteStopResult.ALREADY_STOPPED
        return RemoteStopResult.ACCEPTED
    }
}
