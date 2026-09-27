package com.jarvis.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RefreshCredential(
    val token: String,
    val expiresUtc: String?,
)

interface RefreshCredentialStore {
    fun save(token: String, expiresUtc: String?)
    fun load(): RefreshCredential?
    fun hasUsableCredential(): Boolean
    fun clear()
}

/**
 * Stores the long-lived Android device refresh credential encrypted with an
 * AES key generated inside Android Keystore. The refresh token is never written
 * to SharedPreferences in plaintext.
 *
 * BiometricPrompt remains the user-presence gate in the UI before this
 * credential is used. Keeping encryption and prompting as separate seams also
 * lets JVM tests exercise refresh rotation without an Android Keystore.
 */
class SecureRefreshCredentialStore(context: Context) : RefreshCredentialStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun save(token: String, expiresUtc: String?) {
        if (token.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply {
                if (expiresUtc == null) remove(KEY_EXPIRES) else putString(KEY_EXPIRES, expiresUtc)
            }
            .apply()
    }

    @Synchronized
    override fun load(): RefreshCredential? = runCatching {
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return null
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null)?.let {
            Base64.decode(it, Base64.NO_WRAP)
        } ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val token = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        if (token.isBlank()) return null
        RefreshCredential(token, prefs.getString(KEY_EXPIRES, null))
    }.getOrElse {
        // A replaced/invalidated Keystore key must fail closed.
        clear()
        null
    }

    override fun hasUsableCredential(): Boolean {
        val credential = load() ?: return false
        val expiry = credential.expiresUtc ?: return true
        return runCatching {
            Instant.parse(expiry).isAfter(Instant.now().plusSeconds(30))
        }.getOrDefault(false)
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "jarvis_refresh_credential_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS = "jarvis_refresh_credential"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_EXPIRES = "expires_utc"
    }
}

/** In-memory implementation for JVM/unit tests. */
class MemoryRefreshCredentialStore : RefreshCredentialStore {
    private var credential: RefreshCredential? = null

    override fun save(token: String, expiresUtc: String?) {
        credential = token.takeIf { it.isNotBlank() }?.let { RefreshCredential(it, expiresUtc) }
    }

    override fun load(): RefreshCredential? = credential

    override fun hasUsableCredential(): Boolean {
        val value = credential ?: return false
        val expiry = value.expiresUtc ?: return true
        return runCatching {
            Instant.parse(expiry).isAfter(Instant.now().plusSeconds(30))
        }.getOrDefault(false)
    }

    override fun clear() {
        credential = null
    }
}
