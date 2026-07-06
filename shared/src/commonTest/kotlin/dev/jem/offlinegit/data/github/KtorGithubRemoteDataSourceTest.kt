package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.TokenStore
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
                headers = jsonHeaders(),
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

    @Test
    fun `getRepositories paginates and maps repositories`() = runTest {
        val requestedPages = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedPages += request.url.parameters["page"] ?: error("missing page")
            when (request.url.parameters["page"]) {
                "1" -> respond(
                    content = """
                        [
                          {
                            "id": 1,
                            "name": "hello",
                            "owner": {"login": "octo"},
                            "default_branch": "main",
                            "private": false,
                            "updated_at": "2026-01-01T00:00:00Z"
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(link = "<https://api.github.com/user/repos?page=2>; rel=\"next\""),
                )
                "2" -> respond(
                    content = """
                        [
                          {
                            "id": 2,
                            "name": "private-repo",
                            "owner": {"login": "octo"},
                            "default_branch": "trunk",
                            "private": true
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
                else -> error("unexpected page ${request.url.parameters["page"]}")
            }
        }
        val dataSource = KtorGithubRemoteDataSource(
            httpClient = GithubHttpClientFactory.create(engine),
            tokenStore = StaticTokenStore("token"),
        )

        val result = dataSource.getRepositories()

        val repositories = assertIs<Result.Success<List<GithubRepository>>>(result).data
        assertEquals(listOf("1", "2"), requestedPages)
        assertEquals(2, repositories.size)
        assertEquals("octo", repositories[0].owner)
        assertEquals("main", repositories[0].defaultBranch)
        assertEquals(true, repositories[1].isPrivate)
    }

    @Test
    fun `branches commits and commit details map GitHub responses`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/repos/octo/hello/branches" -> respond(
                    content = """[{"name":"main","commit":{"sha":"head-sha"}}]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
                "/repos/octo/hello/commits" -> {
                    assertEquals("main", request.url.parameters["sha"])
                    assertEquals("1", request.url.parameters["per_page"])
                    respond(
                        content = """
                            [
                              {
                                "sha": "commit-sha",
                                "commit": {
                                  "author": {
                                    "name": "Jane",
                                    "email": "jane@example.com",
                                    "date": "2026-01-02T03:04:05Z"
                                  },
                                  "message": "Add README"
                                },
                                "parents": [{"sha": "parent-sha"}]
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                "/repos/octo/hello/commits/commit-sha" -> respond(
                    content = """
                        {
                          "sha": "commit-sha",
                          "commit": {
                            "author": {
                              "name": "Jane",
                              "email": "jane@example.com",
                              "date": "2026-01-02T03:04:05Z"
                            },
                            "message": "Add README"
                          },
                          "parents": [{"sha": "parent-sha"}],
                          "files": [
                            {
                              "sha": "blob-sha",
                              "filename": "README.md",
                              "status": "modified",
                              "additions": 2,
                              "deletions": 1,
                              "patch": "@@ -1 +1 @@"
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
                else -> error("unexpected path ${request.url.encodedPath}")
            }
        }
        val dataSource = KtorGithubRemoteDataSource(
            httpClient = GithubHttpClientFactory.create(engine),
            tokenStore = StaticTokenStore("token"),
        )

        val branches = assertIs<Result.Success<List<GithubBranch>>>(
            dataSource.getBranches(owner = "octo", repository = "hello"),
        ).data
        val commits = assertIs<Result.Success<List<GithubCommitSummary>>>(
            dataSource.getCommits(owner = "octo", repository = "hello", branch = "main", limit = 1),
        ).data
        val details = assertIs<Result.Success<GithubCommitDetails>>(
            dataSource.getCommitDetails(owner = "octo", repository = "hello", sha = "commit-sha"),
        ).data

        assertEquals("head-sha", branches.single().headSha)
        assertEquals("parent-sha", commits.single().firstParentSha)
        assertEquals("Jane", commits.single().authorName)
        assertEquals("README.md", details.files.single().path)
        assertEquals("blob-sha", details.files.single().blobSha)
        assertEquals("@@ -1 +1 @@", details.files.single().patch)
    }

    @Test
    fun `rate limited forbidden response maps to too many requests`() = runTest {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(rateLimitRemaining = "0"),
            )
        }
        val dataSource = KtorGithubRemoteDataSource(
            httpClient = GithubHttpClientFactory.create(engine),
            tokenStore = StaticTokenStore("token"),
        )

        val result = dataSource.getRepositories()

        assertEquals(Result.Error(DataError.Network.TOO_MANY_REQUESTS), result)
    }

    private fun jsonHeaders(
        link: String? = null,
        rateLimitRemaining: String? = null,
    ): Headers = HeadersBuilder().apply {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        if (link != null) {
            append(HttpHeaders.Link, link)
        }
        if (rateLimitRemaining != null) {
            append("X-RateLimit-Remaining", rateLimitRemaining)
        }
    }.build()

    private class StaticTokenStore(private val token: String?) : TokenStore {
        override suspend fun getToken(): String? = token
    }
}
