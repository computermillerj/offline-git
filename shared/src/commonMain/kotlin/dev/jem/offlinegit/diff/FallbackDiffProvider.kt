package dev.jem.offlinegit.diff

import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.Result

class FallbackDiffProvider(
    private val primary: DiffProvider,
    private val fallback: DiffProvider,
) : DiffProvider {
    override suspend fun getDiff(
        commitSha: String,
        file: GithubCommitFile,
    ): Result<DiffFile, DiffError> = when (val result = primary.getDiff(commitSha = commitSha, file = file)) {
        is Result.Error -> fallback.getDiff(commitSha = commitSha, file = file)
        is Result.Success -> result
    }
}
