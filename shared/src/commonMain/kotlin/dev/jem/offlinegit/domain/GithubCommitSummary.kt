package dev.jem.offlinegit.domain

data class GithubCommitSummary(
    val sha: String,
    val firstParentSha: String?,
    val authorName: String?,
    val authorEmail: String?,
    val message: String,
    val committedAt: String?,
)
