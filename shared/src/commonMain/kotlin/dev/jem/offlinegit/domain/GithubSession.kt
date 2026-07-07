package dev.jem.offlinegit.domain

/** The remote-facing graph for one identity: every call uses that identity's token. */
data class GithubSession(
    val remoteDataSource: GithubRemoteDataSource,
    val branchDownloader: BranchDownloader,
)

interface GithubSessionFactory {
    /** Session authenticated as a signed-in account. */
    fun sessionFor(accountId: Long): GithubSession

    /** Session for a raw token that has no account yet (sign-in probe). */
    fun sessionForToken(token: String): GithubSession
}
