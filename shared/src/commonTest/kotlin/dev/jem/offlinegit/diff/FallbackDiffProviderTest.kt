package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FallbackDiffProviderTest {

    @Test
    fun `returns primary diff when it succeeds`() = runTest {
        val primaryDiff = diff(path = "primary")
        val fallbackDiff = diff(path = "fallback")
        val provider = FallbackDiffProvider(
            primary = FakeDiffProvider(Result.Success(primaryDiff)),
            fallback = FakeDiffProvider(Result.Success(fallbackDiff)),
        )

        val result = provider.getDiff(commitSha = "commit", file = file())

        assertEquals(Result.Success(primaryDiff), result)
    }

    @Test
    fun `returns fallback diff when primary fails`() = runTest {
        val fallbackDiff = diff(path = "fallback")
        val provider = FallbackDiffProvider(
            primary = FakeDiffProvider(Result.Error(DiffError.Storage(DataError.Local.NOT_FOUND))),
            fallback = FakeDiffProvider(Result.Success(fallbackDiff)),
        )

        val result = provider.getDiff(commitSha = "commit", file = file())

        assertEquals(Result.Success(fallbackDiff), result)
    }

    private fun file(): GithubCommitFile = GithubCommitFile(
        path = "README.md",
        previousPath = null,
        status = "modified",
        additions = 1,
        deletions = 1,
        blobSha = "new",
        patch = null,
    )

    private fun diff(path: String): DiffFile = DiffFile(
        path = path,
        previousPath = null,
        status = "modified",
        hunks = listOf(
            DiffHunk(
                oldStart = 1,
                oldLineCount = 1,
                newStart = 1,
                newLineCount = 1,
                lines = listOf(
                    DiffLine(
                        type = DiffLineType.HUNK_HEADER,
                        oldLineNumber = null,
                        newLineNumber = null,
                        text = "@@ -1 +1 @@",
                    ),
                ),
            ),
        ),
    )

    private class FakeDiffProvider(
        private val result: Result<DiffFile, DiffError>,
    ) : DiffProvider {
        override suspend fun getDiff(
            commitSha: String,
            file: GithubCommitFile,
        ): Result<DiffFile, DiffError> = result
    }
}
