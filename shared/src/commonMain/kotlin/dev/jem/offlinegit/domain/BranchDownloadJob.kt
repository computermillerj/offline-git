package dev.jem.offlinegit.domain

data class BranchDownloadJob(
    val id: String,
    val repositoryId: Long,
    val branchName: String,
    val status: BranchDownloadJobStatus,
    val progress: Double,
    val errorMessage: String?,
)

enum class BranchDownloadJobStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}
