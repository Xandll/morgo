package io.qzz.xandll.morgo.domain.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class GithubRelease(
    val tag: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val sha256Url: String?
)

data class UpdateOffer(
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sha256Url: String?,
    val dismissed: Boolean = false
)

object ReleasePage {
    private val name = Regex("[A-Za-z0-9_.-]{1,64}")
    private val tag = Regex("v?[0-9A-Za-z._-]{1,40}")

    /** Nur eine GitHub-Release-Seite. Kein Download, keine fremde Adresse. */
    fun url(owner: String, repo: String, releaseTag: String): String? {
        if (!name.matches(owner) || !name.matches(repo) || !tag.matches(releaseTag)) return null
        return "https://github.com/$owner/$repo/releases/tag/$releaseTag"
    }
}

object ReleaseVersion {
    /** Tag vX.Y.Z ist neuer als die installierte versionName. */
    fun isNewer(tag: String, current: String): Boolean {
        val remote = parts(tag) ?: return false
        val local = parts(current) ?: return false
        val size = maxOf(remote.size, local.size)
        for (index in 0 until size) {
            val left = remote.getOrElse(index) { 0 }
            val right = local.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun parts(raw: String): List<Int>? {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isEmpty()) return null
        val pieces = cleaned.split('.')
        if (pieces.any { piece -> piece.toIntOrNull() == null }) return null
        return pieces.map { it.toInt() }
    }
}

object GithubReleaseJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): GithubRelease {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("GitHub-Antwort ungültig")
        val tag = root.string("tag_name").orEmpty()
        if (tag.isBlank()) throw IllegalArgumentException("Release ohne Version")
        val notes = root.string("body").orEmpty()
        val files = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = asset.string("name") ?: return@mapNotNull null
            val url = asset.string("browser_download_url") ?: return@mapNotNull null
            if (!url.startsWith("https://")) return@mapNotNull null
            name to url
        }
        val apk = files.firstOrNull { it.first == "morgo-release.apk" }
            ?: files.firstOrNull { it.first.endsWith(".apk") && !it.first.contains("debug", ignoreCase = true) }
            ?: throw IllegalArgumentException("Im Release fehlt die APK")
        val sha = files.firstOrNull { it.first == "${apk.first}.sha256" }
            ?: files.firstOrNull { it.first.endsWith(".sha256") }
        return GithubRelease(
            tag = tag,
            notes = notes.take(8000),
            apkName = apk.first,
            apkUrl = apk.second,
            sha256Url = sha?.second
        )
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull
    }
}

object ApkTrust {
    fun parseSha256(text: String): String? {
        val match = Regex("[A-Fa-f0-9]{64}").find(text) ?: return null
        return match.value.lowercase()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sameCertificates(installed: List<ByteArray>, archive: List<ByteArray>): Boolean {
        if (installed.isEmpty() || archive.size != installed.size) return false
        return archive.all { candidate -> installed.any { it.contentEquals(candidate) } }
    }
}
