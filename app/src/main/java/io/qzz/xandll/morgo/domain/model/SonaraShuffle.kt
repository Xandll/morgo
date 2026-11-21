package io.qzz.xandll.morgo.domain.model

/**
 * Sonara 1.0.119.
 * PLAY_SHUFFLED mischt immer und spielt sofort. Ohne playlist_id sind es die Lieblingssongs.
 * Ein bekanntes Fehlerergebnis beendet den Versuch, Verbindungsfehler probieren den nächsten Weg.
 */
object SonaraShuffle {
    const val PACKAGE = "app.sonara.mobile.native"
    const val SERVICE = "app.sonara.mobile.native_app.player.PlaybackService"
    const val ACTION_PLAY_SHUFFLED = "app.sonara.ACTION_PLAY_SHUFFLED"
    const val PLAY_SHUFFLED = "PLAY_SHUFFLED"
    const val PLAYLIST_ID = "playlist_id"
    const val PLAYLISTS_PARENT = "tab:playlists"
    const val LIKED = "tab:liked"
    const val LIKED_SHORT = "liked"
    const val FAVORITES = "Lieblingssongs"
    const val PLAYLIST_PREFIX = "playlist:"

    enum class Transport {
        CONTROLLER,
        SERVICE,
        ACTIVITY
    }

    fun transports(foregroundStartAllowed: Boolean): List<Transport> {
        val steps = ArrayList<Transport>(3)
        steps += Transport.CONTROLLER
        if (foregroundStartAllowed) steps += Transport.SERVICE
        steps += Transport.ACTIVITY
        return steps
    }

    /** Der Dienst hat den Start angenommen. Die Activity würde dieselbe Wiedergabe noch einmal anstoßen. */
    fun activityAfterService(serviceAccepted: Boolean): Boolean = !serviceAccepted

    data class Timing(val connectMs: Long, val commandMs: Long)

    /** Kaltstart braucht ein paar Sekunden, darf die Klingelton-Frist aber nicht überziehen. */
    fun timing(limitSeconds: Int): Timing {
        val total = limitSeconds.coerceAtLeast(5) * 1000L
        val connect = minOf(6_000L, total / 2)
        val command = minOf(8_000L, (total - connect).coerceAtLeast(1_000L))
        return Timing(connect, command)
    }

    fun isFavorites(id: String): Boolean {
        val raw = id.trim()
        return raw.isEmpty() || raw == LIKED || raw == LIKED_SHORT
    }

    /** null lässt das Extra weg und meint die Lieblingssongs. */
    fun playlistArgument(id: String): String? {
        val raw = id.trim()
        if (isFavorites(raw)) return null
        return raw
    }

    fun child(mediaId: String, title: String): SonaraPlaylist? {
        val id = mediaId.trim()
        if (!id.startsWith(PLAYLIST_PREFIX) || id.contains(":song:")) return null
        val name = title.trim().ifEmpty { id.removePrefix(PLAYLIST_PREFIX) }
        return SonaraPlaylist(id, name)
    }

    enum class Code {
        SUCCESS,
        BAD_VALUE,
        AUTHENTICATION_EXPIRED,
        INVALID_STATE,
        IO,
        OTHER
    }

    enum class Verdict {
        PLAYING,
        NEXT_TRANSPORT,
        RINGTONE
    }

    data class Reading(val verdict: Verdict, val message: String?)

    fun read(code: Code, remoteMessage: String?, playing: Boolean): Reading {
        if (code == Code.SUCCESS) return Reading(Verdict.PLAYING, null)
        val message = remoteMessage?.trim().orEmpty().lineSequence().firstOrNull().orEmpty()
            .take(160)
            .ifEmpty { defaultMessage(code) }
        return when (code) {
            Code.BAD_VALUE, Code.AUTHENTICATION_EXPIRED, Code.INVALID_STATE, Code.IO ->
                Reading(Verdict.RINGTONE, message)
            else -> if (playing) Reading(Verdict.PLAYING, null) else Reading(Verdict.NEXT_TRANSPORT, null)
        }
    }

    fun defaultMessage(code: Code): String {
        return when (code) {
            Code.BAD_VALUE -> "Unbekannte Playlist"
            Code.AUTHENTICATION_EXPIRED -> "Sonara ist nicht eingeloggt"
            Code.INVALID_STATE -> "Keine Songs"
            Code.IO -> "Kein Netz zu Sonara"
            else -> "Sonara hat nicht gespielt"
        }
    }
}

data class SonaraPlaylist(
    val id: String,
    val title: String
)

object SonaraCatalog {
    fun choices(
        browsed: List<SonaraPlaylist>,
        selectedId: String = "",
        selectedTitle: String = ""
    ): List<SonaraPlaylist> {
        val favorites = SonaraPlaylist(SonaraShuffle.LIKED, SonaraShuffle.FAVORITES)
        val rest = LinkedHashMap<String, SonaraPlaylist>()
        browsed.forEach { item ->
            val child = SonaraShuffle.child(item.id, item.title) ?: return@forEach
            if (rest.containsKey(child.id)) return@forEach
            rest[child.id] = child
        }
        val selected = selectedId.trim()
        if (selected.isNotEmpty() && !SonaraShuffle.isFavorites(selected) && selected !in rest) {
            rest[selected] = SonaraPlaylist(selected, selectedTitle.trim().ifEmpty { selected })
        }
        return listOf(favorites) + rest.values
    }
}
