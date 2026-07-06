package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.Result

class LocalBlobDiffProvider(
    private val blobStore: BlobStore,
) : DiffProvider {
    override suspend fun getDiff(
        commitSha: String,
        file: GithubCommitFile,
    ): Result<DiffFile, DiffError> {
        val oldSha = file.oldBlobSha ?: return Result.Error(DiffError.Storage(DataError.Local.NOT_FOUND))
        val newSha = file.newBlobSha ?: file.blobSha ?: return Result.Error(DiffError.Storage(DataError.Local.NOT_FOUND))
        val oldText = when (val result = blobStore.readBlob(oldSha)) {
            is Result.Error -> return Result.Error(DiffError.Storage(result.error))
            is Result.Success -> result.data.decodeToString()
        }
        val newText = when (val result = blobStore.readBlob(newSha)) {
            is Result.Error -> return Result.Error(DiffError.Storage(result.error))
            is Result.Success -> result.data.decodeToString()
        }

        return Result.Success(
            DiffFile(
                path = file.path,
                previousPath = file.previousPath,
                status = file.status,
                hunks = listOf(buildHunk(oldText = oldText, newText = newText)),
            ),
        )
    }

    private fun buildHunk(
        oldText: String,
        newText: String,
    ): DiffHunk {
        val oldLines = oldText.toLines()
        val newLines = newText.toLines()
        val lines = buildDiffLines(oldLines = oldLines, newLines = newLines)
        return DiffHunk(
            oldStart = oldLines.startLine(),
            oldLineCount = oldLines.size,
            newStart = newLines.startLine(),
            newLineCount = newLines.size,
            lines = listOf(
                DiffLine(
                    type = DiffLineType.HUNK_HEADER,
                    oldLineNumber = null,
                    newLineNumber = null,
                    text = "@@ -${oldLines.startLine()},${oldLines.size} +${newLines.startLine()},${newLines.size} @@",
                ),
            ) + lines,
        )
    }

    private fun buildDiffLines(
        oldLines: List<String>,
        newLines: List<String>,
    ): List<DiffLine> {
        // ponytail: O(n*m) LCS; replace with Myers if large blob diffs need speed.
        val lcs = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (oldIndex in oldLines.indices.reversed()) {
            for (newIndex in newLines.indices.reversed()) {
                lcs[oldIndex][newIndex] = if (oldLines[oldIndex] == newLines[newIndex]) {
                    lcs[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1])
                }
            }
        }

        val diffLines = mutableListOf<DiffLine>()
        var oldIndex = 0
        var newIndex = 0
        var oldLineNumber = 1
        var newLineNumber = 1
        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            when {
                oldIndex < oldLines.size && newIndex < newLines.size && oldLines[oldIndex] == newLines[newIndex] -> {
                    diffLines += DiffLine(
                        type = DiffLineType.CONTEXT,
                        oldLineNumber = oldLineNumber++,
                        newLineNumber = newLineNumber++,
                        text = oldLines[oldIndex],
                    )
                    oldIndex++
                    newIndex++
                }
                newIndex < newLines.size && (oldIndex == oldLines.size || lcs[oldIndex][newIndex + 1] > lcs[oldIndex + 1][newIndex]) -> {
                    diffLines += DiffLine(
                        type = DiffLineType.ADDITION,
                        oldLineNumber = null,
                        newLineNumber = newLineNumber++,
                        text = newLines[newIndex++],
                    )
                }
                oldIndex < oldLines.size -> {
                    diffLines += DiffLine(
                        type = DiffLineType.DELETION,
                        oldLineNumber = oldLineNumber++,
                        newLineNumber = null,
                        text = oldLines[oldIndex++],
                    )
                }
            }
        }
        return diffLines
    }

    private fun String.toLines(): List<String> = split('\n')
        .let { lines -> if (endsWith('\n')) lines.dropLast(1) else lines }

    private fun List<String>.startLine(): Int = if (isEmpty()) 0 else 1
}
