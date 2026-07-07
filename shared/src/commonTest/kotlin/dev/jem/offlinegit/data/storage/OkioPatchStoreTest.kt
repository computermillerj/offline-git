package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class OkioPatchStoreTest {

    @Test
    fun `writes and reads patch text`() = runTest {
        val root = testRoot()
        val store = OkioPatchStore(root = root)
        val patch = """
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        try {
            assertEquals(
                expected = Result.Success(Unit),
                actual = store.writePatch(
                    commitSha = "commit-sha",
                    path = "src/main/kotlin/App.kt",
                    patch = patch,
                ),
            )
            assertEquals(
                expected = Result.Success(patch),
                actual = store.readPatch(
                    commitSha = "commit-sha",
                    path = "src/main/kotlin/App.kt",
                ),
            )
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    @Test
    fun `writes and reads patch for a very long repo path`() = runTest {
        val root = testRoot()
        val store = OkioPatchStore(root = root)
        // Long enough that hex-encoding it as the cache file name would exceed
        // the 255-byte filename limit.
        val longPath = "very/deeply/nested/".repeat(10) + "GeneratedDatabaseImplementation.kt"
        val patch = "@@ -1 +1 @@"

        try {
            assertEquals(
                expected = Result.Success(Unit),
                actual = store.writePatch(commitSha = "commit-sha", path = longPath, patch = patch),
            )
            assertEquals(
                expected = Result.Success(patch),
                actual = store.readPatch(commitSha = "commit-sha", path = longPath),
            )
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    @Test
    fun `missing patch returns not found`() = runTest {
        val root = testRoot()
        val store = OkioPatchStore(root = root)

        try {
            assertEquals(
                expected = Result.Error(DataError.Local.NOT_FOUND),
                actual = store.readPatch(commitSha = "missing", path = "README.md"),
            )
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    private fun testRoot(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        .resolve("offline-git-patch-test-${Random.nextLong()}")
}
