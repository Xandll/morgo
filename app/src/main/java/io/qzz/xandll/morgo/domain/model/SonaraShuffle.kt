package io.qzz.xandll.morgo.domain.model

/**
 * Sonara-Befehle sind vorläufig und gelten erst, wenn die App sie anbietet.
 * SHUFFLE bleibt ein Toggle ohne lesbaren Zustand und wird nie gesendet.
 * Ohne playlist_id meint PLAY_SHUFFLED die Lieblingssongs.
 */
object SonaraShuffle {
    const val PACKAGE = "app.sonara.mobile.native"
    const val SERVICE = "app.sonara.mobile.native_app.player.PlaybackService"
    const val PLAY_SHUFFLED = "PLAY_SHUFFLED"
    const val PLAYLIST_ID = "playlist_id"
    const val FAVORITES = "Lieblingssongs"

    enum class Step {
        PLAY_SHUFFLED,
        PLAY
    }

    fun steps(shuffleEnabled: Boolean, customActions: Set<String>): List<Step> {
        return if (shuffleEnabled && PLAY_SHUFFLED in customActions) {
            listOf(Step.PLAY_SHUFFLED)
        } else {
            listOf(Step.PLAY)
        }
    }

    /** Leer heißt Lieblingssongs und wandert nicht ins Bundle. */
    fun playlistArgument(id: String): String? = id.trim().ifEmpty { null }
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
        val favorites = SonaraPlaylist("", SonaraShuffle.FAVORITES)
        val rest = LinkedHashMap<String, SonaraPlaylist>()
        browsed.forEach { item ->
            val id = item.id.trim()
            if (id.isEmpty() || rest.containsKey(id)) return@forEach
            rest[id] = SonaraPlaylist(id, item.title.trim().ifEmpty { id })
        }
        val selected = selectedId.trim()
        if (selected.isNotEmpty() && selected !in rest) {
            rest[selected] = SonaraPlaylist(selected, selectedTitle.trim().ifEmpty { selected })
        }
        return listOf(favorites) + rest.values
    }
}
