package io.qzz.xandll.morgo.data.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.MediaStore
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.SpotifyLink

object WakeMusic {
    /**
     * Öffnet Spotify oder eine andere App nur aus einer sichtbaren Activity.
     * Kein Start aus dem Hintergrund und keine Medientasten.
     */
    fun open(context: Context, music: MusicAlarmSettings): Boolean {
        val activity = findActivity(context) ?: return false
        return when (music.kind) {
            MusicKind.STREAM -> music.streamUrl.isNotBlank()
            MusicKind.SPOTIFY -> openSpotify(activity, music.spotifyInput)
            MusicKind.APP -> openApp(activity, music)
        }
    }

    fun pickIntent(searchQuery: String): Intent {
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, searchQuery)
        }
        return Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            putExtra(Intent.EXTRA_INTENT, play)
        }
    }

    private fun openSpotify(activity: Activity, input: String): Boolean {
        val uri = SpotifyLink.parse(input) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).apply {
            setPackage("com.spotify.music")
        }
        return launch(activity, intent) || launch(activity, intent.apply { setPackage(null) })
    }

    private fun openApp(activity: Activity, music: MusicAlarmSettings): Boolean {
        if (music.appPackage.isBlank() && music.searchQuery.isBlank()) return false
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, music.searchQuery)
            if (music.appPackage.isNotBlank()) setPackage(music.appPackage)
        }
        return launch(activity, intent) || launch(activity, intent.apply { setPackage(null) })
    }

    private fun launch(activity: Activity, intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
