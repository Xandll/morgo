package io.qzz.xandll.morgo.domain.model

/**
 * Wohin die Zufallswiedergabe gehen soll.
 * Eine App kann [MusicShufflePlan.register] nutzen und damit den generischen
 * MediaBrowser ersetzen, ohne dass der Wecker die App kennen muss.
 */
enum class ShuffleStyle {
    /** MediaBrowser, setShuffleMode und Deep-Links. */
    GENERIC,

    /** Sonara 1.0.119 startet über den eigenen Ablauf, nicht über setShuffleMode. */
    SONARA
}

data class ShuffleTarget(
    val packageName: String,
    val serviceClasses: List<String> = emptyList(),
    val deepLinks: List<String> = emptyList(),
    val style: ShuffleStyle = ShuffleStyle.GENERIC
)

fun interface AppShuffleHook {
    /** null lässt den generischen Weg für dieses Paket stehen. */
    fun target(music: MusicAlarmSettings): ShuffleTarget?
}

object MusicShufflePlan {
    const val SPOTIFY_PACKAGE = "com.spotify.music"

    private val hooks = LinkedHashMap<String, AppShuffleHook>()

    fun register(packageName: String, hook: AppShuffleHook) {
        val key = packageName.trim()
        if (key.isNotEmpty()) hooks[key] = hook
    }

    fun unregister(packageName: String) {
        hooks.remove(packageName.trim())
    }

    fun target(music: MusicAlarmSettings): ShuffleTarget? {
        val packageName = when (music.kind) {
            MusicKind.SPOTIFY -> SPOTIFY_PACKAGE
            MusicKind.APP -> music.appPackage.trim()
            MusicKind.STREAM -> return null
        }
        if (packageName.isEmpty()) return null
        hooks[packageName]?.target(music)?.let { specific ->
            if (specific.packageName.isNotBlank()) return specific
        }
        if (packageName == SonaraShuffle.PACKAGE) return sonara()
        if (!music.shuffle) return null
        return if (packageName == SPOTIFY_PACKAGE) spotify(music) else ShuffleTarget(packageName)
    }

    fun serviceClasses(discovered: List<String>, preferred: List<String>): List<String> {
        return (discovered + preferred).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Spotify hat keinen dokumentierten Shuffle-Link. `:play` startet nur die Wiedergabe. */
    fun spotifyPlayLink(uri: String): String? {
        val raw = uri.trim()
        if (!raw.startsWith("spotify:")) return null
        if (raw.endsWith(":play")) return raw
        return "$raw:play"
    }

    private fun sonara(): ShuffleTarget {
        return ShuffleTarget(
            packageName = SonaraShuffle.PACKAGE,
            serviceClasses = listOf(SonaraShuffle.SERVICE),
            style = ShuffleStyle.SONARA
        )
    }

    private fun spotify(music: MusicAlarmSettings): ShuffleTarget {
        val link = SpotifyLink.parse(music.spotifyInput)?.let { spotifyPlayLink(it) }
        return ShuffleTarget(
            packageName = SPOTIFY_PACKAGE,
            deepLinks = listOfNotNull(link)
        )
    }
}
