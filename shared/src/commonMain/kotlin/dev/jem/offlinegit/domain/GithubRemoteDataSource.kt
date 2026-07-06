package dev.jem.offlinegit.domain

interface GithubRemoteDataSource {
    suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network>
}
