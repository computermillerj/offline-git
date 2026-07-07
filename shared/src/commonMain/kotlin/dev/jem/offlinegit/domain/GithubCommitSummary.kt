package dev.jem.offlinegit.domain

data class GithubCommitSummary(
    val sha: String,
    val firstParentSha: String?,
    val authorName: String?,
    val authorEmail: String?,
    val message: String,
    val committedAt: String?,
    val additions: Int = 0,
    val deletions: Int = 0,
    val parentCount: Int = 1,
) {
    val isMerge: Boolean get() = parentCount > 1
}
