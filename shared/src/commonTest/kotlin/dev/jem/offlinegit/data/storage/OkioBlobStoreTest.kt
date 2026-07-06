package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OkioBlobStoreTest {

    @Test
    fun `writes and reads blob bytes`() = runTest {
        val root = testRoot()
        val store = OkioBlobStore(root = root)
        val sha = "abcdef123456"
        val bytes = "hello".encodeToByteArray()

        try {
            assertEquals(Result.Success(false), store.hasBlob(sha))
            assertEquals(Result.Success(Unit), store.writeBlob(sha = sha, bytes = bytes))
            assertEquals(Result.Success(true), store.hasBlob(sha))

            val result = store.readBlob(sha)

            assertContentEquals(bytes, assertIs<Result.Success<ByteArray>>(result).data)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    @Test
    fun `missing blob returns not found`() = runTest {
        val root = testRoot()
        val store = OkioBlobStore(root = root)

        try {
            assertEquals(Result.Error(DataError.Local.NOT_FOUND), store.readBlob("missing"))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    private fun testRoot(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        .resolve("offline-git-blob-test-${Random.nextLong()}")
}
