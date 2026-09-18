package com.jarvis.android.data.prefs

import com.jarvis.android.contract.ContractVersion
import com.jarvis.android.contract.webv1.WebV1
import com.jarvis.android.data.repo.SessionPhase
import com.jarvis.android.data.state.ConnectionState
import com.jarvis.android.data.state.DiagnosticEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings summaries are presentation. They must not widen authority, must not
 * leak secrets into copyable text, and a UI reset must not touch credentials.
 */
class SettingsDiagnosticsTest {

    private fun settings(
        isOwner: Boolean = true,
        paired: Boolean = false,
        appLockEnabled: Boolean = false,
        deviceId: String? = null,
        reducedMotion: Boolean = false,
        ownerName: String = "",
        animationIntensity: Float = 1f,
        compactDensity: Boolean = false,
        gatewayBaseUrl: String = "https://gateway.example",
        voiceInputEnabled: Boolean = true,
        useFakeGateway: Boolean = false,
    ) = SettingsStore.Settings(
        isOwner = isOwner,
        paired = paired,
        appLockEnabled = appLockEnabled,
        deviceId = deviceId,
        reducedMotion = reducedMotion,
        ownerName = ownerName,
        animationIntensity = animationIntensity,
        compactDensity = compactDensity,
        gatewayBaseUrl = gatewayBaseUrl,
        voiceInputEnabled = voiceInputEnabled,
        useFakeGateway = useFakeGateway,
    )

    @Test
    fun guestPresentationHidesApprovalsAndGrantsNothing() {
        val guest = SettingsDiagnostics.securitySummary(settings(isOwner = false, paired = true))
        assertEquals(SettingsDiagnostics.RolePresentation.GUEST, guest.role)
        assertEquals("Guest", guest.roleLabel)
        assertNull(SettingsDiagnostics.approvalCountForRole(guest.role, pendingApprovals = 4))

        val owner = SettingsDiagnostics.securitySummary(settings(isOwner = true))
        assertEquals(4, SettingsDiagnostics.approvalCountForRole(owner.role, pendingApprovals = 4))
    }

    @Test
    fun securitySummaryUsesStateLabelsAndShortDeviceLabel() {
        val summary = SettingsDiagnostics.securitySummary(
            settings(paired = true, appLockEnabled = true, deviceId = "abcd1234efgh5678"),
        )
        assertEquals("Paired on this device", summary.pairingLabel)
        assertEquals("Unlock required", summary.lockLabel)
        assertEquals("ABCD1234", summary.deviceLabel)
        assertFalse(summary.deviceLabel!!.contains("efgh"))
    }

    @Test
    fun connectionLabelPrefersFailClosedPhase() {
        assertEquals(
            "Access revoked",
            SettingsDiagnostics.connectionLabel(ConnectionState.ONLINE, SessionPhase.REVOKED),
        )
        assertEquals(
            "Protocol mismatch",
            SettingsDiagnostics.connectionLabel(ConnectionState.DISCONNECTED, SessionPhase.MISMATCH),
        )
        assertEquals(
            "Reconnecting",
            SettingsDiagnostics.connectionLabel(ConnectionState.RECONNECTING, SessionPhase.CONNECTING),
        )
        assertEquals(
            "Not connected",
            SettingsDiagnostics.connectionLabel(ConnectionState.DISCONNECTED, SessionPhase.DISCONNECTED),
        )
    }

