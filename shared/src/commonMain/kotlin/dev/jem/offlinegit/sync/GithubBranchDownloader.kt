package dev.jem.offlinegit.sync

import dev.jem.offlinegit.domain.BranchDownloadProgress
import dev.jem.offlinegit.domain.BranchDownloader
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GithubBranchDownloader(
    private val remoteDataSource: GithubRemoteDataSource,
    private val repositoryStore: RepositoryStore,
    private val patchStore: PatchStore,
) : BranchDownloader {

    override fun downloadBranch(
        repository: GithubRepository,
        branch: GithubBranch,
        commitLimit: Int,
    ): Flow<BranchDownloadProgress> = flow {
        emit(BranchDownloadProgress.Started)

        when (val result = repositoryStore.upsertRepositories(listOf(repository))) {
            is Result.Error -> {
                emit(BranchDownloadProgress.Failed(result.error))
                return@flow
            }
            is Result.Success -> Unit
        }
        when (
            val result = repositoryStore.upsertBranches(
                repositoryId = repository.id,
                branches = listOf(branch),
            )
        ) {
            is Result.Error -> {
                emit(BranchDownloadProgress.Failed(result.error))
                return@flow
            }
            is Result.Success -> Unit
        }

        val commits = when (
            val result = remoteDataSource.getCommits(
                owner = repository.owner,
                repository = repository.name,
                branch = branch.name,
                limit = commitLimit,
            )
        ) {
            is Result.Error -> {
                emit(BranchDownloadProgress.Failed(result.error))
                return@flow
            }
            is Result.Success -> result.data
        }

        when (
            val result = repositoryStore.upsertCommits(
                repositoryId = repository.id,
                branchName = branch.name,
                commits = commits,
            )
        ) {
            is Result.Error -> {
                emit(BranchDownloadProgress.Failed(result.error))
                return@flow
            }
            is Result.Success -> Unit
        }
        emit(BranchDownloadProgress.CommitsStored(commits.size))

        commits.forEachIndexed { index, commit ->
            val details = when (
                val result = remoteDataSource.getCommitDetails(
                    owner = repository.owner,
                    repository = repository.name,
                    sha = commit.sha,
                )
            ) {
                is Result.Error -> {
                    emit(BranchDownloadProgress.Failed(result.error))
                    return@flow
                }
                is Result.Success -> result.data
            }

            when (val result = storeCommitDetails(repositoryId = repository.id, details = details)) {
                is Result.Error -> {
                    emit(BranchDownloadProgress.Failed(result.error))
                    return@flow
                }
                is Result.Success -> Unit
            }
            emit(
                BranchDownloadProgress.CommitStored(
                    sha = commit.sha,
                    completed = index + 1,
                    total = commits.size,
                ),
            )
        }

        emit(BranchDownloadProgress.Completed)
    }

    private suspend fun storeCommitDetails(
        repositoryId: Long,
        details: GithubCommitDetails,
    ): Result<Unit, DataError.Local> {
        repositoryStore.upsertCommitDetails(repositoryId = repositoryId, commit = details).onError { error ->
            return Result.Error(error)
        }
        details.files.forEach { file ->
            val patch = file.patch ?: return@forEach
            patchStore.writePatch(
                commitSha = details.sha,
                path = file.path,
                patch = patch,
            ).onError { error ->
                return Result.Error(error)
            }
        }
        return Result.Success(Unit)
    }

    private inline fun <T> Result<T, DataError.Local>.onError(
        block: (DataError.Local) -> Unit,
    ): Result<T, DataError.Local> {
        if (this is Result.Error) {
            block(error)
        }
        return this
    }
}
