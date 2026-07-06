package dev.jem.offlinegit.data.github.dto

import dev.jem.offlinegit.domain.GithubRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRepositoryDto(
    val id: Long,
    val name: String,
    val owner: GithubRepositoryOwnerDto,
    @SerialName("default_branch") val defaultBranch: String,
    @SerialName("private") val isPrivate: Boolean,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GithubRepositoryOwnerDto(
    val login: String,
)

fun GithubRepositoryDto.toGithubRepository(): GithubRepository = GithubRepository(
    id = id,
    owner = owner.login,
    name = name,
    defaultBranch = defaultBranch,
    isPrivate = isPrivate,
    updatedAt = updatedAt,
)
