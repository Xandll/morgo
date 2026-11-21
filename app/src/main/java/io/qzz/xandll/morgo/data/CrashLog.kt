package io.qzz.xandll.morgo.data

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * Speichert den letzten unbehandelten Absturz in den App-Einstellungen.
 * Der nächste Start kann den Text kopieren oder teilen. Kein extra Recht.
 */
object CrashLog {
    private const val PREFS = "morgo_crash"
    private const val KEY = "trace"
    private const val LIMIT = 16_000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun peek(context: Context): String? {
        return runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .commit()
        }
    }

    internal fun record(context: Context, error: Throwable) {
        val text = buildString {
            append(error.javaClass.name)
            val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            if (message.isNotEmpty()) {
                append(": ")
                append(message.take(200))
            }
            append("\n\n")
            append(Log.getStackTraceString(error))
        }.take(LIMIT)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, text)
            .commit()
    }
}
