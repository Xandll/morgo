package io.qzz.xandll.morgo.data.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.update.ApkTrust
import io.qzz.xandll.morgo.domain.update.UpdateOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val client: GithubReleaseClient
) {
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun enqueue(offer: UpdateOffer) {
        val manager = context.getSystemService(android.app.DownloadManager::class.java)
        val destination = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "morgo-update.apk"
        )
        if (destination.exists()) destination.delete()
        val request = android.app.DownloadManager.Request(Uri.parse(offer.apkUrl))
            .setTitle("Morgo ${offer.tag}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "morgo-update.apk")
        val id = manager.enqueue(request)
        settings.rememberUpdateDownload(id)
        _status.value = "Download läuft. Danach prüft Morgo Prüfsumme und Signatur."
    }

    suspend fun finishDownload(downloadId: Long) {
        if (downloadId != settings.updateDownloadId()) return
        val manager = context.getSystemService(android.app.DownloadManager::class.java)
        val cursor = manager.query(android.app.DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return
            val status = it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
            if (status != android.app.DownloadManager.STATUS_SUCCESSFUL) {
                report("Download fehlgeschlagen")
                return
            }
            val local = it.getString(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI))
            val file = copyToCache(Uri.parse(local))
            val offer = settings.loadUpdateOffer() ?: return
            offer.sha256Url?.let { url ->
                val text = client.fetchText(url).getOrElse {
                    report("Prüfsumme fehlt")
                    return
                }
                val expected = ApkTrust.parseSha256(text)
                val actual = ApkTrust.sha256Hex(file.readBytes())
                if (expected == null || expected != actual) {
                    file.delete()
                    report("Prüfsumme stimmt nicht")
                    return
                }
            }
            if (!sameSignature(file)) {
                file.delete()
                report("Signatur ist nicht die installierte Morgo-Signatur")
                return
            }
            install(file)
        }
    }

    private fun copyToCache(uri: Uri): File {
        val dest = File(context.cacheDir, "morgo-update.apk")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest
    }

    private fun sameSignature(file: File): Boolean {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, flags)
        } catch (_: NameNotFoundException) {
            return false
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: return false
        return ApkTrust.sameCertificates(certificates(installed), certificates(archive))
    }

    private fun certificates(info: PackageInfo): List<ByteArray> {
        val signing = info.signingInfo ?: return emptyList()
        val signers = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners ?: emptyArray()
        } else {
            signing.signingCertificateHistory ?: emptyArray()
        }
        return signers.map { it.toByteArray() }
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("morgo", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val callback = Intent(context, UpdateInstallReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
        _status.value = "Android fragt jetzt nach der Installation."
    }

    private fun report(message: String) {
        _status.value = message
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Update nicht installiert")
            .setContentText(message)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(45, notification)
    }
}
