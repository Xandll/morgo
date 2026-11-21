package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

data class RadioStation(
    val name: String,
    val streamUrl: String,
    val country: String
)

/**
 * Radio-Browser-Suche ohne generierten Serializer.
 * Eine @Serializable-Liste mit verschachtelten Feldern ist im Release schon einmal
 * an R8 gescheitert (Home-Assistant-Zustände). JsonElement bleibt absichtlich stehen.
 */
object RadioBrowser {
    const val DEFAULT_HOST = "https://de1.api.radio-browser.info"

    fun userAgent(version: String): String = "Morgo/$version"
    private const val DIRECTORY = "https://all.api.radio-browser.info/json/servers"
    val fallbackHosts: List<String> = listOf(
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    private val legacyHosts = setOf(
        "https://de1.radio-browser.info",
        "http://de1.radio-browser.info"
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val umlautPairs = listOf(
        "oe" to "ö",
        "ae" to "ä",
        "ue" to "ü",
        "ö" to "oe",
        "ä" to "ae",
        "ü" to "ue"
    )

    fun migrateHost(stored: String?): String {
        val normalized = normalizeHost(stored)
        return if (normalized in legacyHosts) DEFAULT_HOST else normalized
    }

    fun normalizeHost(stored: String?): String {
        val trimmed = stored?.trim()?.trimEnd('/')?.ifBlank { null } ?: return DEFAULT_HOST
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /** Original plus eine Ersetzung je Umlaut-Paar, ohne Duplikate. */
    fun variants(query: String): List<String> {
        val seed = query.trim()
        if (seed.length < 2) return emptyList()
        val found = linkedSetOf(seed)
        for ((from, to) in umlautPairs) {
            val replaced = seed.replace(from, to, ignoreCase = true)
            if (replaced.length >= 2) found += replaced
        }
        return found.toList()
    }

    fun parseStations(text: String): List<RadioStation> {
        val root = json.parseToJsonElement(text)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> listOf(root)
            else -> throw IllegalArgumentException("Radio-Antwort ungültig")
        }
        return items.mapNotNull { element -> parseStation(element) }
    }

    fun parseServers(text: String): List<String> {
        val root = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return root.mapNotNull { element ->
            val name = (element as? JsonObject)?.string("name")?.trim()?.trimEnd('/').orEmpty()
            if (name.isBlank()) null else normalizeHost(name)
        }
    }

    fun merge(lists: List<List<RadioStation>>, limit: Int = 12): List<RadioStation> {
        val seen = HashSet<String>()
        val out = ArrayList<RadioStation>()
        for (list in lists) {
            for (station in list) {
                val key = station.streamUrl.trim().lowercase()
                if (key.isEmpty() || !seen.add(key)) continue
                out += station
                if (out.size >= limit) return out
            }
        }
        return out
    }

    suspend fun search(
        http: HttpClient,
        query: String,
        host: String,
        userAgent: String
    ): Result<List<RadioStation>> {
        val names = variants(query)
        if (names.isEmpty()) return Result.success(emptyList())
        val preferred = migrateHost(host)
        val primary = (listOf(preferred) + fallbackHosts).map(::normalizeHost).distinct()
        var last: Exception? = null
        for (candidate in primary) {
            try {
                return Result.success(searchHost(http, candidate, names, userAgent))
            } catch (error: Exception) {
                last = error
            }
        }
        for (candidate in directoryHosts(http, userAgent)) {
            if (candidate in primary) continue
            try {
                return Result.success(searchHost(http, candidate, names, userAgent))
            } catch (error: Exception) {
                last = error
            }
        }
        return Result.failure(last ?: IllegalStateException("Radiosuche fehlgeschlagen"))
    }

    private suspend fun searchHost(
        http: HttpClient,
        host: String,
        names: List<String>,
        userAgent: String
    ): List<RadioStation> {
        val lists = names.map { name ->
            val response = http.get(searchUrl(host, name)) {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
            if (response.status.value !in 200..299) {
                throw IllegalStateException("Radio-Suche HTTP ${response.status.value}")
            }
            parseStations(response.bodyAsText())
        }
        return merge(lists)
    }

    private suspend fun directoryHosts(http: HttpClient, userAgent: String): List<String> {
        return try {
            val response = http.get(DIRECTORY) {
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
            if (response.status.value !in 200..299) emptyList()
            else parseServers(response.bodyAsText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchUrl(host: String, name: String): String {
        val encoded = URLEncoder.encode(name, Charsets.UTF_8.name())
        return "$host/json/stations/search?name=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=12"
    }

    private fun parseStation(element: JsonElement): RadioStation? {
        val obj = element as? JsonObject ?: return null
        val name = obj.string("name")?.trim().orEmpty()
        val stream = obj.string("url_resolved")?.trim().orEmpty().ifBlank {
            obj.string("url")?.trim().orEmpty()
        }
        if (name.isBlank() || stream.isBlank()) return null
        return RadioStation(name, stream, obj.string("country")?.trim().orEmpty())
    }

    private fun JsonObject.string(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }
}
