package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.DiffSource
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.Result

/**
 * Computes a unified diff locally from cached blobs when no GitHub patch was
 * stored. Unlike the cached-patch path this is an approximation of GitHub's own
 * diff (LCS vs. Myers), so it is tagged [DiffSource.LOCAL_BLOB] for the UI.
 */
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
                hunks = buildHunks(oldLines = oldText.toLines(), newLines = newText.toLines()),
                source = DiffSource.LOCAL_BLOB,
            ),
        )
    }

    private fun buildHunks(
        oldLines: List<String>,
        newLines: List<String>,
    ): List<DiffHunk> {
        val diffLines = if (oldLines.size.toLong() * newLines.size.toLong() > MAX_LCS_CELLS) {
            // The LCS matrix would be too large to hold in memory; degrade to a
            // correct-but-not-minimal whole-file replacement instead of OOMing.
            fullReplacementLines(oldLines = oldLines, newLines = newLines)
        } else {
            lcsDiffLines(oldLines = oldLines, newLines = newLines)
        }
        return collapseToHunks(diffLines)
    }

    private fun lcsDiffLines(
        oldLines: List<String>,
        newLines: List<String>,
    ): List<DiffLine> {
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

    /** Every old line deleted then every new line added — O(n+m), no LCS matrix. */
    private fun fullReplacementLines(
        oldLines: List<String>,
        newLines: List<String>,
    ): List<DiffLine> {
        val diffLines = ArrayList<DiffLine>(oldLines.size + newLines.size)
        oldLines.forEachIndexed { index, text ->
            diffLines += DiffLine(
                type = DiffLineType.DELETION,
                oldLineNumber = index + 1,
                newLineNumber = null,
                text = text,
            )
        }
        newLines.forEachIndexed { index, text ->
            diffLines += DiffLine(
                type = DiffLineType.ADDITION,
                oldLineNumber = null,
                newLineNumber = index + 1,
                text = text,
            )
        }
        return diffLines
    }

    /**
     * Groups the flat diff into hunks the way git does: keep [CONTEXT_LINES] of
     * context around each change and collapse the unchanged runs between them,
     * so a small change in a large file no longer renders the whole file.
     */
    private fun collapseToHunks(diffLines: List<DiffLine>): List<DiffHunk> {
        if (diffLines.isEmpty()) return emptyList()
        val isChange = BooleanArray(diffLines.size) { index ->
            diffLines[index].type == DiffLineType.ADDITION || diffLines[index].type == DiffLineType.DELETION
        }
        if (isChange.none { it }) return emptyList()

        val keep = BooleanArray(diffLines.size)
        diffLines.indices.forEach { index ->
            if (isChange[index]) {
                val from = maxOf(0, index - CONTEXT_LINES)
                val to = minOf(diffLines.size - 1, index + CONTEXT_LINES)
                for (kept in from..to) keep[kept] = true
            }
        }

        val hunks = mutableListOf<DiffHunk>()
        var index = 0
        while (index < diffLines.size) {
            if (!keep[index]) {
                index++
                continue
            }
            var end = index
            while (end < diffLines.size && keep[end]) end++
            hunks += buildHunk(diffLines.subList(index, end))
            index = end
        }
        return hunks
    }

    private fun buildHunk(lines: List<DiffLine>): DiffHunk {
        // Context/deletions carry an old line number; context/additions a new one.
        val oldCount = lines.count { it.oldLineNumber != null }
        val newCount = lines.count { it.newLineNumber != null }
        val oldStart = lines.firstNotNullOfOrNull { it.oldLineNumber } ?: 0
        val newStart = lines.firstNotNullOfOrNull { it.newLineNumber } ?: 0
        val header = "@@ -$oldStart,$oldCount +$newStart,$newCount @@"
        return DiffHunk(
            oldStart = oldStart,
            oldLineCount = oldCount,
            newStart = newStart,
            newLineCount = newCount,
            lines = listOf(
                DiffLine(
                    type = DiffLineType.HUNK_HEADER,
                    oldLineNumber = null,
                    newLineNumber = null,
                    text = header,
                ),
            ) + lines,
        )
    }

    private fun String.toLines(): List<String> = split('\n')
        .let { lines -> if (endsWith('\n')) lines.dropLast(1) else lines }

    private companion object {
        /** Lines of unchanged context kept on each side of a change (git's default). */
        const val CONTEXT_LINES = 3

        /**
         * Ceiling on the LCS matrix size (old*new cells). At 4 bytes/cell this
         * caps the transient allocation near 32 MB; larger files degrade to a
         * full-replacement diff. Tune up if big offline files need real diffs.
         */
        const val MAX_LCS_CELLS = 8_000_000L
    }
}
