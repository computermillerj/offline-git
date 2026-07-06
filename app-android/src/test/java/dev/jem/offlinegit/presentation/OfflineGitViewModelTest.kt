package dev.jem.offlinegit.presentation

import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineGitViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads seeded local repository data`() = runTest {
        val viewModel = OfflineGitViewModel(
            repositoryStore = FakeRepositoryStore(),
            patchStore = FakePatchStore(),
            diffProvider = FakeDiffProvider(),
        )

        val state = viewModel.state.value

        assertFalse(state.isLoading)
        assertEquals("offline-demo", state.repositories.single().name)
        assertEquals("main", state.branches.single().name)
        assertEquals("demo-commit", state.commits.single().sha)
        assertEquals("README.md", state.files.single().path)
        assertNotNull(state.diff)
    }

    private class FakeRepositoryStore : RepositoryStore {
        private val repositories = mutableListOf<GithubRepository>()
        private val branches = mutableMapOf<Long, List<GithubBranch>>()
        private val commits = mutableMapOf<Pair<Long, String>, List<GithubCommitSummary>>()
        private val details = mutableMapOf<String, GithubCommitDetails>()

        override suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun upsertRepositories(repositories: List<GithubRepository>): EmptyResult<DataError.Local> {
            this.repositories.clear()
            this.repositories += repositories
            return Result.Success(Unit)
        }

        override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Local> =
            Result.Success(repositories.toList())

        override suspend fun upsertBranches(
            repositoryId: Long,
            branches: List<GithubBranch>,
        ): EmptyResult<DataError.Local> {
            this.branches[repositoryId] = branches
            return Result.Success(Unit)
        }

        override suspend fun getBranches(repositoryId: Long): Result<List<GithubBranch>, DataError.Local> =
            Result.Success(branches[repositoryId].orEmpty())

        override suspend fun upsertCommits(
            repositoryId: Long,
            branchName: String,
            commits: List<GithubCommitSummary>,
        ): EmptyResult<DataError.Local> {
            this.commits[repositoryId to branchName] = commits
            return Result.Success(Unit)
        }

        override suspend fun getCommits(
            repositoryId: Long,
            branchName: String,
        ): Result<List<GithubCommitSummary>, DataError.Local> =
            Result.Success(commits[repositoryId to branchName].orEmpty())

        override suspend fun upsertCommitDetails(
            repositoryId: Long,
            commit: GithubCommitDetails,
        ): EmptyResult<DataError.Local> {
            details[commit.sha] = commit
            return Result.Success(Unit)
        }

        override suspend fun getCommitDetails(sha: String): Result<GithubCommitDetails, DataError.Local> =
            details[sha]?.let { Result.Success(it) } ?: Result.Error(DataError.Local.NOT_FOUND)
    }

    private class FakePatchStore : PatchStore {
        override suspend fun writePatch(
            commitSha: String,
            path: String,
            patch: String,
        ): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun readPatch(
            commitSha: String,
            path: String,
        ): Result<String, DataError.Local> = Result.Error(DataError.Local.NOT_FOUND)
    }

    private class FakeDiffProvider : DiffProvider {
        override suspend fun getDiff(
            commitSha: String,
            file: GithubCommitFile,
        ): Result<DiffFile, dev.jem.offlinegit.domain.DiffError> = Result.Success(
            DiffFile(
                path = file.path,
                previousPath = file.previousPath,
                status = file.status,
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
            ),
        )
    }
}
