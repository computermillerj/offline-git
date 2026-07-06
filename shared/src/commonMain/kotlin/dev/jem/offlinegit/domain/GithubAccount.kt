package dev.jem.offlinegit.domain

data class GithubAccount(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
)