    @Test
    fun protocolStateReportsVersionAndCompatibility() {
        val pending = SettingsDiagnostics.protocolState("0.1.0-w1", 1, "", "")
        assertEquals(ContractVersion.SUPPORTED, pending.clientProtocolVersion)
        assertEquals(WebV1.PROTOCOL, pending.clientProtocol)
        assertEquals(SettingsDiagnostics.ProtocolCompatibility.NOT_NEGOTIATED, pending.compatibility)
        assertEquals("Not negotiated yet", pending.compatibilityLabel)

        val ok = SettingsDiagnostics.protocolState("0.1.0-w1", 1, WebV1.VERSION, WebV1.FINGERPRINT)
        assertEquals(SettingsDiagnostics.ProtocolCompatibility.COMPATIBLE, ok.compatibility)

        val mismatch = SettingsDiagnostics.protocolState("0.1.0-w1", 1, "2.0", WebV1.FINGERPRINT)
        assertEquals(SettingsDiagnostics.ProtocolCompatibility.INCOMPATIBLE, mismatch.compatibility)

        val badFingerprint = SettingsDiagnostics.protocolState("0.1.0-w1", 1, WebV1.VERSION, "ab".repeat(32))
        assertEquals(SettingsDiagnostics.ProtocolCompatibility.INCOMPATIBLE, badFingerprint.compatibility)
    }

    @Test
    fun diagnosticsCopyHasCountsOnlyAndNoSecrets() {
        val protocol = SettingsDiagnostics.protocolState("0.1.0-w1", 1, WebV1.VERSION, WebV1.FINGERPRINT)
        val text = SettingsDiagnostics.diagnosticsCopy(
            connection = ConnectionState.DEGRADED,
            phase = SessionPhase.READY,
            protocol = protocol,
            entries = listOf(
                DiagnosticEntry(1, DiagnosticEntry.Kind.TRANSPORT, "bearer secret token=abc"),
                DiagnosticEntry(2, DiagnosticEntry.Kind.TRANSPORT, "https://host/path?token=abc"),
                DiagnosticEntry(3, DiagnosticEntry.Kind.MALFORMED_EVENT, "{\"payload\":\"x\"}"),
            ),
        )
        assertNotNull(text)
        val copy = text!!
        assertTrue(copy.contains("transport 2"))
        assertTrue(copy.contains("malformed_event 1"))
        assertTrue(copy.contains("unknown_event 0"))
        assertTrue(copy.contains("App 0.1.0-w1 (1)"))
        assertTrue(copy.contains("Degraded"))
        assertFalse(copy.contains("bearer"))
        assertFalse(copy.contains("token"))
        assertFalse(copy.contains("http"))
        assertFalse(copy.contains("payload"))
        assertFalse(copy.contains(WebV1.FINGERPRINT))
        assertTrue(SettingsDiagnostics.isSafeToCopy(copy))
    }

    @Test
    fun unsafeTextIsRefusedRatherThanRedacted() {
        assertFalse(SettingsDiagnostics.isSafeToCopy("session token present"))
        assertFalse(SettingsDiagnostics.isSafeToCopy("see https://gateway.example"))
        assertFalse(SettingsDiagnostics.isSafeToCopy("cursor 42"))
        assertTrue(SettingsDiagnostics.isSafeToCopy("Connection Online\nNotes 0"))
    }

    @Test
    fun resetTouchesUiPreferencesOnly() {
        val before = settings(
            isOwner = true,
            paired = true,
            appLockEnabled = true,
            deviceId = "device-secret-value",
            reducedMotion = true,
            ownerName = "Ada",
            animationIntensity = 0.6f,
            compactDensity = true,
            gatewayBaseUrl = "https://gateway.example",
            voiceInputEnabled = true,
            useFakeGateway = false,
        )
        val after = SettingsDiagnostics.afterUiPreferenceReset(before)

        assertFalse(after.reducedMotion)
        assertEquals("", after.ownerName)
        assertEquals(1f, after.animationIntensity)
        assertFalse(after.compactDensity)

        assertEquals(before.paired, after.paired)
        assertEquals(before.deviceId, after.deviceId)
        assertEquals(before.isOwner, after.isOwner)
        assertEquals(before.appLockEnabled, after.appLockEnabled)
        assertEquals(before.gatewayBaseUrl, after.gatewayBaseUrl)
        assertEquals(before.voiceInputEnabled, after.voiceInputEnabled)
        assertEquals(before.preferOnDeviceRecognition, after.preferOnDeviceRecognition)
        assertEquals(before.useFakeGateway, after.useFakeGateway)
        assertTrue(after.paired)
        assertEquals("device-secret-value", after.deviceId)
    }
}
