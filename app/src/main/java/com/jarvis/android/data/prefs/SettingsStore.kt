package com.jarvis.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

/**
 * Non-secret settings only. Secrets/session credentials must live behind the
 * Android Keystore (AND-W3) and never in plain DataStore (plan §17, AND-W3 gate).
 */
class SettingsStore(private val context: Context) {

    data class Settings(
        val useFakeGateway: Boolean = true,
        val gatewayBaseUrl: String = "",
        val reducedMotion: Boolean = false,
        val paired: Boolean = false,
        val deviceId: String? = null,
        /** Require biometric unlock when the app comes to the foreground. */
        val appLockEnabled: Boolean = false,
        val ownerName: String = "",
        /**
         * OWNER devices can see and resolve PC-action approvals. Family/guest
         * identities never receive them (plan §25: family identities are a
         * staged extension; this flag is the client-side gate).
         */
        val isOwner: Boolean = true,
        /** True once a local avatar JPEG has been written. */
        val hasAvatar: Boolean = false,
        /** Voice V0: local push-to-talk. Off until the owner opts in. */
        val voiceInputEnabled: Boolean = false,
        /** Network recognition is never used. This only gates the on-device engine. */
        val preferOnDeviceRecognition: Boolean = true,
        /** Speak completed assistant replies. Off by default. */
        val readRepliesAloud: Boolean = false,
        val ttsRate: Float = 1f,
    )

    private object Keys {
        val USE_FAKE = booleanPreferencesKey("use_fake_gateway")
        val BASE_URL = stringPreferencesKey("gateway_base_url")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val PAIRED = booleanPreferencesKey("paired")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val IS_OWNER = booleanPreferencesKey("is_owner")
        val HAS_AVATAR = booleanPreferencesKey("has_avatar")
        val VOICE_INPUT = booleanPreferencesKey("voice_input_enabled")
        val PREFER_ON_DEVICE = booleanPreferencesKey("prefer_on_device_recognition")
        val READ_ALOUD = booleanPreferencesKey("read_replies_aloud")
        val TTS_RATE = floatPreferencesKey("tts_rate")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { t ->
            // Read failures must not crash the app; serve defaults instead.
            if (t is IOException) emit(emptyPreferences()) else throw t
        }
        .map { prefs ->
            Settings(
                useFakeGateway = prefs[Keys.USE_FAKE] ?: true,
                gatewayBaseUrl = prefs[Keys.BASE_URL] ?: "",
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                paired = prefs[Keys.PAIRED] ?: false,
                deviceId = prefs[Keys.DEVICE_ID],
                appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
                ownerName = prefs[Keys.OWNER_NAME] ?: "",
                isOwner = prefs[Keys.IS_OWNER] ?: true,
                hasAvatar = prefs[Keys.HAS_AVATAR] ?: false,
                voiceInputEnabled = prefs[Keys.VOICE_INPUT] ?: false,
                preferOnDeviceRecognition = prefs[Keys.PREFER_ON_DEVICE] ?: true,
                readRepliesAloud = prefs[Keys.READ_ALOUD] ?: false,
                ttsRate = prefs[Keys.TTS_RATE] ?: 1f,
            )
        }

    suspend fun setUseFake(value: Boolean) = context.dataStore.edit { it[Keys.USE_FAKE] = value }
    suspend fun setBaseUrl(value: String) = context.dataStore.edit { it[Keys.BASE_URL] = value }
    suspend fun setReducedMotion(value: Boolean) = context.dataStore.edit { it[Keys.REDUCED_MOTION] = value }
    suspend fun setAppLock(value: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = value }
    suspend fun setOwnerName(value: String) = context.dataStore.edit { it[Keys.OWNER_NAME] = value }
    suspend fun setIsOwner(value: Boolean) = context.dataStore.edit { it[Keys.IS_OWNER] = value }
    suspend fun setHasAvatar(value: Boolean) = context.dataStore.edit { it[Keys.HAS_AVATAR] = value }
    suspend fun setVoiceInputEnabled(value: Boolean) = context.dataStore.edit { it[Keys.VOICE_INPUT] = value }
    suspend fun setPreferOnDeviceRecognition(value: Boolean) = context.dataStore.edit { it[Keys.PREFER_ON_DEVICE] = value }
    suspend fun setReadRepliesAloud(value: Boolean) = context.dataStore.edit { it[Keys.READ_ALOUD] = value }
    suspend fun setTtsRate(value: Float) = context.dataStore.edit { it[Keys.TTS_RATE] = value.coerceIn(0.5f, 2f) }
    suspend fun setPaired(value: Boolean, deviceId: String? = null) = context.dataStore.edit {
        it[Keys.PAIRED] = value
        if (deviceId != null) it[Keys.DEVICE_ID] = deviceId else it.remove(Keys.DEVICE_ID)
    }
}
