package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalBlobDiffProviderTest {

    @Test
    fun `builds unified diff from cached old and new blobs`() = runTest {
        val provider = LocalBlobDiffProvider(
            blobStore = FakeBlobStore(
                blobs = mapOf(
                    "old" to "one\ntwo\nthree\n".encodeToByteArray(),
                    "new" to "one\nchanged\nthree\nfour\n".encodeToByteArray(),
                ),
            ),
        )

        val result = provider.getDiff(
            commitSha = "commit-sha",
            file = GithubCommitFile(
                path = "README.md",
                previousPath = null,
                status = "modified",
                additions = 2,
                deletions = 1,
                blobSha = "new",
                patch = null,
                oldBlobSha = "old",
                newBlobSha = "new",
            ),
        )

        val diff = assertIs<Result.Success<DiffFile>>(result).data
        assertEquals("README.md", diff.path)
        assertEquals("@@ -1,3 +1,4 @@", diff.hunks.single().lines.first().text)
        assertEquals(
            expected = listOf(
                DiffLineType.HUNK_HEADER,
                DiffLineType.CONTEXT,
                DiffLineType.DELETION,
                DiffLineType.ADDITION,
                DiffLineType.CONTEXT,
                DiffLineType.ADDITION,
            ),
            actual = diff.hunks.single().lines.map { it.type },
        )
    }

    @Test
    fun `returns storage error when blob sha is missing`() = runTest {
        val provider = LocalBlobDiffProvider(blobStore = FakeBlobStore(blobs = emptyMap()))

        val result = provider.getDiff(
            commitSha = "commit-sha",
            file = GithubCommitFile(
                path = "README.md",
                previousPath = null,
                status = "modified",
                additions = 1,
                deletions = 1,
                blobSha = null,
                patch = null,
            ),
        )

        assertEquals(Result.Error(DiffError.Storage(DataError.Local.NOT_FOUND)), result)
    }

    private class FakeBlobStore(
        private val blobs: Map<String, ByteArray>,
    ) : BlobStore {
        override suspend fun hasBlob(sha: String): Result<Boolean, DataError.Local> = Result.Success(blobs.containsKey(sha))

        override suspend fun writeBlob(
            sha: String,
            bytes: ByteArray,
        ): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun readBlob(sha: String): Result<ByteArray, DataError.Local> = blobs[sha]
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Local.NOT_FOUND)
    }
}
