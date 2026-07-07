package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffSource
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
    fun `tags the diff as a local blob source`() = runTest {
        val provider = LocalBlobDiffProvider(
            blobStore = FakeBlobStore(
                blobs = mapOf(
                    "old" to "one\n".encodeToByteArray(),
                    "new" to "two\n".encodeToByteArray(),
                ),
            ),
        )

        val result = provider.getDiff(commitSha = "commit-sha", file = modifiedFile())

        assertEquals(DiffSource.LOCAL_BLOB, assertIs<Result.Success<DiffFile>>(result).data.source)
    }

    @Test
    fun `collapses unchanged runs into separate hunks`() = runTest {
        // Twenty identical lines with only the first and last changed: the
        // middle should collapse away, leaving two small hunks with context.
        val old = (1..20).joinToString(separator = "\n", postfix = "\n") { "line$it" }
        val new = old.replace("line1\n", "LINE1\n").replace("line20\n", "LINE20\n")
        val provider = LocalBlobDiffProvider(
            blobStore = FakeBlobStore(
                blobs = mapOf(
                    "old" to old.encodeToByteArray(),
                    "new" to new.encodeToByteArray(),
                ),
            ),
        )

        val diff = assertIs<Result.Success<DiffFile>>(
            provider.getDiff(commitSha = "commit-sha", file = modifiedFile()),
        ).data

        assertEquals(2, diff.hunks.size)
        val allText = diff.hunks.flatMap { it.lines }.map { it.text }
        // The untouched middle (e.g. line10) is not rendered.
        assertEquals(false, allText.any { it == "line10" })
        assertEquals(true, allText.any { it == "LINE1" })
        assertEquals(true, allText.any { it == "LINE20" })
    }

    @Test
    fun `degrades to full replacement when the LCS matrix would be too large`() = runTest {
        // 3000 x 3000 = 9M cells exceeds the LCS cap, so even a one-line change
        // renders as a whole-file replacement (all deletions, then all additions)
        // rather than allocating the matrix.
        val old = (1..3000).joinToString(separator = "\n", postfix = "\n") { "line$it" }
        val new = old.replaceFirst("line1500\n", "CHANGED\n")
        val provider = LocalBlobDiffProvider(
            blobStore = FakeBlobStore(
                blobs = mapOf(
                    "old" to old.encodeToByteArray(),
                    "new" to new.encodeToByteArray(),
                ),
            ),
        )

        val diff = assertIs<Result.Success<DiffFile>>(
            provider.getDiff(commitSha = "commit-sha", file = modifiedFile()),
        ).data

        val hunk = diff.hunks.single()
        val content = hunk.lines.filter { it.type != DiffLineType.HUNK_HEADER }
        assertEquals(3000, content.count { it.type == DiffLineType.DELETION })
        assertEquals(3000, content.count { it.type == DiffLineType.ADDITION })
        assertEquals(DiffLineType.DELETION, content.first().type)
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

    private fun modifiedFile(): GithubCommitFile = GithubCommitFile(
        path = "README.md",
        previousPath = null,
        status = "modified",
        additions = 1,
        deletions = 1,
        blobSha = "new",
        patch = null,
        oldBlobSha = "old",
        newBlobSha = "new",
    )

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
