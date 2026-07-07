package dev.jem.offlinegit.domain

/** Where the user last was; restored on launch so process death loses nothing. */
data class StoredSelection(
    val repositoryId: Long?,
    val branchName: String?,
    val commitSha: String?,
    val filePath: String?,
)

interface RepositoryStore {
    suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local>

    suspend fun getAccounts(): Result<List<GithubAccount>, DataError.Local>

    /** The most recently activated account, or null when none is signed in. */
    suspend fun getActiveAccount(): Result<GithubAccount?, DataError.Local>

    suspend fun setActiveAccount(accountId: Long): EmptyResult<DataError.Local>

    /**
     * Removes the account, its repository links, and any repositories no other
     * account can still see (cascading to their offline data).
     */
    suspend fun removeAccount(accountId: Long): EmptyResult<DataError.Local>

    /** Records which repositories [accountId] listed, replacing earlier links. */
    suspend fun replaceAccountRepositories(
        accountId: Long,
        repositoryIds: List<Long>,
    ): EmptyResult<DataError.Local>

    suspend fun getRepositoriesForAccount(accountId: Long): Result<List<GithubRepository>, DataError.Local>

    suspend fun getSelection(): Result<StoredSelection?, DataError.Local>

    suspend fun saveSelection(selection: StoredSelection): EmptyResult<DataError.Local>

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
