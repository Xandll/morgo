package io.qzz.xandll.morgo.util

import javax.net.ssl.SSLException

/**
 * Einheitliche Nutzertexte für WebUntis, ÖBB und Home Assistant.
 * Technische Details bleiben gekürzt und ohne Antwortkörper.
 */
object ConnectionErrors {
    data class UserFacing(
        val message: String,
        val technical: String?
    )

    enum class Service {
        WEBUNTIS,
        HAFAS,
        HOME_ASSISTANT
    }

    fun describe(error: Throwable?, service: Service): UserFacing {
        val technical = ErrorDetails.summarize(error)
        val blob = generateSequence(error) { it.cause }
            .flatMap { sequenceOf(it.javaClass.simpleName, it.message) }
            .filterNotNull()
            .joinToString(" ")
        val status = generateSequence(error) { it.cause }
            .filterIsInstance<ApiCallException>()
            .firstOrNull()
            ?.statusCode

        val message = when {
            service == Service.HOME_ASSISTANT && (status == 401 || containsAny(blob, "401", "Unauthorized")) ->
                "Token ungültig"
            service == Service.WEBUNTIS && containsAny(
                blob,
                "bad credentials",
                "Invalid username",
                "invalid credentials",
                "401"
            ) -> webUntisCredentials()
            status == 404 || containsAny(blob, "404", "Not Found") ->
                "Falsche URL"
            isCloudflare(status, blob) ->
                "Von Cloudflare blockiert"
            isSsl(error, blob) ->
                "SSL-Fehler"
            isUnreachable(blob) ->
                "Nicht erreichbar"
            service == Service.WEBUNTIS && blob.contains("NoTransformationFound") ->
                "Verbindungsfehler: Server-Antwort konnte nicht verarbeitet werden"
            else -> "Verbindung fehlgeschlagen"
        }
        return UserFacing(message, technical)
    }

    private fun isCloudflare(status: Int?, blob: String): Boolean {
        if (status == 403 || (status != null && status in 520..530) || (status != null && status in 1000..1199)) {
            return true
        }
        return blob.contains("cloudflare", ignoreCase = true) &&
            Regex("\\b1\\d{3}\\b").containsMatchIn(blob)
    }

    private fun isSsl(error: Throwable?, blob: String): Boolean {
        if (generateSequence(error) { it.cause }.any { it is SSLException }) return true
        return containsAny(blob, "SSLHandshake", "SSLException", "CertificateException", "CertPath")
    }

    private fun isUnreachable(blob: String): Boolean {
        return containsAny(
            blob,
            "timeout",
            "timed out",
            "UnknownHost",
            "Unable to resolve host",
            "ConnectException",
            "No address associated"
        )
    }

    private fun containsAny(blob: String, vararg needles: String): Boolean {
        return needles.any { blob.contains(it, ignoreCase = true) }
    }

    private fun webUntisCredentials(): String {
        return buildString {
            append("Anmeldung fehlgeschlagen: Zugangsdaten ungültig\n\n")
            append("Bitte prüfen:\n")
            append("• Interner Schulname korrekt? (aus WebUntis-URL nach 'school=')\n")
            append("• Server-Adresse korrekt? (z.B. mese.webuntis.com)\n")
            append("• Benutzername und Passwort korrekt?\n")
            append("• Falls SSO-Login (Microsoft/Google): QR-Code-Login verwenden")
        }
    }
}

class ApiCallException(
    val statusCode: Int?,
    technicalMessage: String
) : Exception(technicalMessage.take(ErrorDetails.MAX_LENGTH))
