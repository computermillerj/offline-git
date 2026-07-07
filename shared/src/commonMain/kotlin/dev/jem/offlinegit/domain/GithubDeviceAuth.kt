package dev.jem.offlinegit.domain

data class GithubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

data class GithubAccessToken(
    val accessToken: String,
)

sealed interface GithubDeviceAuthError : Error {
    data object AuthorizationPending : GithubDeviceAuthError
    data object SlowDown : GithubDeviceAuthError
    data object ExpiredToken : GithubDeviceAuthError
    data object AccessDenied : GithubDeviceAuthError
    data class Network(val error: DataError.Network) : GithubDeviceAuthError
}

interface GithubDeviceAuthDataSource {
    suspend fun createDeviceCode(clientId: String): Result<GithubDeviceCode, DataError.Network>

    suspend fun pollAccessToken(
        clientId: String,
        deviceCode: String,
    ): Result<GithubAccessToken, GithubDeviceAuthError>
}
