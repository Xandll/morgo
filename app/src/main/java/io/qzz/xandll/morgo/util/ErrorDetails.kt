package io.qzz.xandll.morgo.util

/**
 * Kürzt Fehlermeldungen für die Anzeige.
 * WebUntis-Exceptions enthalten oft die komplette JSON-Antwort (Stundenplan, Namen).
 */
object ErrorDetails {
    const val MAX_LENGTH = 300

    fun summarize(error: Throwable?): String? {
        if (error == null) return null
        val type = error.javaClass.simpleName.ifBlank { "Fehler" }
        val cause = shortCause(error.message)
        val text = if (cause.isBlank()) type else "$type: $cause"
        return if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH - 1) + "…"
    }

    private fun shortCause(message: String?): String {
        if (message.isNullOrBlank()) return ""
        var text = message
            .substringBefore("JSON input")
            .substringBefore("json input")
            .substringBefore("JSON Input")
            .lineSequence()
            .first()
        val brace = text.indexOf('{')
        if (brace >= 0) text = text.substring(0, brace)
        val bracket = text.indexOf('[')
        if (bracket >= 0) text = text.substring(0, bracket)
        text = SECRET_PATTERN.replace(text) { "${it.groupValues[1]}=***" }
        return text.replace(Regex("\\s+"), " ").trim().trimEnd(':', '-', '–', ',', '.')
    }

    private val SECRET_PATTERN = Regex(
        "(?i)(password|secret|otp|token|jsessionid|cookie|authorization|key)\\s*[=:]\\s*\\S+"
    )
}
