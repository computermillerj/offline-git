package dev.jem.offlinegit.domain

data class GithubCommitDetails(
    val sha: String,
    val firstParentSha: String?,
    val authorName: String?,
    val authorEmail: String?,
    val message: String,
    val committedAt: String?,
    val files: List<GithubCommitFile>,
)

data class GithubCommitFile(
    val path: String,
    val previousPath: String?,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val blobSha: String?,
    val patch: String?,
)
