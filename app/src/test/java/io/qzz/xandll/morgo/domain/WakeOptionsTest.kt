package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.ExtraAlarm
import io.qzz.xandll.morgo.domain.model.ExtraAlarmCodec
import io.qzz.xandll.morgo.domain.model.ExtraAlarmPlan
import io.qzz.xandll.morgo.domain.model.FollowUpPlan
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonStatus
import io.qzz.xandll.morgo.domain.model.SpotifyLink
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.domain.model.WakeSoundMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import io.qzz.xandll.morgo.domain.model.StreamPhase
import io.qzz.xandll.morgo.domain.model.StreamSample
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WakeOptionsTest {

    private val day = LocalDate.of(2026, 11, 23)
    private val wake = LocalDateTime.of(day, LocalTime.of(5, 30))

    @Test
    fun followUpIsFiveMinutesAfterTheMainAlarm() {
        assertEquals(LocalTime.of(5, 35), FollowUpPlan.at(wake, 5).toLocalTime())
        assertEquals(LocalTime.of(6, 0), FollowUpPlan.at(wake, 30).toLocalTime())
    }

    @Test
    fun followUpFollowsPlanBAndSkipsAQuietMorning() {
        val ringtone = AlarmSettings(followUpEnabled = true, followUpMinutes = 5, wakeSound = WakeSound.RINGTONE)
        assertTrue(FollowUpPlan.shouldSchedule(rings = true, ringtone))
        assertFalse(FollowUpPlan.shouldSchedule(rings = false, ringtone))
        assertFalse(FollowUpPlan.shouldSchedule(rings = true, ringtone.copy(followUpEnabled = false)))
        assertFalse(FollowUpPlan.shouldSchedule(rings = true, ringtone.copy(wakeSound = WakeSound.MUSIC)))
    }

    @Test
    fun awakeConfirmationCancelsFollowUpUnlessToldOtherwise() {
        val normal = AlarmSettings(followUpEvenIfAwake = false)
        val stubborn = AlarmSettings(followUpEvenIfAwake = true)
        assertTrue(FollowUpPlan.cancelWhenConfirmedAwake(normal))
        assertFalse(FollowUpPlan.cancelWhenConfirmedAwake(stubborn))
    }

    @Test
    fun extraAlarmMatchesTheSubjectAndIgnoresCancelledLessons() {
        val lessons = listOf(
            lesson("Turnen", LessonStatus.REGULAR),
            lesson("Mathe", LessonStatus.CANCELLED),
            lesson("Deutsch", LessonStatus.SUBSTITUTED)
        )
        val subjects = ExtraAlarmPlan.activeSubjects(lessons, day)
        assertEquals(listOf("Turnen", "Deutsch"), subjects)
        assertTrue(ExtraAlarmPlan.matches(" turnen ", "Turnen"))
        assertFalse(ExtraAlarmPlan.matches("Sport", "Turnen"))
        assertFalse(ExtraAlarmPlan.matches("Mathe", "Mathe") && subjects.any { ExtraAlarmPlan.matches(it, "Mathe") })
    }

    @Test
    fun extraAlarmTimeIsMinutesAfterGettingUp() {
        val extras = listOf(
            ExtraAlarm("1", "Turnzeug einpacken", "Turnen", 15, true),
            ExtraAlarm("2", "Geodreieck", "Mathe", 20, false)
        )
        val due = ExtraAlarmPlan.due(wake, listOf("Turnen"), extras)
        assertEquals(1, due.size)
        assertEquals(LocalTime.of(5, 45), due.single().at.toLocalTime())
        assertEquals(listOf("05:45 Turnzeug einpacken"), ExtraAlarmPlan.lines(LocalTime.of(5, 30), listOf("Turnen"), extras))
    }

    @Test
    fun noExtraAlarmWhenTheSubjectIsNotOnThatDay() {
        val extras = listOf(ExtraAlarm("1", "Turnzeug", "Turnen", 15, true))
        assertTrue(ExtraAlarmPlan.due(wake, listOf("Deutsch"), extras).isEmpty())
        assertTrue(ExtraAlarmPlan.lines(LocalTime.of(5, 30), emptyList(), extras).isEmpty())
    }

    @Test
    fun extraAlarmCodecRoundTrip() {
        val original = listOf(ExtraAlarm("a", "Turnzeug einpacken", "Turnen", 15, true))
        assertEquals(original, ExtraAlarmCodec.decode(ExtraAlarmCodec.encode(original)))
    }

    @Test
    fun spotifyLinksBecomeUris() {
        assertEquals(
            "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
            SpotifyLink.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
        )
        assertEquals(
            "spotify:album:1X",
            SpotifyLink.parse("spotify:album:1X")
        )
        assertEquals(
            "spotify:track:9Z",
            SpotifyLink.parse("https://open.spotify.com/intl-de/track/9Z")
        )
        assertNull(SpotifyLink.parse("https://example.com/playlist/1"))
        assertNull(SpotifyLink.parse(""))
    }

    @Test
    fun volumeRisesAndMusicFallsBack() {
        assertEquals(0f, WakeSoundMath.volumeAt(0, 30_000, 0.7f), 0.001f)
        assertEquals(0.35f, WakeSoundMath.volumeAt(15_000, 30_000, 0.7f), 0.001f)
        assertEquals(0.7f, WakeSoundMath.volumeAt(40_000, 30_000, 0.7f), 0.001f)
        assertFalse(WakeSoundMath.fallbackNow(true, true, 10, playing = true, limitSeconds = 20))
        assertFalse(WakeSoundMath.fallbackNow(true, true, 10, playing = false, limitSeconds = 20))
        assertTrue(WakeSoundMath.fallbackNow(true, true, 20, playing = false, limitSeconds = 20))
        assertFalse(WakeSoundMath.fallbackNow(true, false, 3, playing = false, limitSeconds = 20))
        assertTrue(WakeSoundMath.fallbackNow(true, false, 20, playing = false, limitSeconds = 20))
    }

    @Test
    fun silentRampStillCountsAsPlaying() {
        val sample = StreamSample(
            elapsedSeconds = 20,
            limitSeconds = 20,
            online = true,
            phase = StreamPhase.READY,
            playWhenReady = true
        )
        assertTrue(WakeSoundMath.countsAsPlaying(StreamPhase.READY, playWhenReady = true))
        assertNull(WakeSoundMath.fallbackReason(sample))
        assertEquals(0.02f, WakeSoundMath.audibleVolume(0f), 0.001f)
    }

    @Test
    fun missingNetworkWaitsForTheFallbackWindow() {
        val early = StreamSample(3, 20, online = false, StreamPhase.BUFFERING, playWhenReady = true)
        assertNull(WakeSoundMath.fallbackReason(early))
        val late = early.copy(elapsedSeconds = 20)
        assertEquals("Kein Netz nach 20s, Klingelton", WakeSoundMath.fallbackReason(late))
    }

    @Test
    fun playerErrorIsNamedAfterTheFallbackWindow() {
        val early = StreamSample(
            elapsedSeconds = 2,
            limitSeconds = 20,
            online = true,
            phase = StreamPhase.IDLE,
            playWhenReady = true,
            errorCode = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            errorDetail = "timeout"
        )
        assertNull(WakeSoundMath.fallbackReason(early))
        assertTrue(WakeSoundMath.shouldRetry(2, 20, failed = true, lastRetrySecond = -3))
        assertFalse(WakeSoundMath.shouldRetry(2, 20, failed = true, lastRetrySecond = 2))
        val late = early.copy(elapsedSeconds = 20)
        assertEquals(
            "Musik fehlgeschlagen: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED (timeout)",
            WakeSoundMath.fallbackReason(late)
        )
    }
}

private fun lesson(subject: String, status: LessonStatus) = Lesson(
    date = LocalDate.of(2026, 11, 23),
    startTime = LocalTime.of(8, 0),
    endTime = LocalTime.of(8, 50),
    subject = subject,
    teacher = "",
    room = "",
    status = status
)
