package dev.jem.offlinegit.data.github.dto

import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubCommitDto(
    val sha: String,
    val commit: GithubCommitMetadataDto,
    val parents: List<GithubCommitParentDto> = emptyList(),
    val files: List<GithubCommitFileDto> = emptyList(),
)

@Serializable
data class GithubCommitMetadataDto(
    val author: GithubCommitPersonDto? = null,
    val committer: GithubCommitPersonDto? = null,
    val message: String = "",
)

@Serializable
data class GithubCommitPersonDto(
    val name: String? = null,
    val email: String? = null,
    val date: String? = null,
)

@Serializable
data class GithubCommitParentDto(
    val sha: String,
)

@Serializable
data class GithubCommitFileDto(
    val sha: String? = null,
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    @SerialName("previous_filename") val previousFilename: String? = null,
    val patch: String? = null,
)

fun GithubCommitDto.toGithubCommitSummary(): GithubCommitSummary = GithubCommitSummary(
    sha = sha,
    firstParentSha = parents.firstOrNull()?.sha,
    authorName = commit.author?.name ?: commit.committer?.name,
    authorEmail = commit.author?.email ?: commit.committer?.email,
    message = commit.message,
    committedAt = commit.author?.date ?: commit.committer?.date,
)

fun GithubCommitDto.toGithubCommitDetails(): GithubCommitDetails {
    val summary = toGithubCommitSummary()
    return GithubCommitDetails(
        sha = summary.sha,
        firstParentSha = summary.firstParentSha,
        authorName = summary.authorName,
        authorEmail = summary.authorEmail,
        message = summary.message,
        committedAt = summary.committedAt,
        files = files.map { it.toGithubCommitFile() },
    )
}

fun GithubCommitFileDto.toGithubCommitFile(): GithubCommitFile = GithubCommitFile(
    path = filename,
    previousPath = previousFilename,
    status = status,
    additions = additions,
    deletions = deletions,
    blobSha = sha,
    patch = patch,
)
