package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.domain.GithubAccessToken
import dev.jem.offlinegit.domain.GithubDeviceAuthError
import dev.jem.offlinegit.domain.GithubDeviceCode
import dev.jem.offlinegit.domain.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorGithubDeviceAuthDataSourceTest {

    @Test
    fun `createDeviceCode maps GitHub device code response`() = runTest {
        var path: String? = null
        val dataSource = KtorGithubDeviceAuthDataSource(
            httpClient = GithubHttpClientFactory.create(
                MockEngine { request ->
                    path = request.url.encodedPath
                    respond(
                        content = """
                            {
                              "device_code": "device-code",
                              "user_code": "ABCD-1234",
                              "verification_uri": "https://github.com/login/device",
                              "expires_in": 900,
                              "interval": 5
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ),
        )

        val result = dataSource.createDeviceCode(clientId = "client-id")

        val code = assertIs<Result.Success<GithubDeviceCode>>(result).data
        assertEquals("/login/oauth/device/code", path)
        assertEquals("ABCD-1234", code.userCode)
        assertEquals(900, code.expiresInSeconds)
    }

    @Test
    fun `pollAccessToken maps successful token response`() = runTest {
        val dataSource = KtorGithubDeviceAuthDataSource(
            httpClient = GithubHttpClientFactory.create(
                MockEngine {
                    respond(
                        content = """{"access_token":"token","token_type":"bearer"} """,
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ),
        )

        val result = dataSource.pollAccessToken(clientId = "client-id", deviceCode = "device-code")

        assertEquals("token", assertIs<Result.Success<GithubAccessToken>>(result).data.accessToken)
    }

    @Test
    fun `pollAccessToken maps pending response`() = runTest {
        val dataSource = KtorGithubDeviceAuthDataSource(
            httpClient = GithubHttpClientFactory.create(
                MockEngine {
                    respond(
                        content = """{"error":"authorization_pending"}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                },
            ),
        )

        val result = dataSource.pollAccessToken(clientId = "client-id", deviceCode = "device-code")

        assertEquals(Result.Error(GithubDeviceAuthError.AuthorizationPending), result)
    }

    private fun jsonHeaders(): Headers = HeadersBuilder().apply {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }.build()
}
