package dev.jem.offlinegit.sync

import dev.jem.offlinegit.data.github.KtorGithubRemoteDataSource
import dev.jem.offlinegit.domain.AccountTokenStore
import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.GithubSession
import dev.jem.offlinegit.domain.GithubSessionFactory
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.TokenStore
import io.ktor.client.HttpClient

/**
 * Builds per-identity remote graphs over one shared [HttpClient], so switching
 * accounts swaps tokens without threading an account id through every call.
 */
class KtorGithubSessionFactory(
    private val httpClient: HttpClient,
    private val accountTokenStore: AccountTokenStore,
    private val repositoryStore: RepositoryStore,
    private val patchStore: PatchStore,
    private val blobStore: BlobStore,
    private val baseUrl: String = "https://api.github.com",
) : GithubSessionFactory {

    override fun sessionFor(accountId: Long): GithubSession = session(
        object : TokenStore {
            override suspend fun getToken(): String? = accountTokenStore.getToken(accountId)
        },
    )

    override fun sessionForToken(token: String): GithubSession = session(
        object : TokenStore {
            override suspend fun getToken(): String = token
        },
    )

    private fun session(tokenStore: TokenStore): GithubSession {
        val remoteDataSource = KtorGithubRemoteDataSource(
            httpClient = httpClient,
            tokenStore = tokenStore,
            baseUrl = baseUrl,
        )
        return GithubSession(
            remoteDataSource = remoteDataSource,
            branchDownloader = GithubBranchDownloader(
                remoteDataSource = remoteDataSource,
                repositoryStore = repositoryStore,
                patchStore = patchStore,
                blobStore = blobStore,
            ),
        )
    }
}
