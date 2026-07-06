package dev.jem.offlinegit.data.github

import dev.jem.offlinegit.domain.TokenStore

class EnvironmentTokenStore(
    private val variableName: String = "GITHUB_TOKEN",
) : TokenStore {
    override suspend fun getToken(): String? = System.getenv(variableName)
}
