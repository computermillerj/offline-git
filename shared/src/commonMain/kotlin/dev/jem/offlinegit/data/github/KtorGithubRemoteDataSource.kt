package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.data.github.dto.GithubAccountDto
import dev.jem.offlinegit.data.github.dto.GithubBranchDto
import dev.jem.offlinegit.data.github.dto.GithubCommitDto
import dev.jem.offlinegit.data.github.dto.GithubRepositoryDto
import dev.jem.offlinegit.data.github.dto.toGithubAccount
import dev.jem.offlinegit.data.github.dto.toGithubBranch
import dev.jem.offlinegit.data.github.dto.toGithubCommitDetails
import dev.jem.offlinegit.data.github.dto.toGithubCommitSummary
import dev.jem.offlinegit.data.github.dto.toGithubRepository
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.TokenStore
import dev.jem.offlinegit.domain.map
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

class KtorGithubRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore,
    private val baseUrl: String = "https://api.github.com",
) : GithubRemoteDataSource {

    override suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network> = withToken { token ->
        getJson<GithubAccountDto>(path = "/user", token = token)
            .map { it.toGithubAccount() }
    }

    override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Network> = withToken { token ->
        getPaginatedJson<GithubRepositoryDto>(
            path = "/user/repos",
            token = token,
            queryParameters = mapOf("sort" to "updated"),
        ).map { repositories -> repositories.map { it.toGithubRepository() } }
    }

    override suspend fun getBranches(
        owner: String,
        repository: String,
    ): Result<List<GithubBranch>, DataError.Network> = withToken { token ->
        getPaginatedJson<GithubBranchDto>(
            path = "/repos/$owner/$repository/branches",
            token = token,
        ).map { branches -> branches.map { it.toGithubBranch() } }
    }

    override suspend fun getCommits(
        owner: String,
        repository: String,
        branch: String,
        limit: Int,
    ): Result<List<GithubCommitSummary>, DataError.Network> = withToken { token ->
        getPaginatedJson<GithubCommitDto>(
            path = "/repos/$owner/$repository/commits",
            token = token,
            queryParameters = mapOf("sha" to branch),
            limit = limit,
        ).map { commits -> commits.map { it.toGithubCommitSummary() } }
    }

    override suspend fun getCommitDetails(
        owner: String,
        repository: String,
        sha: String,
    ): Result<GithubCommitDetails, DataError.Network> = withToken { token ->
        getJson<GithubCommitDto>(
            path = "/repos/$owner/$repository/commits/$sha",
            token = token,
        ).map { it.toGithubCommitDetails() }
    }

    override suspend fun getBlob(
        owner: String,
        repository: String,
        sha: String,
    ): Result<ByteArray, DataError.Network> = withToken { token ->
        getBytes(
            path = "/repos/$owner/$repository/git/blobs/$sha",
            token = token,
            accept = GITHUB_RAW_MEDIA_TYPE,
        )
    }

    private suspend fun <T> withToken(
        block: suspend (String) -> Result<T, DataError.Network>,
    ): Result<T, DataError.Network> {
        val token = tokenStore.getToken()?.trim()
        return if (token.isNullOrEmpty()) {
            Result.Error(DataError.Network.UNAUTHORIZED)
        } else {
            block(token)
        }
    }

    private suspend inline fun <reified T> getJson(
        path: String,
        token: String,
        queryParameters: Map<String, Any?> = emptyMap(),
    ): Result<T, DataError.Network> = request {
        httpClient.get(urlString(path)) {
            addGithubHeaders(token)
            queryParameters.forEach { (key, value) -> parameter(key, value) }
        }
    }

    private suspend fun getBytes(
        path: String,
        token: String,
        accept: String,
    ): Result<ByteArray, DataError.Network> = request {
        httpClient.get(urlString(path)) {
            addGithubHeaders(token = token, accept = accept)
        }
    }

    private suspend inline fun <reified T> getPaginatedJson(
        path: String,
        token: String,
        queryParameters: Map<String, Any?> = emptyMap(),
        limit: Int? = null,
    ): Result<List<T>, DataError.Network> {
        if (limit != null && limit <= 0) {
            return Result.Success(emptyList())
        }

        val items = mutableListOf<T>()
        var page = 1
        while (true) {
            val remaining = limit?.minus(items.size)
            if (remaining != null && remaining <= 0) {
                return Result.Success(items.toList())
            }

            val pageResult = requestResponse(
                execute = {
                    httpClient.get(urlString(path)) {
                        addGithubHeaders(token)
                        queryParameters.forEach { (key, value) -> parameter(key, value) }
                        parameter("per_page", minOf(PER_PAGE, remaining ?: PER_PAGE))
                        parameter("page", page)
                    }
                },
                read = { response ->
                    Page(
                        data = response.body<List<T>>(),
                        hasNextPage = response.headers[LINK_HEADER]?.hasNextPage() == true,
                    )
                },
            )

            when (pageResult) {
                is Result.Error -> return Result.Error(pageResult.error)
                is Result.Success -> {
                    items += pageResult.data.data
                    if (!pageResult.data.hasNextPage || pageResult.data.data.isEmpty()) {
                        return Result.Success(items.toList())
                    }
                    page++
                }
            }
        }
    }

    private suspend inline fun <reified T> request(
        crossinline execute: suspend () -> HttpResponse,
    ): Result<T, DataError.Network> = requestResponse(
        execute = execute,
        read = { response -> response.body<T>() },
    )

    private suspend inline fun <T> requestResponse(
        crossinline execute: suspend () -> HttpResponse,
        crossinline read: suspend (HttpResponse) -> T,
    ): Result<T, DataError.Network> = try {
        responseToResult(response = execute(), read = read)
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

    private suspend inline fun <T> responseToResult(
        response: HttpResponse,
        read: suspend (HttpResponse) -> T,
    ): Result<T, DataError.Network> = when {
        response.status.value in 200..299 -> Result.Success(read(response))
        response.status == HttpStatusCode.BadRequest -> Result.Error(DataError.Network.BAD_REQUEST)
        response.status == HttpStatusCode.Unauthorized -> Result.Error(DataError.Network.UNAUTHORIZED)
        response.status == HttpStatusCode.Forbidden && response.headers[RATE_LIMIT_REMAINING_HEADER] == "0" -> {
            Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        }
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

    private fun HttpRequestBuilder.addGithubHeaders(
        token: String,
        accept: String = GITHUB_JSON_MEDIA_TYPE,
    ) {
        bearerAuth(token)
        header(HttpHeaders.Accept, accept)
        header(GITHUB_API_VERSION_HEADER, GITHUB_API_VERSION)
    }

    private fun urlString(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    private fun String.hasNextPage(): Boolean = split(',').any { link ->
        link.contains("rel=\"next\"")
    }

    private data class Page<out T>(
        val data: List<T>,
        val hasNextPage: Boolean,
    )

    private companion object {
        const val GITHUB_API_VERSION = "2022-11-28"
        const val GITHUB_API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val GITHUB_JSON_MEDIA_TYPE = "application/vnd.github+json"
        const val GITHUB_RAW_MEDIA_TYPE = "application/vnd.github.raw"
        const val LINK_HEADER = "Link"
        const val PER_PAGE = 100
        const val RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining"
    }
}
