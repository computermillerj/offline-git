package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.TokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorGithubRemoteDataSourceTest {

    @Test
    fun `getAuthenticatedUser sends bearer token and maps account`() = runTest {
        var authorizationHeader: String? = null
        var apiVersionHeader: String? = null
        var path: String? = null
        val engine = MockEngine { request ->
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            apiVersionHeader = request.headers["X-GitHub-Api-Version"]
            path = request.url.encodedPath
            respond(
                content = """{"id":1,"login":"octocat","avatar_url":"https://example.com/avatar.png"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val dataSource = KtorGithubRemoteDataSource(
            httpClient = GithubHttpClientFactory.create(engine),
            tokenStore = StaticTokenStore(" test-token "),
        )

        val result = dataSource.getAuthenticatedUser()

        val account = assertIs<Result.Success<GithubAccount>>(result).data
        assertEquals("Bearer test-token", authorizationHeader)
        assertEquals("2022-11-28", apiVersionHeader)
        assertEquals("/user", path)
        assertEquals("octocat", account.login)
    }

    @Test
    fun `getAuthenticatedUser returns unauthorized when token is missing`() = runTest {
        val dataSource = KtorGithubRemoteDataSource(
            httpClient = GithubHttpClientFactory.create(MockEngine { error("network should not be called") }),
            tokenStore = StaticTokenStore(null),
        )

        val result = dataSource.getAuthenticatedUser()

        assertEquals(Result.Error(DataError.Network.UNAUTHORIZED), result)
    }

    private class StaticTokenStore(private val token: String?) : TokenStore {
        override suspend fun getToken(): String? = token
    }
}
