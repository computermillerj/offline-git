package dev.jem.offlinegit.domain

import kotlinx.coroutines.flow.Flow

interface BranchDownloader {
    fun downloadBranch(
        repository: GithubRepository,
        branch: GithubBranch,
        commitLimit: Int = 100,
    ): Flow<BranchDownloadProgress>
}

sealed interface BranchDownloadProgress {
    data object Started : BranchDownloadProgress

    data class CommitsStored(val count: Int) : BranchDownloadProgress

    data class CommitStored(
        val sha: String,
        val completed: Int,
        val total: Int,
    ) : BranchDownloadProgress

    data object Completed : BranchDownloadProgress

    data class Failed(val error: DataError) : BranchDownloadProgress
}
