package dev.jem.offlinegit.domain

interface BlobStore {
    suspend fun hasBlob(sha: String): Result<Boolean, DataError.Local>

    suspend fun writeBlob(
        sha: String,
        bytes: ByteArray,
    ): EmptyResult<DataError.Local>

    suspend fun readBlob(sha: String): Result<ByteArray, DataError.Local>
}
