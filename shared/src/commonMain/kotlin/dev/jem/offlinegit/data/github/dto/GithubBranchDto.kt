package dev.jem.offlinegit.data.github.dto

import dev.jem.offlinegit.domain.GithubBranch
import kotlinx.serialization.Serializable

@Serializable
data class GithubBranchDto(
    val name: String,
    val commit: GithubBranchCommitDto,
)

@Serializable
data class GithubBranchCommitDto(
    val sha: String,
)

fun GithubBranchDto.toGithubBranch(): GithubBranch = GithubBranch(
    name = name,
    headSha = commit.sha,
)
