package dev.jem.offlinegit.domain

interface RepositoryStore {
    suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local>

    suspend fun upsertRepositories(repositories: List<GithubRepository>): EmptyResult<DataError.Local>

    suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Local>

    suspend fun upsertBranches(
        repositoryId: Long,
        branches: List<GithubBranch>,
    ): EmptyResult<DataError.Local>

    suspend fun getBranches(repositoryId: Long): Result<List<GithubBranch>, DataError.Local>

    suspend fun upsertCommits(
        repositoryId: Long,
        branchName: String,
        commits: List<GithubCommitSummary>,
    ): EmptyResult<DataError.Local>

    suspend fun getCommits(
        repositoryId: Long,
        branchName: String,
    ): Result<List<GithubCommitSummary>, DataError.Local>

    suspend fun upsertCommitDetails(
        repositoryId: Long,
        commit: GithubCommitDetails,
    ): EmptyResult<DataError.Local>

    suspend fun getCommitDetails(sha: String): Result<GithubCommitDetails, DataError.Local>

    suspend fun upsertDownloadJob(job: BranchDownloadJob): EmptyResult<DataError.Local>

    suspend fun getDownloadJob(
        repositoryId: Long,
        branchName: String,
    ): Result<BranchDownloadJob?, DataError.Local>

    /** Ids of repositories with at least one completed branch download. */
    suspend fun getDownloadedRepositoryIds(): Result<Set<Long>, DataError.Local>
}
