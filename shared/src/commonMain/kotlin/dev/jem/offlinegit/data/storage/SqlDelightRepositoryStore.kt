package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.StoredSelection

class SqlDelightRepositoryStore(
    private val database: OfflineGitDatabase,
) : RepositoryStore {

    private val queries get() = database.offlineGitQueries

    override suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local> = localResult {
        queries.upsertAccount(
            id = account.id,
            login = account.login,
            avatarUrl = account.avatarUrl,
        )
    }

    override suspend fun getAccounts(): Result<List<GithubAccount>, DataError.Local> = localResult {
        queries.selectAccounts()
            .executeAsList()
            .map { it.toGithubAccount() }
    }

    override suspend fun getActiveAccount(): Result<GithubAccount?, DataError.Local> = localResult {
        queries.selectActiveAccount()
            .executeAsOneOrNull()
            ?.toGithubAccount()
    }

    override suspend fun setActiveAccount(accountId: Long): EmptyResult<DataError.Local> = localResult {
        queries.touchAccount(id = accountId)
    }

    override suspend fun removeAccount(accountId: Long): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            // Order matters: the orphan check reads this account's junction
            // rows, so it must run before they are deleted. Each orphaned
            // repository's data is removed table by table — foreign keys are
            // off, so the schema's cascades never fire.
            val orphanedRepositoryIds = queries.selectRepositoryIdsOnlyVisibleToAccount(accountId = accountId)
                .executeAsList()
            orphanedRepositoryIds.forEach { repositoryId ->
                queries.deleteCommitFilesForRepository(repositoryId)
                queries.deleteBranchCommitsForRepository(repositoryId)
                queries.deleteCommitsForRepository(repositoryId)
                queries.deleteDownloadJobsForRepository(repositoryId)
                queries.deleteBranches(repositoryId)
                queries.deleteRepository(repositoryId)
            }
            queries.deleteAccountRepositories(accountId = accountId)
            queries.deleteAccount(id = accountId)
        }
    }

    override suspend fun replaceAccountRepositories(
        accountId: Long,
        repositoryIds: List<Long>,
    ): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            queries.deleteAccountRepositories(accountId = accountId)
            repositoryIds.forEach { repositoryId ->
                queries.insertAccountRepository(
                    accountId = accountId,
                    repositoryId = repositoryId,
                )
            }
        }
    }

    override suspend fun getRepositoriesForAccount(
        accountId: Long,
    ): Result<List<GithubRepository>, DataError.Local> = localResult {
        queries.selectRepositoriesForAccount(accountId = accountId)
            .executeAsList()
            .map { it.toGithubRepository() }
    }

    override suspend fun getSelection(): Result<StoredSelection?, DataError.Local> = localResult {
        queries.selectSelection()
            .executeAsOneOrNull()
            ?.let {
                StoredSelection(
                    repositoryId = it.repositoryId,
                    branchName = it.branchName,
                    commitSha = it.commitSha,
                    filePath = it.filePath,
                )
            }
    }

    override suspend fun saveSelection(selection: StoredSelection): EmptyResult<DataError.Local> = localResult {
        queries.upsertSelection(
            repositoryId = selection.repositoryId,
            branchName = selection.branchName,
            commitSha = selection.commitSha,
            filePath = selection.filePath,
        )
    }

    override suspend fun upsertRepositories(
        repositories: List<GithubRepository>,
    ): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            repositories.forEach { repository ->
                queries.upsertRepository(
                    id = repository.id,
                    owner = repository.owner,
                    name = repository.name,
                    defaultBranch = repository.defaultBranch,
                    isPrivate = repository.isPrivate.toLong(),
                    updatedAt = repository.updatedAt,
                )
            }
        }
    }

    override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Local> = localResult {
        queries.selectRepositories()
            .executeAsList()
            .map { it.toGithubRepository() }
    }

    override suspend fun upsertBranches(
        repositoryId: Long,
        branches: List<GithubBranch>,
    ): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            branches.forEach { branch ->
                queries.upsertBranch(
                    repositoryId = repositoryId,
                    name = branch.name,
                    headSha = branch.headSha,
                    downloadedAt = null,
                )
            }
        }
    }

    override suspend fun getBranches(repositoryId: Long): Result<List<GithubBranch>, DataError.Local> = localResult {
        queries.selectBranches(repositoryId = repositoryId)
            .executeAsList()
            .map { it.toGithubBranch() }
    }

    override suspend fun upsertCommits(
        repositoryId: Long,
        branchName: String,
        commits: List<GithubCommitSummary>,
    ): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            queries.deleteBranchCommits(
                repositoryId = repositoryId,
                branchName = branchName,
            )
            commits.forEachIndexed { index, commit ->
                queries.upsertCommit(
                    sha = commit.sha,
                    repositoryId = repositoryId,
                    firstParentSha = commit.firstParentSha,
                    authorName = commit.authorName,
                    authorEmail = commit.authorEmail,
                    message = commit.message,
                    committedAt = commit.committedAt,
                    parentCount = commit.parentCount.toLong(),
                )
                queries.insertBranchCommit(
                    repositoryId = repositoryId,
                    branchName = branchName,
                    sha = commit.sha,
                    position = index.toLong(),
                )
            }
        }
    }

    override suspend fun getCommits(
        repositoryId: Long,
        branchName: String,
    ): Result<List<GithubCommitSummary>, DataError.Local> = localResult {
        queries.selectCommitsForBranch(
            repositoryId = repositoryId,
            branchName = branchName,
        ).executeAsList()
            .map {
                GithubCommitSummary(
                    sha = it.sha,
                    firstParentSha = it.firstParentSha,
                    authorName = it.authorName,
                    authorEmail = it.authorEmail,
                    message = it.message,
                    committedAt = it.committedAt,
                    additions = it.additions.toInt(),
                    deletions = it.deletions.toInt(),
                    parentCount = it.parentCount.toInt(),
                )
            }
    }

    override suspend fun upsertCommitDetails(
        repositoryId: Long,
        commit: GithubCommitDetails,
    ): EmptyResult<DataError.Local> = localResult {
        database.transaction {
            queries.upsertCommit(
                sha = commit.sha,
                repositoryId = repositoryId,
                firstParentSha = commit.firstParentSha,
                authorName = commit.authorName,
                authorEmail = commit.authorEmail,
                message = commit.message,
                committedAt = commit.committedAt,
                parentCount = commit.parentCount.toLong(),
            )
            queries.deleteFilesForCommit(commitSha = commit.sha)
            commit.files.forEach { file ->
                queries.upsertCommitFile(
                    commitSha = commit.sha,
                    path = file.path,
                    previousPath = file.previousPath,
                    status = file.status,
                    additions = file.additions.toLong(),
                    deletions = file.deletions.toLong(),
                    patchId = file.patch?.let { file.path },
                    oldBlobSha = file.oldBlobSha,
                    newBlobSha = file.newBlobSha,
                )
            }
        }
    }

    override suspend fun getCommitDetails(sha: String): Result<GithubCommitDetails, DataError.Local> = localResult {
        val commit = queries.selectCommitBySha(sha = sha).executeAsOneOrNull()
            ?: return Result.Error(DataError.Local.NOT_FOUND)
        GithubCommitDetails(
            sha = commit.sha,
            firstParentSha = commit.firstParentSha,
            authorName = commit.authorName,
            authorEmail = commit.authorEmail,
            message = commit.message,
            committedAt = commit.committedAt,
            parentCount = commit.parentCount.toInt(),
            files = queries.selectFilesForCommit(commitSha = sha)
                .executeAsList()
                .map { it.toGithubCommitFile() },
        )
    }

    override suspend fun upsertDownloadJob(job: BranchDownloadJob): EmptyResult<DataError.Local> = localResult {
        queries.upsertDownloadJob(
            id = job.id,
            repositoryId = job.repositoryId,
            branchName = job.branchName,
            status = job.status.name,
            progress = job.progress,
            errorMessage = job.errorMessage,
        )
    }

    override suspend fun getDownloadJob(
        repositoryId: Long,
        branchName: String,
    ): Result<BranchDownloadJob?, DataError.Local> = localResult {
        queries.selectDownloadJobForBranch(
            repositoryId = repositoryId,
            branchName = branchName,
        ).executeAsOneOrNull()
            ?.toBranchDownloadJob()
    }

    override suspend fun getDownloadedRepositoryIds(): Result<Set<Long>, DataError.Local> = localResult {
        queries.selectDownloadedRepositoryIds().executeAsList().toSet()
    }

    private inline fun <T> localResult(block: () -> T): Result<T, DataError.Local> = try {
        Result.Success(block())
    } catch (e: Exception) {
        // Keep the root cause in logcat; the enum alone reads as an opaque
        // "local storage error" in the UI.
        println("OfflineGit: database operation failed: ${e.stackTraceToString()}")
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private fun Account.toGithubAccount(): GithubAccount = GithubAccount(
        id = id,
        login = login,
        avatarUrl = avatarUrl,
    )

    private fun Repository.toGithubRepository(): GithubRepository = GithubRepository(
        id = id,
        owner = owner,
        name = name,
        defaultBranch = defaultBranch,
        isPrivate = isPrivate == 1L,
        updatedAt = updatedAt,
    )

    private fun Branch.toGithubBranch(): GithubBranch = GithubBranch(
        name = name,
        headSha = headSha,
    )

    private fun CommitFile.toGithubCommitFile(): GithubCommitFile = GithubCommitFile(
        path = path,
        previousPath = previousPath,
        status = status,
        additions = additions.toInt(),
        deletions = deletions.toInt(),
        blobSha = newBlobSha,
        patch = null,
        oldBlobSha = oldBlobSha,
        newBlobSha = newBlobSha,
    )

    private fun DownloadJob.toBranchDownloadJob(): BranchDownloadJob = BranchDownloadJob(
        id = id,
        repositoryId = repositoryId,
        branchName = branchName,
        status = BranchDownloadJobStatus.valueOf(status),
        progress = progress,
        errorMessage = errorMessage,
    )
}
