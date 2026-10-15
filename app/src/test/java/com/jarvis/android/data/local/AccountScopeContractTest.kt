package com.jarvis.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Identical accounts share a namespace; distinct owners NEVER do. */
class AccountScopeContractTest {
    @Test fun distinctAccountsCannotShareRoomOrAttachments() {
        val a = JarvisDatabase.accountStorageKey("https://gateway.test\u001fprincipal-a")
        val b = JarvisDatabase.accountStorageKey("https://gateway.test\u001fprincipal-b")
        assertNotEquals(a, b)
        assertEquals(a, JarvisDatabase.accountStorageKey("https://gateway.test\u001fprincipal-a"))
        assertEquals(32, a.length)
        assertFalse(a.contains("principal-a"))
    }

    @Test fun guestAndLegacyStateNeverAssignedToLoggedInAccount() {
        val guest = JarvisDatabase.accountStorageKey("")
        assertEquals("unauthenticated", guest)
        assertNotEquals(guest, JarvisDatabase.accountStorageKey("principal-a"))
    }
}
