package dev.jem.offlinegit.domain

data class DiffFile(
    val path: String,
    val previousPath: String?,
    val status: String,
    val hunks: List<DiffHunk>,
)

data class DiffHunk(
    val oldStart: Int,
    val oldLineCount: Int,
    val newStart: Int,
    val newLineCount: Int,
    val lines: List<DiffLine>,
)

data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
)

enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION,
    HUNK_HEADER,
    NO_NEWLINE,
}

sealed interface DiffError : Error {
    data object InvalidPatch : DiffError
    data class Storage(val error: DataError.Local) : DiffError
}

interface DiffParser {
    fun parse(
        path: String,
        previousPath: String?,
        status: String,
        patch: String,
    ): Result<DiffFile, DiffError>
}

interface DiffProvider {
    suspend fun getDiff(
        commitSha: String,
        file: GithubCommitFile,
    ): Result<DiffFile, DiffError>
}
