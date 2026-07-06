package dev.jem.offlinegit.sync

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.Result
import okio.ByteString.Companion.toByteString

class TarballSnapshotIndexer {
    fun index(tarBytes: ByteArray): Result<List<SnapshotBlob>, DataError.Local> = try {
        val blobs = mutableListOf<SnapshotBlob>()
        var offset = 0
        while (offset + BLOCK_SIZE <= tarBytes.size) {
            val header = tarBytes.copyOfRange(offset, offset + BLOCK_SIZE)
            if (header.all { it == 0.toByte() }) {
                return Result.Success(blobs)
            }

            val path = header.path()?.withoutRootDirectory()
            val size = header.octalLong(start = SIZE_OFFSET, length = SIZE_LENGTH)
            val type = header[TYPE_OFFSET].toInt().toChar()
            val contentOffset = offset + BLOCK_SIZE
            val nextOffset = contentOffset + size.roundUpToBlock()
            if (path != null && type.isRegularFile() && size <= tarBytes.size - contentOffset) {
                val bytes = tarBytes.copyOfRange(contentOffset, contentOffset + size.toInt())
                blobs += SnapshotBlob(
                    path = path,
                    sha = bytes.gitBlobSha(),
                    bytes = bytes,
                    isText = bytes.none { it == 0.toByte() },
                )
            }
            offset = nextOffset.toInt()
        }
        Result.Success(blobs)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun ByteArray.path(): String? {
        val name = text(start = NAME_OFFSET, length = NAME_LENGTH)
        val prefix = text(start = PREFIX_OFFSET, length = PREFIX_LENGTH)
        return when {
            name.isBlank() -> null
            prefix.isBlank() -> name
            else -> "$prefix/$name"
        }
    }

    private fun ByteArray.text(
        start: Int,
        length: Int,
    ): String = copyOfRange(start, start + length)
        .takeWhile { it != 0.toByte() }
        .toByteArray()
        .decodeToString()

    private fun ByteArray.octalLong(
        start: Int,
        length: Int,
    ): Long = text(start = start, length = length)
        .trim()
        .ifEmpty { "0" }
        .toLong(radix = 8)

    private fun String.withoutRootDirectory(): String? = substringAfter('/', missingDelimiterValue = "")
        .ifBlank { null }

    private fun Char.isRegularFile(): Boolean = this == '0' || this == '\u0000'

    private fun Long.roundUpToBlock(): Long = ((this + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE

    private fun ByteArray.gitBlobSha(): String {
        val header = "blob $size\u0000".encodeToByteArray()
        return (header + this).toByteString().sha1().hex()
    }

    private companion object {
        const val BLOCK_SIZE = 512
        const val NAME_OFFSET = 0
        const val NAME_LENGTH = 100
        const val SIZE_OFFSET = 124
        const val SIZE_LENGTH = 12
        const val TYPE_OFFSET = 156
        const val PREFIX_OFFSET = 345
        const val PREFIX_LENGTH = 155
    }
}

data class SnapshotBlob(
    val path: String,
    val sha: String,
    val bytes: ByteArray,
    val isText: Boolean,
)
