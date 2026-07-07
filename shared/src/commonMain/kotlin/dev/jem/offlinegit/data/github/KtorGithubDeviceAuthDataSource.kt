package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccessToken
import dev.jem.offlinegit.domain.GithubDeviceAuthDataSource
import dev.jem.offlinegit.domain.GithubDeviceAuthError
import dev.jem.offlinegit.domain.GithubDeviceCode
import dev.jem.offlinegit.domain.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

class KtorGithubDeviceAuthDataSource(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://github.com/login/oauth",
) : GithubDeviceAuthDataSource {

    override suspend fun createDeviceCode(clientId: String): Result<GithubDeviceCode, DataError.Network> = request(
        execute = {
            httpClient.submitForm(
                url = urlString("/device/code"),
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("scope", DEFAULT_SCOPE)
                },
            ) {
                header(HttpHeaders.Accept, JSON_MEDIA_TYPE)
            }
        },
        read = { response -> response.body<GithubDeviceCodeDto>().toGithubDeviceCode() },
    )

    override suspend fun pollAccessToken(
        clientId: String,
        deviceCode: String,
    ): Result<GithubAccessToken, GithubDeviceAuthError> = when (
        val result = request(
            execute = {
                httpClient.submitForm(
                    url = urlString("/access_token"),
                    formParameters = Parameters.build {
                        append("client_id", clientId)
                        append("device_code", deviceCode)
                        append("grant_type", DEVICE_CODE_GRANT_TYPE)
                    },
                ) {
                    header(HttpHeaders.Accept, JSON_MEDIA_TYPE)
                }
            },
            read = { response -> response.body<GithubAccessTokenDto>() },
        )
    ) {
        is Result.Error -> Result.Error(GithubDeviceAuthError.Network(result.error))
        is Result.Success -> result.data.toResult()
    }

    private suspend inline fun <T> request(
        crossinline execute: suspend () -> HttpResponse,
        crossinline read: suspend (HttpResponse) -> T,
    ): Result<T, DataError.Network> = try {
        val response = execute()
        if (response.status.value in 200..299) {
            Result.Success(read(response))
        } else {
            Result.Error(response.status.toNetworkError())
        }
    } catch (e: JsonConvertException) {
        Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: SerializationException) {
        Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: IOException) {
        Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Error(DataError.Network.UNKNOWN)
    }

    private fun GithubAccessTokenDto.toResult(): Result<GithubAccessToken, GithubDeviceAuthError> {
        val token = accessToken
        return when {
            token != null -> Result.Success(GithubAccessToken(accessToken = token))
            error == "authorization_pending" -> Result.Error(GithubDeviceAuthError.AuthorizationPending)
            error == "slow_down" -> Result.Error(GithubDeviceAuthError.SlowDown)
            error == "expired_token" -> Result.Error(GithubDeviceAuthError.ExpiredToken)
            error == "access_denied" -> Result.Error(GithubDeviceAuthError.AccessDenied)
            else -> Result.Error(GithubDeviceAuthError.Network(DataError.Network.UNKNOWN))
        }
    }

    private fun GithubDeviceCodeDto.toGithubDeviceCode(): GithubDeviceCode = GithubDeviceCode(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUri = verificationUri,
        expiresInSeconds = expiresIn,
        intervalSeconds = interval,
    )

    private fun HttpStatusCode.toNetworkError(): DataError.Network = when (this) {
        HttpStatusCode.BadRequest -> DataError.Network.BAD_REQUEST
        HttpStatusCode.Unauthorized -> DataError.Network.UNAUTHORIZED
        HttpStatusCode.Forbidden -> DataError.Network.FORBIDDEN
        HttpStatusCode.NotFound -> DataError.Network.NOT_FOUND
        HttpStatusCode.Conflict -> DataError.Network.CONFLICT
        HttpStatusCode.RequestTimeout -> DataError.Network.REQUEST_TIMEOUT
        HttpStatusCode.PayloadTooLarge -> DataError.Network.PAYLOAD_TOO_LARGE
        HttpStatusCode.TooManyRequests -> DataError.Network.TOO_MANY_REQUESTS
        HttpStatusCode.ServiceUnavailable -> DataError.Network.SERVICE_UNAVAILABLE
        else -> if (value in 500..599) DataError.Network.SERVER_ERROR else DataError.Network.UNKNOWN
    }

    private fun urlString(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    @Serializable
    private data class GithubDeviceCodeDto(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int,
    )

    @Serializable
    private data class GithubAccessTokenDto(
        @SerialName("access_token") val accessToken: String? = null,
        val error: String? = null,
    )

    private companion object {
        const val DEFAULT_SCOPE = "repo read:user"
        const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
