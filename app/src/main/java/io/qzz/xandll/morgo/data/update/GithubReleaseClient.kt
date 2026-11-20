package io.qzz.xandll.morgo.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.domain.update.GithubRelease
import io.qzz.xandll.morgo.domain.update.GithubReleaseJson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubReleaseClient @Inject constructor() {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    suspend fun latest(owner: String, repo: String): Result<GithubRelease> {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        if (!SLUG.matches(cleanOwner) || !SLUG.matches(cleanRepo)) {
            return Result.failure(IllegalArgumentException("Repository-Name ungültig"))
        }
        return runCatching {
            val response = http.get("https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "Morgo/${BuildConfig.VERSION_NAME}")
            }
            val body = response.bodyAsText()
            when (response.status) {
                HttpStatusCode.OK -> GithubReleaseJson.parse(body)
                HttpStatusCode.NotFound -> throw IllegalStateException("Kein Release gefunden")
                else -> throw IllegalStateException("GitHub antwortet mit ${response.status.value}")
            }
        }
    }

    suspend fun fetchText(url: String): Result<String> {
        if (!url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Nur https-Adressen"))
        }
        return runCatching {
            val response = http.get(url) {
                header(HttpHeaders.UserAgent, "Morgo/${BuildConfig.VERSION_NAME}")
            }
            if (response.status != HttpStatusCode.OK) {
                throw IllegalStateException("Prüfsumme nicht geladen")
            }
            val text = response.bodyAsText()
            if (text.length > 4096) throw IllegalStateException("Prüfsumme unlesbar")
            text
        }
    }

    companion object {
        private val SLUG = Regex("[A-Za-z0-9_.-]{1,100}")
    }
}
