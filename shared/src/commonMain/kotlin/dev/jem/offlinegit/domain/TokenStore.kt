package dev.jem.offlinegit.domain

interface TokenStore {
    suspend fun getToken(): String?
}
