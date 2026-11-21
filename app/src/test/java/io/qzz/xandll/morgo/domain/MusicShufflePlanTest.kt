package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.AppShuffleHook
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.MusicShufflePlan
import io.qzz.xandll.morgo.domain.model.ShuffleStyle
import io.qzz.xandll.morgo.domain.model.ShuffleTarget
import io.qzz.xandll.morgo.domain.model.SonaraCatalog
import io.qzz.xandll.morgo.domain.model.SonaraPlaylist
import io.qzz.xandll.morgo.domain.model.SonaraShuffle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicShufflePlanTest {

    @Test
    fun shuffleStaysOffForStreamsAndWhenTheSwitchIsOff() {
        assertTrue(MusicAlarmSettings().shuffle)
        assertNull(MusicShufflePlan.target(MusicAlarmSettings(kind = MusicKind.STREAM)))
        assertNull(
            MusicShufflePlan.target(
                MusicAlarmSettings(kind = MusicKind.SPOTIFY, shuffle = false, spotifyInput = "spotify:playlist:1")
            )
        )
        assertNull(MusicShufflePlan.target(MusicAlarmSettings(kind = MusicKind.APP, appPackage = "  ")))
    }

    @Test
    fun sonaraStartsShuffledAndFallsBackInOrder() {
        val on = MusicShufflePlan.target(
            MusicAlarmSettings(kind = MusicKind.APP, appPackage = SonaraShuffle.PACKAGE, shuffle = true)
        )
        val off = MusicShufflePlan.target(
            MusicAlarmSettings(kind = MusicKind.APP, appPackage = SonaraShuffle.PACKAGE, shuffle = false)
        )
        assertEquals(ShuffleStyle.SONARA, on?.style)
        assertEquals(SonaraShuffle.SERVICE, on?.serviceClasses?.single())
        assertEquals(on, off)
        assertEquals(
            listOf(SonaraShuffle.Transport.CONTROLLER, SonaraShuffle.Transport.SERVICE, SonaraShuffle.Transport.ACTIVITY),
            SonaraShuffle.transports(foregroundStartAllowed = true)
        )
        assertEquals(
            listOf(SonaraShuffle.Transport.CONTROLLER, SonaraShuffle.Transport.ACTIVITY),
            SonaraShuffle.transports(foregroundStartAllowed = false)
        )
        assertEquals(false, SonaraShuffle.activityAfterService(serviceAccepted = true))
        assertEquals(true, SonaraShuffle.activityAfterService(serviceAccepted = false))
        assertEquals(SonaraShuffle.Timing(6_000L, 8_000L), SonaraShuffle.timing(20))
        assertEquals(SonaraShuffle.Timing(2_500L, 2_500L), SonaraShuffle.timing(5))
        assertNull(SonaraShuffle.playlistArgument("  "))
        assertNull(SonaraShuffle.playlistArgument(SonaraShuffle.LIKED))
        assertNull(SonaraShuffle.playlistArgument(SonaraShuffle.LIKED_SHORT))
        assertEquals("playlist:morgen", SonaraShuffle.playlistArgument(" playlist:morgen "))
        assertEquals("playlist:morgen:song:1", SonaraShuffle.playlistArgument("playlist:morgen:song:1"))
        assertNull(SonaraShuffle.child("tab:playlists", "Playlists"))
        assertNull(SonaraShuffle.child("playlist:morgen:song:1", "Titel"))
        assertEquals(SonaraPlaylist("playlist:pin", "Angepinnt"), SonaraShuffle.child("playlist:pin", "Angepinnt"))
        assertEquals(
            listOf(SonaraPlaylist(SonaraShuffle.LIKED, SonaraShuffle.FAVORITES)),
            SonaraCatalog.choices(emptyList())
        )
        assertEquals(
            listOf(
                SonaraPlaylist(SonaraShuffle.LIKED, SonaraShuffle.FAVORITES),
                SonaraPlaylist("playlist:pin", "Angepinnt"),
                SonaraPlaylist("playlist:spaeter", "Später")
            ),
            SonaraCatalog.choices(
                listOf(
                    SonaraPlaylist("playlist:pin", "Angepinnt"),
                    SonaraPlaylist("playlist:pin:song:1", "Titel"),
                    SonaraPlaylist("tab:playlists", "Playlists"),
                    SonaraPlaylist("playlist:spaeter", "Später"),
                    SonaraPlaylist("playlist:pin", "doppelt")
                )
            )
        )
        val saved = SonaraCatalog.choices(emptyList(), "playlist:weg", "Morgen")
        assertEquals(SonaraPlaylist("playlist:weg", "Morgen"), saved.last())
        val unknown = SonaraShuffle.read(SonaraShuffle.Code.BAD_VALUE, "Unbekannte Playlist", playing = true)
        assertEquals(SonaraShuffle.Verdict.RINGTONE, unknown.verdict)
        assertEquals("Unbekannte Playlist", unknown.message)
        assertEquals(
            "Sonara ist nicht eingeloggt",
            SonaraShuffle.read(SonaraShuffle.Code.AUTHENTICATION_EXPIRED, "  ", playing = false).message
        )
        assertEquals(
            SonaraShuffle.Verdict.PLAYING,
            SonaraShuffle.read(SonaraShuffle.Code.SUCCESS, null, playing = false).verdict
        )
        assertEquals(
            SonaraShuffle.Verdict.NEXT_TRANSPORT,
            SonaraShuffle.read(SonaraShuffle.Code.OTHER, null, playing = false).verdict
        )
        assertEquals(
            SonaraShuffle.Verdict.PLAYING,
            SonaraShuffle.read(SonaraShuffle.Code.OTHER, null, playing = true).verdict
        )
        assertEquals(
            "Sonara · Morgen",
            MusicAlarmSettings(
                kind = MusicKind.APP,
                appPackage = SonaraShuffle.PACKAGE,
                appLabel = "Sonara",
                sonaraPlaylistId = "playlist:morgen",
                sonaraPlaylistTitle = "Morgen"
            ).summary()
        )
    }

    @Test
    fun spotifyGetsAPlayLinkAndAnotherAppStaysGeneric() {
        val spotify = MusicShufflePlan.target(
            MusicAlarmSettings(
                kind = MusicKind.SPOTIFY,
                spotifyInput = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
            )
        )
        assertEquals(MusicShufflePlan.SPOTIFY_PACKAGE, spotify?.packageName)
        assertEquals(listOf("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M:play"), spotify?.deepLinks)
        assertEquals("spotify:album:1X:play", MusicShufflePlan.spotifyPlayLink("spotify:album:1X"))
        assertEquals("spotify:playlist:1:play", MusicShufflePlan.spotifyPlayLink("spotify:playlist:1:play"))
        assertNull(MusicShufflePlan.spotifyPlayLink("https://example.com/playlist/1"))

        val app = MusicShufflePlan.target(
            MusicAlarmSettings(kind = MusicKind.APP, appPackage = "com.example.player", appLabel = "Player")
        )
        assertEquals(ShuffleTarget("com.example.player"), app)
    }

    @Test
    fun anAppCanReplaceTheGenericPath() {
        val hook = AppShuffleHook {
            ShuffleTarget(
                packageName = "com.example.player",
                serviceClasses = listOf("com.example.player.MusicService"),
                deepLinks = listOf("example:shuffle")
            )
        }
        MusicShufflePlan.register("com.example.player", hook)
        try {
            val target = MusicShufflePlan.target(
                MusicAlarmSettings(kind = MusicKind.APP, appPackage = "com.example.player")
            )
            assertEquals("com.example.player.MusicService", target?.serviceClasses?.single())
            assertEquals(listOf("example:shuffle"), target?.deepLinks)
        } finally {
            MusicShufflePlan.unregister("com.example.player")
        }
        val after = MusicShufflePlan.target(
            MusicAlarmSettings(kind = MusicKind.APP, appPackage = "com.example.player")
        )
        assertEquals(ShuffleTarget("com.example.player"), after)
        MusicShufflePlan.register("com.example.player", AppShuffleHook { null })
        try {
            val fallback = MusicShufflePlan.target(
                MusicAlarmSettings(kind = MusicKind.APP, appPackage = "com.example.player")
            )
            assertEquals(ShuffleTarget("com.example.player"), fallback)
        } finally {
            MusicShufflePlan.unregister("com.example.player")
        }
        assertEquals(
            listOf("Discovered", "Preferred"),
            MusicShufflePlan.serviceClasses(listOf("Discovered", ""), listOf("Preferred", "Discovered"))
        )
    }
}
