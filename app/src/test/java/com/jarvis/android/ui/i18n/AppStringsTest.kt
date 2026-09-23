package com.jarvis.android.ui.i18n

import com.jarvis.android.data.prefs.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppStringsTest {

    @Test
    fun testResolveEnStrings() {
        val strings = resolveAppStrings("en")
        assertEquals(EnAppStrings, strings)
        assertEquals("Chat", strings.navChat)
        assertEquals("ONLINE", strings.statusOnline)
        assertEquals("New chat", strings.newChat)
        assertEquals("Approve", strings.approve)
        assertEquals("Settings", strings.settingsTitle)
    }

    @Test
    fun testResolveEsStrings() {
        val strings = resolveAppStrings("es")
        assertEquals(EsAppStrings, strings)
        assertEquals("Chat", strings.navChat)
        assertEquals("EN LÍNEA", strings.statusOnline)
        assertEquals("Nuevo chat", strings.newChat)
        assertEquals("Aprobar", strings.approve)
        assertEquals("Ajustes", strings.settingsTitle)
    }

    @Test
    fun testResolveSystemDefault() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals(EnAppStrings, resolveAppStrings("system"))

            Locale.setDefault(Locale("es", "MX"))
            assertEquals(EsAppStrings, resolveAppStrings("system"))
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    @Test
    fun testSettingsDefaultHasSystemLanguage() {
        val settings = SettingsStore.Settings()
        assertEquals("system", settings.appLanguage)
    }

    @Test
    fun testKeyStringsParity() {
        val en = EnAppStrings
        val es = EsAppStrings

        assertTrue(en.navChat.isNotBlank())
        assertTrue(es.navChat.isNotBlank())

        assertTrue(en.navProjects.isNotBlank())
        assertTrue(es.navProjects.isNotBlank())

        assertTrue(en.navApprovals.isNotBlank())
        assertTrue(es.navApprovals.isNotBlank())

        assertTrue(en.navTasks.isNotBlank())
        assertTrue(es.navTasks.isNotBlank())

        assertTrue(en.navSettings.isNotBlank())
        assertTrue(es.navSettings.isNotBlank())

        assertEquals("5 ACTIVE", en.activeCount(5))
        assertEquals("5 ACTIVAS", es.activeCount(5))

        assertEquals("EXPIRES IN 30S", en.expiresIn(30))
        assertEquals("EXPIRA EN 30S", es.expiresIn(30))
    }
}
