package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.qzz.xandll.morgo.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class RadioStation(
    val name: String,
    val streamUrl: String,
    val country: String
)

@Serializable
private data class RadioStationDto(
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val country: String = ""
)

@Singleton
class RadioBrowserClient @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp)

    suspend fun search(query: String, host: String): Result<List<RadioStation>> {
        val name = query.trim()
        if (name.length < 2) return Result.success(emptyList())
        val base = host.trim().trimEnd('/').ifBlank { "https://de1.radio-browser.info" }
        val encoded = URLEncoder.encode(name, "UTF-8")
        val url = "$base/json/stations/search?name=$encoded&limit=12&hidebroken=true&order=clickcount&reverse=true"
        return try {
            val response = http.get(url) {
                header(HttpHeaders.UserAgent, "Morgo/${BuildConfig.VERSION_NAME}")
                header(HttpHeaders.Accept, "application/json")
            }
            val text = response.bodyAsText()
            if (response.status.value !in 200..299) {
                return Result.failure(IllegalStateException("Radio-Suche HTTP ${response.status.value}"))
            }
            val stations = json.decodeFromString<List<RadioStationDto>>(text).mapNotNull { dto ->
                val stream = dto.urlResolved.ifBlank { dto.url }
                if (dto.name.isBlank() || stream.isBlank()) null
                else RadioStation(dto.name, stream, dto.country)
            }
            Result.success(stations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
