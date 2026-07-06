package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GithubPatchDiffProviderTest {

    @Test
    fun `reads cached patch and parses diff`() = runTest {
        val provider = GithubPatchDiffProvider(
            patchStore = FakePatchStore(
                patches = mapOf("commit-sha:README.md" to """
                    @@ -1 +1 @@
                    -old
                    +new
                """.trimIndent()),
            ),
        )

        val result = provider.getDiff(
            commitSha = "commit-sha",
            file = aFile(),
        )

        val diff = assertIs<Result.Success<DiffFile>>(result).data
        assertEquals("README.md", diff.path)
        assertEquals(3, diff.hunks.single().lines.size)
    }

    @Test
    fun `maps missing patch to storage error`() = runTest {
        val provider = GithubPatchDiffProvider(patchStore = FakePatchStore(patches = emptyMap()))

        val result = provider.getDiff(
            commitSha = "commit-sha",
            file = aFile(),
        )

        assertEquals(Result.Error(DiffError.Storage(DataError.Local.NOT_FOUND)), result)
    }

    private fun aFile(): GithubCommitFile = GithubCommitFile(
        path = "README.md",
        previousPath = null,
        status = "modified",
        additions = 1,
        deletions = 1,
        blobSha = "blob-sha",
        patch = null,
    )

    private class FakePatchStore(
        private val patches: Map<String, String>,
    ) : PatchStore {
        override suspend fun writePatch(
            commitSha: String,
            path: String,
            patch: String,
        ): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun readPatch(
            commitSha: String,
            path: String,
        ): Result<String, DataError.Local> = patches["$commitSha:$path"]
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Local.NOT_FOUND)
    }
}
