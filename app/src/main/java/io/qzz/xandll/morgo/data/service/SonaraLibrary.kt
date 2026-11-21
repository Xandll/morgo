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
    private var pendingTimeout: Runnable? = null

    fun load(context: Context, onResult: (List<SonaraPlaylist>) -> Unit) {
        val app = context.applicationContext
        if (!installed(app)) {
            onResult(emptyList())
            return
        }
        val mine = ++ticket
        main.post {
            runCatching { connect(app, mine, onResult) }.onFailure {
                fail(mine, onResult)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SonaraShuffle.PACKAGE, 0)
        }.isSuccess
    }

    private fun connect(context: Context, mine: Int, onResult: (List<SonaraPlaylist>) -> Unit) {
        browser?.runCatching { release() }
        browser = null
        val token = SessionToken(context, ComponentName(SonaraShuffle.PACKAGE, SonaraShuffle.SERVICE))
        val future = MediaBrowser.Builder(context, token).buildAsync()
        val executor = ContextCompat.getMainExecutor(context)
        val timeout = Runnable { fail(mine, onResult) }
        arm(timeout)
        future.addListener({
            if (mine != ticket) return@addListener
            runCatching {
                val connected = future.get()
                browser = connected
                val params: MediaLibraryService.LibraryParams? = null
                val children = connected.getChildren(SonaraShuffle.PLAYLISTS_PARENT, 0, PAGE_SIZE, params)
                children.addListener({
                    disarm()
                    if (mine != ticket) return@addListener
                    runCatching {
                        val result = children.get()
                        if (result.resultCode != LibraryResult.RESULT_SUCCESS) {
                            fail(mine, onResult)
                            return@runCatching
                        }
                        val items = result.value.orEmpty().mapNotNull { item ->
                            SonaraShuffle.child(item.mediaId, item.mediaMetadata.title?.toString().orEmpty())
                        }
                        deliver(mine, items, onResult)
                    }.onFailure { fail(mine, onResult) }
                }, executor)
            }.onFailure {
                disarm()
                fail(mine, onResult)
            }
        }, executor)
    }

    private fun arm(timeout: Runnable) {
        pendingTimeout?.let { main.removeCallbacks(it) }
        pendingTimeout = timeout
        main.postDelayed(timeout, TIMEOUT_MS)
    }

    private fun disarm() {
        pendingTimeout?.let { main.removeCallbacks(it) }
        pendingTimeout = null
    }

    private fun fail(mine: Int, onResult: (List<SonaraPlaylist>) -> Unit) {
        if (mine != ticket) return
        Log.w(TAG, "Sonara-Playlists nicht gelesen")
        deliver(mine, emptyList(), onResult)
    }

    private fun deliver(mine: Int, items: List<SonaraPlaylist>, onResult: (List<SonaraPlaylist>) -> Unit) {
        if (mine != ticket) return
        disarm()
        browser?.runCatching { release() }
        browser = null
        runCatching { onResult(items) }
    }
}
