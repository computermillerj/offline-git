package dev.jem.offlinegit.data.github.dto

import dev.jem.offlinegit.domain.GithubAccount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubAccountDto(
    val id: Long,
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

fun GithubAccountDto.toGithubAccount(): GithubAccount = GithubAccount(
    id = id,
    login = login,
    avatarUrl = avatarUrl,
)
