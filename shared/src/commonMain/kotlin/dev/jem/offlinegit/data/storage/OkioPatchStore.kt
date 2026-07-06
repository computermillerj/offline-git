package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.Result
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path

class OkioPatchStore(
    private val root: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : PatchStore {

    override suspend fun writePatch(
        commitSha: String,
        path: String,
        patch: String,
    ): EmptyResult<DataError.Local> = try {
        val cachePath = patchPath(commitSha = commitSha, path = path)
        fileSystem.createDirectories(cachePath.parent!!)
        fileSystem.write(cachePath) {
            writeUtf8(patch)
        }
        Result.Success(Unit)
    } catch (e: IOException) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun readPatch(
        commitSha: String,
        path: String,
    ): Result<String, DataError.Local> = try {
        val cachePath = patchPath(commitSha = commitSha, path = path)
        if (!fileSystem.exists(cachePath)) {
            return Result.Error(DataError.Local.NOT_FOUND)
        }
        Result.Success(
            fileSystem.read(cachePath) {
                readUtf8()
            },
        )
    } catch (e: IOException) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun patchPath(
        commitSha: String,
        path: String,
    ): Path = root
        .resolve("patches")
        .resolve(commitSha)
        .resolve("${path.toCacheFileName()}.patch")

    private fun String.toCacheFileName(): String = encodeToByteArray().joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(length = 2, padChar = '0')
    }
}
