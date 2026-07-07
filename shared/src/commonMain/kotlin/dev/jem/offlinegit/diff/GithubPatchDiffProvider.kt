package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffParser
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.DiffSource
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.Result

class GithubPatchDiffProvider(
    private val patchStore: PatchStore,
    private val parser: DiffParser = UnifiedDiffParser(),
) : DiffProvider {
    override suspend fun getDiff(
        commitSha: String,
        file: GithubCommitFile,
    ): Result<DiffFile, DiffError> = when (val patch = patchStore.readPatch(commitSha = commitSha, path = file.path)) {
        is Result.Error -> Result.Error(DiffError.Storage(patch.error))
        is Result.Success -> when (
            val parsed = parser.parse(
                path = file.path,
                previousPath = file.previousPath,
                status = file.status,
                patch = patch.data,
            )
        ) {
            is Result.Error -> parsed
            is Result.Success -> Result.Success(parsed.data.copy(source = DiffSource.CACHED_PATCH))
        }
    }
}
