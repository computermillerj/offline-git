package dev.jem.offlinegit.domain

interface GithubRemoteDataSource {
    suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network>

    suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Network>

    suspend fun getBranches(
        owner: String,
        repository: String,
    ): Result<List<GithubBranch>, DataError.Network>

    suspend fun getCommits(
        owner: String,
        repository: String,
        branch: String,
        limit: Int = 100,
    ): Result<List<GithubCommitSummary>, DataError.Network>

    suspend fun getCommitDetails(
        owner: String,
        repository: String,
        sha: String,
    ): Result<GithubCommitDetails, DataError.Network>

    suspend fun getBlob(
        owner: String,
        repository: String,
        sha: String,
    ): Result<ByteArray, DataError.Network>
}
