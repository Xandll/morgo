package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.update.ApkTrust
import io.qzz.xandll.morgo.domain.update.GithubReleaseJson
import io.qzz.xandll.morgo.domain.update.ReleasePage
import io.qzz.xandll.morgo.domain.update.ReleaseVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseTest {
    @Test
    fun `newer tags compare numerically`() {
        assertTrue(ReleaseVersion.isNewer("v1.6.1", "1.6.0"))
        assertTrue(ReleaseVersion.isNewer("v1.10.0", "1.9.0"))
        assertFalse(ReleaseVersion.isNewer("v1.6.0", "1.6.0"))
        assertFalse(ReleaseVersion.isNewer("v1.5.0", "1.6.0"))
        assertFalse(ReleaseVersion.isNewer("nightly", "1.6.0"))
        assertFalse(ReleaseVersion.isNewer("v1.6.0", "1.6"))
        assertTrue(ReleaseVersion.isNewer("v1.6.1", "1.6"))
    }

    @Test
    fun `release json picks the apk and optional checksum`() {
        val release = GithubReleaseJson.parse(
            """
            {
              "tag_name": "v1.7.0",
              "body": "Licht und Updates",
              "assets": [
                {"name": "app-debug.apk", "browser_download_url": "https://example.test/debug.apk"},
                {"name": "morgo-release.apk", "browser_download_url": "https://github.com/Xandll/morgo/releases/download/v1.7.0/morgo-release.apk"},
                {"name": "morgo-release.apk.sha256", "browser_download_url": "https://github.com/Xandll/morgo/releases/download/v1.7.0/morgo-release.apk.sha256"}
              ]
            }
            """.trimIndent()
        )
        assertEquals("v1.7.0", release.tag)
        assertEquals("Licht und Updates", release.notes)
        assertEquals("morgo-release.apk", release.apkName)
        assertTrue(release.apkUrl.endsWith("morgo-release.apk"))
        assertEquals("https://github.com/Xandll/morgo/releases/download/v1.7.0/morgo-release.apk.sha256", release.sha256Url)
    }

    @Test
    fun `release page stays on github and rejects other addresses`() {
        assertEquals(
            "https://github.com/Xandll/morgo/releases/tag/v1.6.5",
            ReleasePage.url("Xandll", "morgo", "v1.6.5")
        )
        assertNull(ReleasePage.url("Xandll", "morgo", "https://evil.example/apk"))
        assertNull(ReleasePage.url("../Xandll", "morgo", "v1"))
        assertNull(ReleasePage.url("Xandll", "morgo/../other", "v1"))
    }

    @Test
    fun `checksum file and certificates`() {
        assertEquals("ab".repeat(32), ApkTrust.parseSha256("${"ab".repeat(32)}  morgo-release.apk"))
        assertNull(ApkTrust.parseSha256("zu kurz"))
        val bytes = "morgo".toByteArray()
        assertEquals(ApkTrust.sha256Hex(bytes), ApkTrust.sha256Hex(bytes))
        assertTrue(ApkTrust.sameCertificates(listOf(byteArrayOf(1, 2)), listOf(byteArrayOf(1, 2))))
        assertFalse(ApkTrust.sameCertificates(listOf(byteArrayOf(1)), listOf(byteArrayOf(2))))
        assertFalse(ApkTrust.sameCertificates(emptyList(), listOf(byteArrayOf(1))))
    }
}
