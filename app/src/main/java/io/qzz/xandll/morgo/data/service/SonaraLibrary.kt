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

/**
 * Liest die Playlists von Sonara über onGetChildren.
 * Schlägt das fehl, bleibt die Auswahl bei den Lieblingssongs.
 */
object SonaraLibrary {
    private const val TAG = "MorgoShuffle"
    private const val PAGE_SIZE = 200

    private var browser: MediaBrowser? = null
    private var ticket = 0

    fun load(context: Context, onResult: (List<SonaraPlaylist>) -> Unit) {
        val app = context.applicationContext
        val mine = ++ticket
        Handler(Looper.getMainLooper()).post {
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
        future.addListener({
            if (mine != ticket) return@addListener
            val connected = runCatching { future.get() }.getOrElse {
                fail(mine, onResult)
                return@addListener
            }
            browser = connected
            val params: MediaLibraryService.LibraryParams? = null
            val rootFuture = connected.getLibraryRoot(params)
            rootFuture.addListener({
                if (mine != ticket) return@addListener
                val root = runCatching { rootFuture.get() }.getOrNull()
                val parent = root?.value?.mediaId
                if (root == null || root.resultCode != androidx.media3.session.LibraryResult.RESULT_SUCCESS || parent == null) {
                    fail(mine, onResult)
                    return@addListener
                }
                val children = connected.getChildren(parent, 0, PAGE_SIZE, params)
                children.addListener({
                    if (mine != ticket) return@addListener
                    val result = runCatching { children.get() }.getOrNull()
                    if (result == null || result.resultCode != androidx.media3.session.LibraryResult.RESULT_SUCCESS) {
                        fail(mine, onResult)
                        return@addListener
                    }
                    val items = result.value.orEmpty().mapNotNull { item ->
                        val id = item.mediaId.trim()
                        if (id.isEmpty()) {
                            null
                        } else {
                            val title = item.mediaMetadata.title?.toString()?.trim().orEmpty()
                            SonaraPlaylist(id, title.ifEmpty { id })
                        }
                    }
                    deliver(mine, items, onResult)
                }, executor)
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
        browser?.runCatching { release() }
        browser = null
        onResult(items)
    }
}
