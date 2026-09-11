package com.jarvis.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Android Keystore-backed device identity (plan AND-W3, partial skeleton).
 *
 * The private signing key is generated inside the Keystore with
 * `setRandomizedEncryptionRequired` defaults and NEVER leaves it
 * (`getPublicKey` is the only export). Full pairing handshake + short-lived
 * session credential handling waits for the pairing contract freeze (§11);
 * this class only establishes the non-exportable keypair + stable device id.
 */
class DeviceIdentityStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_identity", Context.MODE_PRIVATE)

    /** Stable public device id: fingerprint of the Keystore public key. */
    val deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)

    val isProvisioned: Boolean get() = deviceId != null

    /** Generates the Keystore keypair once; returns the device id. */
    @Synchronized
    fun provision(): String {
        deviceId?.let { return it }
        ensureKeyPair()
        val pub = publicKey() ?: error("keystore public key unavailable")
        val id = "dev_" + pub.encoded.sha256Hex().take(24)
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /** Wipe local identity on revoke/re-pair. Keystore entry is removed. */
    @Synchronized
    fun wipe() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        }
        prefs.edit().clear().apply()
    }

    fun exportPublicKeyPem(): String? {
        val pub = publicKey() ?: return null
        val b64 = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
        return buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            b64.chunked(64).forEach { append(it).append('\n') }
            append("-----END PUBLIC KEY-----\n")
        }
    }

    // ---- internals ------------------------------------------------------------

    private fun ensureKeyPair() {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(spec)
        }.generateKeyPair()
    }

    private fun publicKey(): RSAPublicKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getCertificate(ALIAS)?.publicKey as? RSAPublicKey)
            ?: run {
                // Some devices don't return the cert chain; derive from encoded spec.
                val encoded = prefs.getString(KEY_PUB_DER, null) ?: return null
                KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.NO_WRAP))) as RSAPublicKey
            }
            .also { k ->
                if (prefs.getString(KEY_PUB_DER, null) == null) {
                    prefs.edit().putString(KEY_PUB_DER, Base64.encodeToString(k.encoded, Base64.NO_WRAP)).apply()
                }
            }
    } catch (t: Throwable) {
        null
    }

    private fun ByteArray.sha256Hex(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(this).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "jarvis_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PUB_DER = "public_key_der"
    }
}
