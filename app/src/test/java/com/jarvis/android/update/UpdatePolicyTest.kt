package com.jarvis.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun acceptsOnlyUpdateApiSubpaths() {
        assertTrue(UpdatePolicy.safeDownloadPath("/api/app/update/apk"))
        assertTrue(UpdatePolicy.safeDownloadPath("/api/app/update/builds/2.apk"))
    }

    @Test
    fun rejectsAbsoluteTraversalAndDecoratedPaths() {
        assertFalse(UpdatePolicy.safeDownloadPath("https://example.com/app.apk"))
        assertFalse(UpdatePolicy.safeDownloadPath("//100.94.103.82/app.apk"))
        assertFalse(UpdatePolicy.safeDownloadPath("/api/app/update/../secret"))
        assertFalse(UpdatePolicy.safeDownloadPath("/api/app/update/apk?token=x"))
        assertFalse(UpdatePolicy.safeDownloadPath("/api/app/update/apk#fragment"))
    }
}
