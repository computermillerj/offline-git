package dev.jem.offlinegit.data.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object GithubHttpClientFactory {
    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
    }
}
