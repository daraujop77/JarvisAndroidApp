package com.jarvis.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun acceptsOnlyPinnedGithubReleaseAssets() {
        assertTrue(
            UpdatePolicy.safeGithubReleaseAsset(
                "https://github.com/daraujop77/JarvisAndroidApp/releases/latest/download/JARVIS-release.json",
            ),
        )
        assertTrue(
            UpdatePolicy.safeGithubReleaseAsset(
                "https://github.com/daraujop77/JarvisAndroidApp/releases/latest/download/JARVIS-release.apk",
            ),
        )
    }

    @Test
    fun rejectsOtherHostsTagsAndDecoratedUrls() {
        assertFalse(UpdatePolicy.safeGithubReleaseAsset("https://example.com/JARVIS-release.apk"))
        assertFalse(
            UpdatePolicy.safeGithubReleaseAsset(
                "https://github.com/daraujop77/JarvisAndroidApp/releases/download/android-v41/JARVIS-release.apk",
            ),
        )
        assertFalse(
            UpdatePolicy.safeGithubReleaseAsset(
                "https://github.com/daraujop77/JarvisAndroidApp/releases/latest/download/JARVIS-release.apk?token=x",
            ),
        )
        assertFalse(
            UpdatePolicy.safeGithubReleaseAsset(
                "https://github.com/other/JarvisAndroidApp/releases/latest/download/JARVIS-release.apk",
            ),
        )
    }
}
