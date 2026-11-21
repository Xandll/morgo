package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.qzz.xandll.morgo.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioBrowserClient @Inject constructor() {
    private val http = HttpClient(OkHttp) {
        expectSuccess = false
    }

    suspend fun search(query: String, host: String): Result<List<RadioStation>> {
        return RadioBrowser.search(http, query, host, RadioBrowser.userAgent(BuildConfig.VERSION_NAME))
    }
}
