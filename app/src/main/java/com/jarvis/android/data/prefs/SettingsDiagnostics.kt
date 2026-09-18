package com.jarvis.android.data.prefs

import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.contract.webv1.WebV1Codec
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry

/**
 * Phase A9 settings presentation. Everything here is derived from state the
 * app already holds; nothing is a new server claim and nothing grants a new
 * capability. Owner/guest is a local presentation flag, not an authority.
 */
object SettingsDiagnostics {

    enum class RolePresentation { OWNER, GUEST }

    enum class ProtocolCompatibility { NOT_NEGOTIATED, COMPATIBLE, INCOMPATIBLE }

    data class SecuritySummary(
        val paired: Boolean,
        val appLockEnabled: Boolean,
        val role: RolePresentation,
        /** Opaque device label. Never a key, token, or pairing secret. */
        val deviceLabel: String?,
    ) {
        val pairingLabel: String = if (paired) "Paired on this device" else "Not paired"
        val lockLabel: String = if (appLockEnabled) "Unlock required" else "Unlock off"
        val roleLabel: String = if (role == RolePresentation.OWNER) "Owner" else "Guest"
    }

    data class ProtocolState(
        val appVersionName: String,
        val appVersionCode: Int,
        val clientProtocol: String,
        val clientProtocolVersion: String,
        val negotiatedVersion: String,
        val compatibility: ProtocolCompatibility,
    ) {
        val compatibilityLabel: String = when (compatibility) {
            ProtocolCompatibility.NOT_NEGOTIATED -> "Not negotiated yet"
            ProtocolCompatibility.COMPATIBLE -> "Compatible with this app"
            ProtocolCompatibility.INCOMPATIBLE -> "Not compatible — update required"
        }
    }

    /** Local-only preferences a reset may touch. Credentials are never in this set. */
    data class LocalUiPreferences(
        val reducedMotion: Boolean = false,
        val ownerName: String = "",
        val animationIntensity: Float = 1f,
        val compactDensity: Boolean = false,
    )

    fun roleOf(isOwner: Boolean): RolePresentation =
        if (isOwner) RolePresentation.OWNER else RolePresentation.GUEST

    /**
     * Guest presentation never sees approval counts. This does not change what
     * the server will accept; it only keeps the local summary honest.
     */
    fun approvalCountForRole(role: RolePresentation, pendingApprovals: Int): Int? =
        if (role == RolePresentation.OWNER) pendingApprovals else null

    fun securitySummary(settings: SettingsStore.Settings): SecuritySummary = SecuritySummary(
        paired = settings.paired,
        appLockEnabled = settings.appLockEnabled,
        role = roleOf(settings.isOwner),
        deviceLabel = settings.deviceId?.let(::deviceLabel),
    )

    /**
     * Device ids are opaque local labels. Show a short prefix only — never the
     * full value, which could be copied into a support channel by habit.
     */
    fun deviceLabel(deviceId: String): String {
        val clean = deviceId.trim()
        if (clean.isEmpty()) return "—"
        return clean.take(DEVICE_LABEL_CHARS).uppercase()
    }

    fun connectionLabel(connection: ConnectionState, phase: SessionPhase): String = when (phase) {
        SessionPhase.REVOKED -> "Access revoked"
        SessionPhase.AUTH_EXPIRED -> "Session expired"
        SessionPhase.MISMATCH -> "Protocol mismatch"
        else -> when (connection) {
            ConnectionState.ONLINE -> "Online"
            ConnectionState.DEGRADED -> "Degraded"
            ConnectionState.CONNECTING -> "Connecting"
            ConnectionState.RECONNECTING -> "Reconnecting"
            ConnectionState.OFFLINE -> "Offline"
            ConnectionState.AUTH_EXPIRED -> "Session expired"
            ConnectionState.DEVICE_REVOKED -> "Access revoked"
            ConnectionState.PROTOCOL_MISMATCH -> "Protocol mismatch"
            ConnectionState.DISCONNECTED -> "Not connected"
        }
    }

    fun protocolState(
        appVersionName: String,
        appVersionCode: Int,
        negotiatedVersion: String,
        negotiatedFingerprint: String,
    ): ProtocolState {
        val negotiated = negotiatedVersion.isNotBlank()
        val compatible = negotiated && WebV1Codec.protocolCompatible(
            negotiatedVersion,
            negotiatedFingerprint.ifBlank { null },
        )
        return ProtocolState(
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            clientProtocol = WebV1.PROTOCOL,
            clientProtocolVersion = ContractVersion.SUPPORTED,
            negotiatedVersion = if (negotiated) negotiatedVersion else "",
            compatibility = when {
                !negotiated -> ProtocolCompatibility.NOT_NEGOTIATED
                compatible -> ProtocolCompatibility.COMPATIBLE
                else -> ProtocolCompatibility.INCOMPATIBLE
            },
        )
    }

    /**
     * Troubleshooting text. Counts and categories only — never diagnostic
     * detail strings, cursors, fingerprints, device ids, URLs or payloads.
     * Returns null when the text would not be safe to hand to someone else.
     */
    fun diagnosticsCopy(
        connection: ConnectionState,
        phase: SessionPhase,
        protocol: ProtocolState,
        entries: List<DiagnosticEntry>,
    ): String? {
        val counts = entries.groupingBy { it.kind }.eachCount()
        val lines = buildList {
            add("Jarvis Android diagnostics")
            add("App ${protocol.appVersionName} (${protocol.appVersionCode})")
            add("Protocol ${protocol.clientProtocol} ${protocol.clientProtocolVersion}")
            add("Compatibility ${protocol.compatibility.name.lowercase()}")
            add("Connection ${connectionLabel(connection, phase)}")
            add("Notes ${entries.size}")
            DiagnosticEntry.Kind.entries.forEach { kind ->
                add("${kind.name.lowercase()} ${counts[kind] ?: 0}")
            }
        }
        val text = lines.joinToString("\n")
        return text.takeIf { isSafeToCopy(it) }
    }

    fun isSafeToCopy(text: String): Boolean {
        val lower = text.lowercase()
        if (SECRET_MARKERS.any { lower.contains(it) }) return false
        if (text.contains("://")) return false
        return true
    }

    /**
     * Reset local UI chrome only. Pairing, device id, owner role, app lock,
     * gateway URL and voice settings are session/security state and survive.
     */
    fun afterUiPreferenceReset(current: SettingsStore.Settings): SettingsStore.Settings = current.copy(
        reducedMotion = false,
        ownerName = "",
        animationIntensity = 1f,
        compactDensity = false,
    )

    private const val DEVICE_LABEL_CHARS = 8

    private val SECRET_MARKERS = listOf(
        "bearer",
        "token",
        "password",
        "secret",
        "authorization",
        "keystore",
        "private key",
        "fingerprint",
        "cursor",
        "payload",
        "envelope",
        "clientrequest",
    )
}
