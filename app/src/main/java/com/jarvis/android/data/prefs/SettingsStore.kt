package com.jarvis.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        /**
         * Lesson ids completed per learner, encoded `profileId|lessonId`.
         * Local only — never sent anywhere. One child's progress never unlocks
         * another's rewards.
         */
        val lessonProgress: Set<String> = emptySet(),
        /** Learner shown when the Learning Room opens. */
        val activeLearnerId: String = com.jarvis.android.data.learning.LearnerProfiles.DEFAULT_ID,
        /** Local child profiles, encoded `id|Name`. */
        val learnerProfiles: Set<String> = emptySet(),
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
        val LESSON_PROGRESS = stringSetPreferencesKey("lesson_progress")
        val ACTIVE_LEARNER = stringPreferencesKey("active_learner")
        val LEARNER_PROFILES = stringSetPreferencesKey("learner_profiles")
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
                lessonProgress = prefs[Keys.LESSON_PROGRESS] ?: emptySet(),
                activeLearnerId = prefs[Keys.ACTIVE_LEARNER]
                    ?: com.jarvis.android.data.learning.LearnerProfiles.DEFAULT_ID,
                learnerProfiles = prefs[Keys.LEARNER_PROFILES] ?: emptySet(),
            )
        }

    suspend fun setUseFake(value: Boolean) = context.dataStore.edit { it[Keys.USE_FAKE] = value }
    suspend fun setBaseUrl(value: String) = context.dataStore.edit { it[Keys.BASE_URL] = value }
    suspend fun setReducedMotion(value: Boolean) = context.dataStore.edit { it[Keys.REDUCED_MOTION] = value }
    suspend fun setAppLock(value: Boolean) = context.dataStore.edit { it[Keys.APP_LOCK] = value }
    suspend fun setOwnerName(value: String) = context.dataStore.edit { it[Keys.OWNER_NAME] = value }
    suspend fun setIsOwner(value: Boolean) = context.dataStore.edit { it[Keys.IS_OWNER] = value }
    suspend fun setHasAvatar(value: Boolean) = context.dataStore.edit { it[Keys.HAS_AVATAR] = value }

    /** Records one finished lesson for one learner. Repeating it changes nothing. */
    suspend fun completeLesson(profileId: String, lessonId: String) = context.dataStore.edit {
        val key = com.jarvis.android.data.learning.LessonProgress.encode(profileId, lessonId)
        it[Keys.LESSON_PROGRESS] = (it[Keys.LESSON_PROGRESS] ?: emptySet()) + key
    }

    suspend fun setActiveLearner(profileId: String) = context.dataStore.edit {
        it[Keys.ACTIVE_LEARNER] = com.jarvis.android.data.learning.LearnerProfiles.sanitize(profileId)
    }

    /** Adds a child profile. A blank name is ignored; the id comes from the name. */
    suspend fun addLearner(name: String) = context.dataStore.edit { prefs ->
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@edit
        val id = com.jarvis.android.data.learning.LearnerProfiles.sanitize(trimmed)
        prefs[Keys.LEARNER_PROFILES] = (prefs[Keys.LEARNER_PROFILES] ?: emptySet()) + "$id|$trimmed"
        prefs[Keys.ACTIVE_LEARNER] = id
    }
    suspend fun setPaired(value: Boolean, deviceId: String? = null) = context.dataStore.edit {
        it[Keys.PAIRED] = value
        if (deviceId != null) it[Keys.DEVICE_ID] = deviceId else it.remove(Keys.DEVICE_ID)
    }
}
