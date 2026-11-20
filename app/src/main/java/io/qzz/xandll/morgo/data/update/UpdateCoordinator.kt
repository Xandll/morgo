package io.qzz.xandll.morgo.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.update.ReleaseVersion
import io.qzz.xandll.morgo.domain.update.UpdateOffer
import io.qzz.xandll.morgo.presentation.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val offer: UpdateOffer) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

@Singleton
class UpdateCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: GithubReleaseClient,
    private val settings: SettingsRepository
) {
    suspend fun check(manual: Boolean): UpdateCheckResult {
        val update = settings.loadSettings().update
        if (!update.enabled && !manual) return UpdateCheckResult.UpToDate
        val release = client.latest(update.owner, update.repo).getOrElse { error ->
            return UpdateCheckResult.Failed(error.message ?: "GitHub nicht erreichbar")
        }
        if (!ReleaseVersion.isNewer(release.tag, BuildConfig.VERSION_NAME)) {
            if (manual) settings.saveUpdateOffer(null)
            return UpdateCheckResult.UpToDate
        }
        val previous = settings.loadUpdateOffer()
        val dismissed = previous?.tag == release.tag && previous.dismissed && !manual
        val offer = UpdateOffer(
            tag = release.tag,
            notes = release.notes,
            apkUrl = release.apkUrl,
            sha256Url = release.sha256Url,
            dismissed = dismissed
        )
        settings.saveUpdateOffer(offer)
        if (!dismissed && settings.lastNotifiedUpdateTag() != release.tag) {
            notify(offer)
            settings.rememberNotifiedUpdate(release.tag)
        }
        return UpdateCheckResult.Available(offer)
    }

    private fun notify(offer: UpdateOffer) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val line = offer.notes.lineSequence().firstOrNull { it.isNotBlank() } ?: "Neues Release auf GitHub"
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Morgo ${offer.tag.removePrefix("v")} ist da")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(offer.notes.ifBlank { line }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(44, notification) }
    }
}
