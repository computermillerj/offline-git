package dev.jem.offlinegit.domain

/** Supplies the token for one session; implementations bind it to an account. */
interface TokenStore {
    suspend fun getToken(): String?
}

/** Persistent token storage, one entry per signed-in account. */
interface AccountTokenStore {
    suspend fun getToken(accountId: Long): String?

    suspend fun saveToken(
        accountId: Long,
        token: String,
    )

    suspend fun clearToken(accountId: Long)

    /**
     * Token saved by the pre-multi-account build, if any. Kept until
     * [clearLegacyToken] so a failed migration (e.g. offline) can retry.
     */
    suspend fun getLegacyToken(): String? = null

    suspend fun clearLegacyToken() {}
}
