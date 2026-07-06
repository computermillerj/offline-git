package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.data.github.dto.GithubAccountDto
import dev.jem.offlinegit.data.github.dto.toGithubAccount
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.TokenStore
import dev.jem.offlinegit.domain.map
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

class KtorGithubRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore,
    private val baseUrl: String = "https://api.github.com",
) : GithubRemoteDataSource {

    override suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network> {
        val token = tokenStore.getToken()?.trim()
        if (token.isNullOrEmpty()) {
            return Result.Error(DataError.Network.UNAUTHORIZED)
        }

        return request<GithubAccountDto> {
            httpClient.get("$baseUrl/user") {
                bearerAuth(token)
                header(HttpHeaders.Accept, GITHUB_JSON_MEDIA_TYPE)
                header(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
            }
        }.map { it.toGithubAccount() }
    }

    private suspend inline fun <reified T> request(
        crossinline execute: suspend () -> HttpResponse,
    ): Result<T, DataError.Network> = try {
        responseToResult(execute())
    } catch (e: JsonConvertException) {
        Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: SerializationException) {
        Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(DataError.Network.UNKNOWN)
    }

    private suspend inline fun <reified T> responseToResult(
        response: HttpResponse,
    ): Result<T, DataError.Network> = when {
        response.status.value in 200..299 -> Result.Success(response.body<T>())
        response.status == HttpStatusCode.BadRequest -> Result.Error(DataError.Network.BAD_REQUEST)
        response.status == HttpStatusCode.Unauthorized -> Result.Error(DataError.Network.UNAUTHORIZED)
        response.status == HttpStatusCode.Forbidden -> Result.Error(DataError.Network.FORBIDDEN)
        response.status == HttpStatusCode.NotFound -> Result.Error(DataError.Network.NOT_FOUND)
        response.status == HttpStatusCode.Conflict -> Result.Error(DataError.Network.CONFLICT)
        response.status == HttpStatusCode.RequestTimeout -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        response.status == HttpStatusCode.PayloadTooLarge -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        response.status == HttpStatusCode.TooManyRequests -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        response.status == HttpStatusCode.ServiceUnavailable -> Result.Error(DataError.Network.SERVICE_UNAVAILABLE)
        response.status.value in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val GITHUB_JSON_MEDIA_TYPE = "application/vnd.github+json"
    }
}
