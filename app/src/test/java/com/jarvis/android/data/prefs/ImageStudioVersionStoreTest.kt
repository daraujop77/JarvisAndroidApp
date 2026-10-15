package com.jarvis.android.data.prefs

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStudioVersionStoreTest {

    @Test
    fun emptyChainShowsOnlyTheRoot() {
        val store = ImageStudioVersionStore(FakePrefs(), "user-a")

        assertEquals(listOf("att_root"), store.versions("att_root"))
    }

    @Test
    fun appendKeepsRootFirstAndNeverDuplicates() {
        val store = ImageStudioVersionStore(FakePrefs(), "user-a")

        assertTrue(store.append("att_root", "visual_v2"))
        assertTrue(store.append("att_root", "visual_v2"))
        assertTrue(store.append("att_root", "att_root"))

        assertEquals(listOf("att_root", "visual_v2"), store.versions("att_root"))
    }

    @Test
    fun versionsReloadFromPrefsInANewStoreInstance() {
        val prefs = FakePrefs()
        assertTrue(ImageStudioVersionStore(prefs, "user-a").append("att_root", "visual_v2"))
        assertTrue(ImageStudioVersionStore(prefs, "user-a").append("att_root", "gen_v3"))

        assertEquals(
            listOf("att_root", "visual_v2", "gen_v3"),
            ImageStudioVersionStore(prefs, "user-a").versions("att_root"),
        )
    }

    @Test
    fun chainsAreIsolatedByAccount() {
        val prefs = FakePrefs()
        assertTrue(ImageStudioVersionStore(prefs, "user-a").append("att_root", "visual_v2"))

        assertEquals(listOf("att_root"), ImageStudioVersionStore(prefs, "user-b").versions("att_root"))
    }

    @Test
    fun chainIsWrittenUnderAccountAndRootKey() {
        val prefs = FakePrefs()
        assertTrue(ImageStudioVersionStore(prefs, "user-a").append("att_root", "visual_v2"))

        assertEquals("att_root,visual_v2", prefs.values["jarvis.imagestudio.user-a.att_root"])
    }

    @Test
    fun failedCommitReportsFalseAndKeepsThePreviousChain() {
        val prefs = FakePrefs().apply { commitSucceeds = false }
        val store = ImageStudioVersionStore(prefs, "user-a")

        assertFalse(store.append("att_root", "visual_v2"))
        assertEquals(listOf("att_root"), store.versions("att_root"))
    }

    @Test
    fun appendFailsClosedWithoutAccountOrWithUnsafeIds() {
        val prefs = FakePrefs()

        assertFalse(ImageStudioVersionStore(prefs, "").append("att_root", "visual_v2"))
        val store = ImageStudioVersionStore(prefs, "user-a")
        assertFalse(store.append("att_root", "visual,v2"))
        assertFalse(store.append("", "visual_v2"))

        assertTrue(prefs.values.isEmpty())
    }

    private class FakePrefs : SharedPreferences {
        val values = linkedMapOf<String, String>()
        var commitSucceeds = true

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key.orEmpty()] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = values.containsKey(key.orEmpty())
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val staged = linkedMapOf<String, String?>()
            private var cleared = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                staged[key.orEmpty()] = value
                return this
            }

            override fun putStringSet(key: String?, value: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this

            override fun remove(key: String?): SharedPreferences.Editor {
                staged[key.orEmpty()] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                cleared = true
                return this
            }

            override fun commit(): Boolean {
                if (!commitSucceeds) return false
                apply()
                return true
            }

            override fun apply() {
                if (cleared) values.clear()
                staged.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                staged.clear()
                cleared = false
            }
        }
    }
}
