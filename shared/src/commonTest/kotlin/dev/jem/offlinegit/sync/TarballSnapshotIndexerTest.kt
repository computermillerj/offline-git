package dev.jem.offlinegit.sync

import dev.jem.offlinegit.domain.Result
import okio.Buffer
import okio.GzipSink
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TarballSnapshotIndexerTest {

    private val indexer = TarballSnapshotIndexer()

    @Test
    fun `indexes regular files and computes git blob sha`() {
        val tar = tarOf(
            entry(path = "repo-root/README.md", content = "hello\n".encodeToByteArray()),
            entry(path = "repo-root/src/App.kt", content = "fun main() {}\n".encodeToByteArray()),
        )

        val blobs = assertIs<Result.Success<List<SnapshotBlob>>>(indexer.index(tar)).data

        assertEquals(listOf("README.md", "src/App.kt"), blobs.map { it.path })
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", blobs.first().sha)
        assertContentEquals("hello\n".encodeToByteArray(), blobs.first().bytes)
        assertTrue(blobs.all { it.isText })
    }

    @Test
    fun `indexes gzipped tarball`() {
        val tar = tarOf(
            entry(path = "repo-root/README.md", content = "hello\n".encodeToByteArray()),
        ).gzip()

        val blobs = assertIs<Result.Success<List<SnapshotBlob>>>(indexer.index(tar)).data

        assertEquals(listOf("README.md"), blobs.map { it.path })
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", blobs.single().sha)
    }

    @Test
    fun `marks binary files and skips directories`() {
        val tar = tarOf(
            entry(path = "repo-root/src", content = byteArrayOf(), type = '5'),
            entry(path = "repo-root/image.bin", content = byteArrayOf(1, 0, 2)),
        )

        val blobs = assertIs<Result.Success<List<SnapshotBlob>>>(indexer.index(tar)).data

        assertEquals(listOf("image.bin"), blobs.map { it.path })
        assertFalse(blobs.single().isText)
    }

    @Test
    fun `skips files larger than the size cap`() {
        val cappedIndexer = TarballSnapshotIndexer(maxBlobBytes = 8)
        val tar = tarOf(
            entry(path = "repo-root/small.txt", content = "hi\n".encodeToByteArray()),
            entry(path = "repo-root/big.txt", content = "0123456789".encodeToByteArray()),
        )

        val blobs = assertIs<Result.Success<List<SnapshotBlob>>>(cappedIndexer.index(tar)).data

        assertEquals(listOf("small.txt"), blobs.map { it.path })
    }

    private fun tarOf(vararg entries: ByteArray): ByteArray = entries.fold(byteArrayOf()) { acc, entry ->
        acc + entry
    } + ByteArray(BLOCK_SIZE * 2)

    private fun entry(
        path: String,
        content: ByteArray,
        type: Char = '0',
    ): ByteArray {
        val header = ByteArray(BLOCK_SIZE)
        header.writeText(offset = 0, length = 100, value = path)
        header.writeText(offset = 100, length = 8, value = "0000777")
        header.writeText(offset = 108, length = 8, value = "0000000")
        header.writeText(offset = 116, length = 8, value = "0000000")
        header.writeText(offset = 124, length = 12, value = content.size.toString(radix = 8).padStart(length = 11, padChar = '0'))
        header.writeText(offset = 136, length = 12, value = "00000000000")
        header[156] = type.code.toByte()
        val padding = ByteArray((BLOCK_SIZE - content.size % BLOCK_SIZE) % BLOCK_SIZE)
        return header + content + padding
    }

    private fun ByteArray.writeText(
        offset: Int,
        length: Int,
        value: String,
    ) {
        value.encodeToByteArray()
            .take(length)
            .forEachIndexed { index, byte -> this[offset + index] = byte }
    }

    private fun ByteArray.gzip(): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { sink ->
            sink.write(this)
        }
        return buffer.readByteArray()
    }

    private companion object {
        const val BLOCK_SIZE = 512
    }
}
