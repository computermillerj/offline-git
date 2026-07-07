package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.Result
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path

class OkioBlobStore(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : BlobStore {

    override suspend fun hasBlob(sha: String): Result<Boolean, DataError.Local> = try {
        Result.Success(fileSystem.exists(blobPath(sha)))
    } catch (e: IOException) {
        Result.Error(e.toLocalError("hasBlob($sha)"))
    }

    override suspend fun writeBlob(
        sha: String,
        bytes: ByteArray,
    ): EmptyResult<DataError.Local> = try {
        val path = blobPath(sha)
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) {
            write(bytes)
        }
        Result.Success(Unit)
    } catch (e: IOException) {
        Result.Error(e.toLocalError("writeBlob($sha)"))
    }

    override suspend fun readBlob(sha: String): Result<ByteArray, DataError.Local> = try {
        val path = blobPath(sha)
        if (!fileSystem.exists(path)) {
            return Result.Error(DataError.Local.NOT_FOUND)
        }
        Result.Success(
            fileSystem.read(path) {
                readByteArray()
            },
        )
    } catch (e: IOException) {
        Result.Error(e.toLocalError("readBlob($sha)"))
    }

    private fun blobPath(sha: String): Path = root
        .resolve("blobs")
        .resolve(sha.take(2))
        .resolve(sha)
}
