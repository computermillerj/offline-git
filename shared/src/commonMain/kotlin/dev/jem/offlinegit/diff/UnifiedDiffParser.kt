package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffParser
import dev.jem.offlinegit.domain.Result

class UnifiedDiffParser : DiffParser {
    override fun parse(
        path: String,
        previousPath: String?,
        status: String,
        patch: String,
    ): Result<DiffFile, DiffError> {
        val hunks = mutableListOf<DiffHunk>()
        var currentHunk: MutableHunk? = null
        var oldLineNumber = 0
        var newLineNumber = 0

        patch.lineSequence().forEach { line ->
            val header = HUNK_HEADER.matchEntire(line)
            if (header != null) {
                currentHunk?.toDiffHunk()?.let { hunks += it }
                val oldStart = header.groupValues[1].toInt()
                val oldLineCount = header.groupValues[2].toCount()
                val newStart = header.groupValues[3].toInt()
                val newLineCount = header.groupValues[4].toCount()
                currentHunk = MutableHunk(
                    oldStart = oldStart,
                    oldLineCount = oldLineCount,
                    newStart = newStart,
                    newLineCount = newLineCount,
                    header = line,
                )
                oldLineNumber = oldStart
                newLineNumber = newStart
                return@forEach
            }

            val hunk = currentHunk ?: return@forEach
            when {
                line.startsWith("+") -> {
                    hunk.lines += DiffLine(
                        type = DiffLineType.ADDITION,
                        oldLineNumber = null,
                        newLineNumber = newLineNumber++,
                        text = line.drop(1),
                    )
                }
                line.startsWith("-") -> {
                    hunk.lines += DiffLine(
                        type = DiffLineType.DELETION,
                        oldLineNumber = oldLineNumber++,
                        newLineNumber = null,
                        text = line.drop(1),
                    )
                }
                line.startsWith(" ") -> {
                    hunk.lines += DiffLine(
                        type = DiffLineType.CONTEXT,
                        oldLineNumber = oldLineNumber++,
                        newLineNumber = newLineNumber++,
                        text = line.drop(1),
                    )
                }
                line.startsWith(NO_NEWLINE_PREFIX) -> {
                    hunk.lines += DiffLine(
                        type = DiffLineType.NO_NEWLINE,
                        oldLineNumber = null,
                        newLineNumber = null,
                        text = line,
                    )
                }
                else -> return Result.Error(DiffError.InvalidPatch)
            }
        }

        currentHunk?.toDiffHunk()?.let { hunks += it }
        return Result.Success(
            DiffFile(
                path = path,
                previousPath = previousPath,
                status = status,
                hunks = hunks,
            ),
        )
    }

    private fun String.toCount(): Int = if (isEmpty()) 1 else toInt()

    private data class MutableHunk(
        val oldStart: Int,
        val oldLineCount: Int,
        val newStart: Int,
        val newLineCount: Int,
        val header: String,
        val lines: MutableList<DiffLine> = mutableListOf(),
    ) {
        fun toDiffHunk(): DiffHunk = DiffHunk(
            oldStart = oldStart,
            oldLineCount = oldLineCount,
            newStart = newStart,
            newLineCount = newLineCount,
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

    private companion object {
        val HUNK_HEADER = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*")
        const val NO_NEWLINE_PREFIX = "\\ No newline at end of file"
    }
}
