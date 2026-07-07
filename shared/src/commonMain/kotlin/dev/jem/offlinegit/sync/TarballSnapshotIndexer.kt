package dev.jem.offlinegit.sync

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.Result
import okio.Buffer
import okio.GzipSource
import okio.buffer

class TarballSnapshotIndexer(
    private val maxBlobBytes: Long = DEFAULT_MAX_BLOB_BYTES,
) {
    fun index(tarBytes: ByteArray): Result<List<SnapshotBlob>, DataError.Local> = try {
        val bytes = tarBytes.gunzipIfNeeded()
        val blobs = mutableListOf<SnapshotBlob>()
        var offset = 0
        while (offset + BLOCK_SIZE <= bytes.size) {
            val header = bytes.copyOfRange(offset, offset + BLOCK_SIZE)
            if (header.all { it == 0.toByte() }) {
                return Result.Success(blobs)
            }

            val path = header.path()?.withoutRootDirectory()
            val size = header.octalLong(start = SIZE_OFFSET, length = SIZE_LENGTH)
            val type = header[TYPE_OFFSET].toInt().toChar()
            val contentOffset = offset + BLOCK_SIZE
            val nextOffset = contentOffset + size.roundUpToBlock()
            // Skip oversized files: their content isn't worth caching for text
            // diffs, and materializing + hashing a huge blob OOMs the app.
            if (path != null && type.isRegularFile() && size in 0..(bytes.size - contentOffset).toLong() && size <= maxBlobBytes) {
                val content = bytes.copyOfRange(contentOffset, contentOffset + size.toInt())
                blobs += SnapshotBlob(
                    path = path,
                    sha = content.gitBlobSha(),
                    bytes = content,
                    isText = content.none { it == 0.toByte() },
                )
            }
            offset = nextOffset.toInt()
        }
        Result.Success(blobs)
    } catch (e: Exception) {
        // Keep the root cause in logcat; the enum alone reads as an opaque
        // "local storage error" in the UI.
        println("OfflineGit: tarball indexing failed: ${e.stackTraceToString()}")
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun ByteArray.gunzipIfNeeded(): ByteArray {
        if (size < 2 || this[0] != GZIP_ID_1 || this[1] != GZIP_ID_2) {
            return this
        }
        val source = GzipSource(Buffer().write(this))
        return source.buffer().use { it.readByteArray() }
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
        // Stream the header and content through one Buffer instead of allocating
        // a `header + content` copy (and then a ByteString copy) — that double
        // allocation OOMs on large blobs.
        val buffer = Buffer()
        buffer.write(header)
        buffer.write(this)
        return buffer.sha1().hex()
    }

    private companion object {
        const val DEFAULT_MAX_BLOB_BYTES = 5L * 1024 * 1024
        const val BLOCK_SIZE = 512
        const val NAME_OFFSET = 0
        const val NAME_LENGTH = 100
        const val SIZE_OFFSET = 124
        const val SIZE_LENGTH = 12
        const val TYPE_OFFSET = 156
        const val PREFIX_OFFSET = 345
        const val PREFIX_LENGTH = 155
        const val GZIP_ID_1: Byte = 0x1f
        const val GZIP_ID_2: Byte = -0x75
    }
}

data class SnapshotBlob(
    val path: String,
    val sha: String,
    val bytes: ByteArray,
    val isText: Boolean,
)
