package dev.jem.offlinegit.domain

interface TokenStore {
    suspend fun getToken(): String?
}

interface WritableTokenStore : TokenStore {
    suspend fun saveToken(token: String)

    suspend fun clearToken()
}
