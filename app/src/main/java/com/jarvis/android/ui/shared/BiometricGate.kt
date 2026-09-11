package com.jarvis.android.ui.shared

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jarvis.android.contract.ApprovalTier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AND-W5: BiometricPrompt gate for sensitive approval tiers. Returns a suspend
 * function the UI calls before resolving an approval. If the device has no
 * enrolled biometric, we allow the action (documented V1 limitation — the
 * pairing/identity contract freeze may mandate device-bound auth once PC-A
 * policy is final).
 */
@Composable
fun rememberBiometricGate(): suspend (tier: ApprovalTier, reason: String) -> Boolean {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val available = BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val gate: suspend (ApprovalTier, String) -> Boolean = { tier, reason ->
        when {
            !requiresBiometric(tier) -> true
            activity == null || !available -> true // no biometrics: allow (V1 limitation)
            else -> authenticate(activity, reason)
        }
    }
    return remember(activity, available) { gate }
}

fun requiresBiometric(tier: ApprovalTier): Boolean =
    tier == ApprovalTier.SENSITIVE || tier == ApprovalTier.CRITICAL

private suspend fun authenticate(activity: FragmentActivity, reason: String): Boolean =
    runPrompt(activity) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm approval")
            .setSubtitle(reason)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
    }

/**
 * App-unlock prompt. Allows device credential (PIN/pattern/password) as a
 * fallback so users without enrolled biometrics aren't locked out of their own
 * app; that combination can't use a negative button.
 */
suspend fun authenticateDeviceOwner(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
): Boolean = runPrompt(activity) {
    BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()
}

private suspend fun runPrompt(
    activity: FragmentActivity,
    info: () -> BiometricPrompt.PromptInfo,
): Boolean = suspendCancellableCoroutine { cont ->
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Any hard error (cancel, lockout) fails closed.
                if (cont.isActive) cont.resume(false)
            }
        },
    )
    prompt.authenticate(info())
    cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
}
