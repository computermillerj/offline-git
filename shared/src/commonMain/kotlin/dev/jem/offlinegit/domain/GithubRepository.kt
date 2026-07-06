package dev.jem.offlinegit.domain

data class GithubRepository(
    val id: Long,
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val updatedAt: String?,
)
