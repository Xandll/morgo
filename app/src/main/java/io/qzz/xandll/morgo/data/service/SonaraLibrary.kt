package io.qzz.xandll.morgo.data.service

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import io.qzz.xandll.morgo.domain.model.SonaraPlaylist
import io.qzz.xandll.morgo.domain.model.SonaraShuffle
import androidx.media3.session.LibraryResult

/**
 * Liest die Playlists von Sonara über onGetChildren.
 * Schlägt das fehl, bleibt die Auswahl bei den Lieblingssongs.
 */
object SonaraLibrary {
    private const val TAG = "MorgoShuffle"
    private const val PAGE_SIZE = 200
    private const val TIMEOUT_MS = 8_000L

    private val main = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private var ticket = 0

    fun load(context: Context, onResult: (List<SonaraPlaylist>) -> Unit) {
        val app = context.applicationContext
        val mine = ++ticket
        main.post {
            runCatching { connect(app, mine, onResult) }.onFailure {
                fail(mine, onResult)
            }
        }
    }

    private fun connect(context: Context, mine: Int, onResult: (List<SonaraPlaylist>) -> Unit) {
        browser?.runCatching { release() }
        browser = null
        val token = SessionToken(context, ComponentName(SonaraShuffle.PACKAGE, SonaraShuffle.SERVICE))
        val future = MediaBrowser.Builder(context, token).buildAsync()
        val executor = ContextCompat.getMainExecutor(context)
        val timeout = Runnable { fail(mine, onResult) }
        main.postDelayed(timeout, TIMEOUT_MS)
        future.addListener({
            if (mine != ticket) return@addListener
            val connected = runCatching { future.get() }.getOrElse {
                main.removeCallbacks(timeout)
                fail(mine, onResult)
                return@addListener
            }
            browser = connected
            val params: MediaLibraryService.LibraryParams? = null
            val children = connected.getChildren(SonaraShuffle.PLAYLISTS_PARENT, 0, PAGE_SIZE, params)
            children.addListener({
                main.removeCallbacks(timeout)
                if (mine != ticket) return@addListener
                val result = runCatching { children.get() }.getOrNull()
                if (result == null || result.resultCode != LibraryResult.RESULT_SUCCESS) {
                    fail(mine, onResult)
                    return@addListener
                }
                val items = result.value.orEmpty().mapNotNull { item ->
                    SonaraShuffle.child(item.mediaId, item.mediaMetadata.title?.toString().orEmpty())
                }
                deliver(mine, items, onResult)
            }, executor)
        }, executor)
    }

    private fun fail(mine: Int, onResult: (List<SonaraPlaylist>) -> Unit) {
        if (mine != ticket) return
        Log.w(TAG, "Sonara-Playlists nicht gelesen")
        deliver(mine, emptyList(), onResult)
    }

    private fun deliver(mine: Int, items: List<SonaraPlaylist>, onResult: (List<SonaraPlaylist>) -> Unit) {
        if (mine != ticket) return
        main.removeCallbacksAndMessages(null)
        browser?.runCatching { release() }
        browser = null
        onResult(items)
    }
}
