package dev.jem.offlinegit.domain

interface PatchStore {
    suspend fun writePatch(
        commitSha: String,
        path: String,
        patch: String,
    ): EmptyResult<DataError.Local>

    suspend fun readPatch(
        commitSha: String,
        path: String,
    ): Result<String, DataError.Local>
}
