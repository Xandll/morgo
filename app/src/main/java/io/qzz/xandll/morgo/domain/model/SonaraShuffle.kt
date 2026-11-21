package io.qzz.xandll.morgo.domain.model

/**
 * Sonara hält in der Session nur den aktuellen Titel.
 * setShuffleMode ändert daran nichts. SHUFFLE ist ein Toggle ohne lesbaren Zustand
 * und wird deshalb nie gesendet. SET_SHUFFLE gibt es erst, wenn die App den Befehl anbietet.
 */
object SonaraShuffle {
    const val PACKAGE = "app.sonara.mobile.native"
    const val SERVICE = "app.sonara.mobile.native_app.player.PlaybackService"
    const val SET_SHUFFLE = "SET_SHUFFLE"
    const val ENABLED = "enabled"

    enum class Step {
        SET_SHUFFLE_ON,
        PLAY
    }

    fun steps(shuffleEnabled: Boolean, customActions: Set<String>): List<Step> {
        val steps = ArrayList<Step>(2)
        if (shuffleEnabled && SET_SHUFFLE in customActions) steps += Step.SET_SHUFFLE_ON
        steps += Step.PLAY
        return steps
    }
}
