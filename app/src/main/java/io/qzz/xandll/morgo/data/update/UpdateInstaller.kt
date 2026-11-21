package io.qzz.xandll.morgo.data.update

import android.content.Intent
import android.net.Uri
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.update.ReleasePage
import io.qzz.xandll.morgo.domain.update.UpdateOffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Öffnet nur die Release-Seite. Morgo lädt keine APK und installiert nichts selbst.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    private val settings: SettingsRepository
) {
    fun releaseIntent(offer: UpdateOffer): Intent? {
        val update = settings.loadSettings().update
        val url = ReleasePage.url(update.owner, update.repo, offer.tag) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
