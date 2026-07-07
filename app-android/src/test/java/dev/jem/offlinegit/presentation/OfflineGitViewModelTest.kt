package dev.jem.offlinegit.presentation

import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.BranchDownloadProgress
import dev.jem.offlinegit.domain.BranchDownloader
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubAccessToken
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubDeviceAuthDataSource
import dev.jem.offlinegit.domain.GithubDeviceAuthError
import dev.jem.offlinegit.domain.GithubDeviceCode
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        val viewModel = createViewModel()

        val state = viewModel.state.value

        assertFalse(state.isLoading)
        assertEquals("offline-demo", state.repositories.single().name)
        assertEquals("main", state.branches.single().name)
        assertEquals("demo-commit", state.commits.single().sha)
        assertEquals("README.md", state.files.single().path)
        assertEquals(PhonePane.REPOSITORIES, state.phonePane)
        assertNotNull(state.diff)
    }

    @Test
    fun `loads persisted download job for selected branch`() = runTest {
        val repositoryStore = FakeRepositoryStore()
        val repository = GithubRepository(
            id = 10,
            owner = "octo",
            name = "downloaded",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val branch = GithubBranch(name = "main", headSha = "commit-sha")
        val commit = GithubCommitSummary(
            sha = "commit-sha",
            firstParentSha = null,
            authorName = null,
            authorEmail = null,
            message = "Commit",
            committedAt = null,
        )
        val details = GithubCommitDetails(
            sha = commit.sha,
            firstParentSha = null,
            authorName = null,
            authorEmail = null,
            message = commit.message,
            committedAt = null,
            files = emptyList(),
        )
        val job = BranchDownloadJob(
            id = "10:main",
            repositoryId = repository.id,
            branchName = branch.name,
            status = BranchDownloadJobStatus.COMPLETED,
            progress = 1.0,
            errorMessage = null,
        )
        repositoryStore.upsertRepositories(listOf(repository))
        repositoryStore.upsertBranches(repositoryId = repository.id, branches = listOf(branch))
        repositoryStore.upsertCommits(repositoryId = repository.id, branchName = branch.name, commits = listOf(commit))
        repositoryStore.upsertCommitDetails(repositoryId = repository.id, commit = details)
        repositoryStore.upsertDownloadJob(job)

        val viewModel = createViewModel(repositoryStore = repositoryStore)

        assertEquals(job, viewModel.state.value.downloadJob)
    }

    @Test
    fun `changes diff viewer mode`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(OfflineGitAction.OnViewerModeClick(DiffViewerMode.SIDE_BY_SIDE))

        assertEquals(DiffViewerMode.SIDE_BY_SIDE, viewModel.state.value.viewerMode)
    }

    @Test
    fun `phone navigation advances and goes back`() = runTest {
        val viewModel = createViewModel()

        viewModel.onAction(OfflineGitAction.OnRepositoryClick(1))
        assertEquals(PhonePane.BRANCHES, viewModel.state.value.phonePane)

        viewModel.onAction(OfflineGitAction.OnBranchClick("main"))
        assertEquals(PhonePane.COMMITS, viewModel.state.value.phonePane)

        viewModel.onAction(OfflineGitAction.OnCommitClick("demo-commit"))
        assertEquals(PhonePane.FILES, viewModel.state.value.phonePane)

        viewModel.onAction(OfflineGitAction.OnFileClick("README.md"))
        assertEquals(PhonePane.DIFF, viewModel.state.value.phonePane)

        viewModel.onAction(OfflineGitAction.OnPhoneBackClick)
        assertEquals(PhonePane.FILES, viewModel.state.value.phonePane)
    }

    @Test
    fun `device auth saves token and fetches repositories`() = runTest {
        val remoteRepository = GithubRepository(
            id = 43,
            owner = "octo",
            name = "device-repo",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val tokenStore = MutableTokenStore()
        val viewModel = createViewModel(
            remoteDataSource = FakeGithubRemoteDataSource(repositories = listOf(remoteRepository)),
            tokenStore = tokenStore,
        )

        viewModel.onAction(OfflineGitAction.OnAuthClientIdChange("client-id"))
        viewModel.onAction(OfflineGitAction.OnStartDeviceAuthClick)
        advanceUntilIdle()

        assertEquals("token", tokenStore.token)
        assertEquals(true, viewModel.state.value.hasToken)
        assertEquals("device-repo", viewModel.state.value.repositories.single().name)
    }

    @Test
    fun `fetch repositories stores remote repositories`() = runTest {
        val remoteRepository = GithubRepository(
            id = 42,
            owner = "octo",
            name = "remote-repo",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val viewModel = createViewModel(
            remoteDataSource = FakeGithubRemoteDataSource(repositories = listOf(remoteRepository)),
        )

        viewModel.onAction(OfflineGitAction.OnTokenChange("token"))
        viewModel.onAction(OfflineGitAction.OnFetchRepositoriesClick)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("", state.githubToken)
        assertEquals(true, state.hasToken)
        assertEquals("remote-repo", state.repositories.single().name)
        assertEquals("Fetched 1 repositories", state.syncStatus)
    }

    private fun createViewModel(
        repositoryStore: FakeRepositoryStore = FakeRepositoryStore(),
        remoteDataSource: GithubRemoteDataSource = FakeGithubRemoteDataSource(),
        authDataSource: GithubDeviceAuthDataSource = FakeGithubDeviceAuthDataSource(),
        tokenStore: MutableTokenStore = MutableTokenStore(),
    ): OfflineGitViewModel = OfflineGitViewModel(
        repositoryStore = repositoryStore,
        patchStore = FakePatchStore(),
        diffProvider = FakeDiffProvider(),
        remoteDataSource = remoteDataSource,
        authDataSource = authDataSource,
        branchDownloader = FakeBranchDownloader(),
        tokenStore = tokenStore,
    )

    private class FakeRepositoryStore : RepositoryStore {
        private val repositories = mutableListOf<GithubRepository>()
        private val branches = mutableMapOf<Long, List<GithubBranch>>()
        private val commits = mutableMapOf<Pair<Long, String>, List<GithubCommitSummary>>()
        private val details = mutableMapOf<String, GithubCommitDetails>()
        private val downloadJobs = mutableListOf<BranchDownloadJob>()

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

        override suspend fun upsertDownloadJob(job: BranchDownloadJob): EmptyResult<DataError.Local> {
            downloadJobs += job
            return Result.Success(Unit)
        }

        override suspend fun getDownloadJob(
            repositoryId: Long,
            branchName: String,
        ): Result<BranchDownloadJob?, DataError.Local> = Result.Success(
            downloadJobs.lastOrNull { it.repositoryId == repositoryId && it.branchName == branchName },
        )

        override suspend fun getDownloadedRepositoryIds(): Result<Set<Long>, DataError.Local> = Result.Success(
            downloadJobs
                .filter { it.status == BranchDownloadJobStatus.COMPLETED }
                .map { it.repositoryId }
                .toSet(),
        )
    }

    private class FakeGithubRemoteDataSource(
        private val repositories: List<GithubRepository> = emptyList(),
        private val branches: List<GithubBranch> = emptyList(),
    ) : GithubRemoteDataSource {
        override suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network> = error("not used")

        override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Network> =
            Result.Success(repositories)

        override suspend fun getBranches(
            owner: String,
            repository: String,
        ): Result<List<GithubBranch>, DataError.Network> = Result.Success(branches)

        override suspend fun getCommits(
            owner: String,
            repository: String,
            branch: String,
            limit: Int,
        ): Result<List<GithubCommitSummary>, DataError.Network> = error("not used")

        override suspend fun getCommitDetails(
            owner: String,
            repository: String,
            sha: String,
        ): Result<GithubCommitDetails, DataError.Network> = error("not used")

        override suspend fun getBlob(
            owner: String,
            repository: String,
            sha: String,
        ): Result<ByteArray, DataError.Network> = error("not used")

        override suspend fun getTarball(
            owner: String,
            repository: String,
            ref: String,
        ): Result<ByteArray, DataError.Network> = error("not used")
    }

    private class FakeGithubDeviceAuthDataSource : GithubDeviceAuthDataSource {
        override suspend fun createDeviceCode(clientId: String): Result<GithubDeviceCode, DataError.Network> = Result.Success(
            GithubDeviceCode(
                deviceCode = "device-code",
                userCode = "USER-CODE",
                verificationUri = "https://github.com/login/device",
                expiresInSeconds = 900,
                intervalSeconds = 5,
            ),
        )

        override suspend fun pollAccessToken(
            clientId: String,
            deviceCode: String,
        ): Result<GithubAccessToken, GithubDeviceAuthError> = Result.Success(GithubAccessToken(accessToken = "token"))
    }

    private class FakeBranchDownloader : BranchDownloader {
        override fun downloadBranch(
            repository: GithubRepository,
            branch: GithubBranch,
            commitLimit: Int,
        ): Flow<BranchDownloadProgress> = emptyFlow()
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
        ): Result<DiffFile, DiffError> = Result.Success(
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
