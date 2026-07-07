package dev.jem.offlinegit.presentation

import dev.jem.offlinegit.domain.AccountTokenStore
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
import dev.jem.offlinegit.domain.GithubSession
import dev.jem.offlinegit.domain.GithubSessionFactory
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.StoredSelection
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
        assertEquals("README.md", state.fileDiffs.single().file.path)
        assertNotNull(state.fileDiffs.single().diff)
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
        val tokenStore = FakeAccountTokenStore()
        val viewModel = createViewModel(
            remoteDataSource = FakeGithubRemoteDataSource(repositories = listOf(remoteRepository)),
            tokenStore = tokenStore,
        )

        viewModel.onAction(OfflineGitAction.OnAuthClientIdChange("client-id"))
        viewModel.onAction(OfflineGitAction.OnStartDeviceAuthClick)
        advanceUntilIdle()

        assertEquals("token", tokenStore.tokens[7])
        assertEquals(7L, viewModel.state.value.activeAccountId)
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

    @Test
    fun `switching accounts scopes repositories to the active account`() = runTest {
        val (repositoryStore, tokenStore) = twoAccountSetup()
        val viewModel = createViewModel(repositoryStore = repositoryStore, tokenStore = tokenStore)

        assertEquals(listOf("shared"), viewModel.state.value.repositories.map { it.name })

        viewModel.onAction(OfflineGitAction.OnAccountClick(2))

        assertEquals(2L, viewModel.state.value.activeAccountId)
        assertEquals(listOf("shared", "bob-only"), viewModel.state.value.repositories.map { it.name })
    }

    @Test
    fun `removing an account keeps repositories other accounts can see`() = runTest {
        val (repositoryStore, tokenStore) = twoAccountSetup()
        val viewModel = createViewModel(repositoryStore = repositoryStore, tokenStore = tokenStore)
        viewModel.onAction(OfflineGitAction.OnAccountClick(2))

        viewModel.onAction(OfflineGitAction.OnRemoveAccountClick(2))

        val state = viewModel.state.value
        assertEquals(1L, state.activeAccountId)
        assertEquals(listOf("shared"), state.repositories.map { it.name })
        assertEquals(null, tokenStore.tokens[2])
    }

    @Test
    fun `restores persisted selection on load`() = runTest {
        val repositoryStore = FakeRepositoryStore()
        val repository = GithubRepository(
            id = 5,
            owner = "octo",
            name = "restored",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val commits = listOf("first-sha", "second-sha").map { sha ->
            GithubCommitSummary(
                sha = sha,
                firstParentSha = null,
                authorName = null,
                authorEmail = null,
                message = "Commit $sha",
                committedAt = null,
            )
        }
        repositoryStore.upsertRepositories(listOf(repository))
        repositoryStore.upsertBranches(repositoryId = repository.id, branches = listOf(GithubBranch("main", "head")))
        repositoryStore.upsertCommits(repositoryId = repository.id, branchName = "main", commits = commits)
        commits.forEach { commit ->
            repositoryStore.upsertCommitDetails(
                repositoryId = repository.id,
                commit = GithubCommitDetails(
                    sha = commit.sha,
                    firstParentSha = null,
                    authorName = null,
                    authorEmail = null,
                    message = commit.message,
                    committedAt = null,
                    files = emptyList(),
                ),
            )
        }
        repositoryStore.saveSelection(
            StoredSelection(
                repositoryId = repository.id,
                branchName = "main",
                commitSha = "second-sha",
                filePath = null,
            ),
        )

        val viewModel = createViewModel(repositoryStore = repositoryStore)

        assertEquals(repository.id, viewModel.state.value.selectedRepositoryId)
        assertEquals("main", viewModel.state.value.selectedBranchName)
        assertEquals("second-sha", viewModel.state.value.selectedCommitSha)
    }

    @Test
    fun `migrates legacy single-account token on load`() = runTest {
        val tokenStore = FakeAccountTokenStore(legacyToken = "legacy-token")
        val viewModel = createViewModel(tokenStore = tokenStore)

        assertEquals("legacy-token", tokenStore.tokens[7])
        assertEquals(null, tokenStore.legacyToken)
        assertEquals(7L, viewModel.state.value.activeAccountId)
        assertEquals(true, viewModel.state.value.hasToken)
    }

    private suspend fun twoAccountSetup(): Pair<FakeRepositoryStore, FakeAccountTokenStore> {
        val repositoryStore = FakeRepositoryStore()
        val shared = GithubRepository(
            id = 10,
            owner = "octo",
            name = "shared",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val bobOnly = shared.copy(id = 20, name = "bob-only")
        repositoryStore.upsertAccount(GithubAccount(id = 1, login = "alice", avatarUrl = null))
        repositoryStore.upsertAccount(GithubAccount(id = 2, login = "bob", avatarUrl = null))
        repositoryStore.upsertRepositories(listOf(shared, bobOnly))
        repositoryStore.replaceAccountRepositories(accountId = 1, repositoryIds = listOf(10))
        repositoryStore.replaceAccountRepositories(accountId = 2, repositoryIds = listOf(10, 20))
        repositoryStore.setActiveAccount(2)
        repositoryStore.setActiveAccount(1)
        val tokenStore = FakeAccountTokenStore().apply {
            tokens[1] = "alice-token"
            tokens[2] = "bob-token"
        }
        return repositoryStore to tokenStore
    }

    private fun createViewModel(
        repositoryStore: FakeRepositoryStore = FakeRepositoryStore(),
        remoteDataSource: GithubRemoteDataSource = FakeGithubRemoteDataSource(),
        authDataSource: GithubDeviceAuthDataSource = FakeGithubDeviceAuthDataSource(),
        tokenStore: FakeAccountTokenStore = FakeAccountTokenStore(),
    ): OfflineGitViewModel = OfflineGitViewModel(
        repositoryStore = repositoryStore,
        patchStore = FakePatchStore(),
        diffProvider = FakeDiffProvider(),
        sessionFactory = FakeSessionFactory(remoteDataSource),
        authDataSource = authDataSource,
        accountTokenStore = tokenStore,
    )

    private class FakeAccountTokenStore(
        var legacyToken: String? = null,
    ) : AccountTokenStore {
        val tokens = mutableMapOf<Long, String>()

        override suspend fun getToken(accountId: Long): String? = tokens[accountId]

        override suspend fun saveToken(
            accountId: Long,
            token: String,
        ) {
            tokens[accountId] = token
        }

        override suspend fun clearToken(accountId: Long) {
            tokens.remove(accountId)
        }

        override suspend fun getLegacyToken(): String? = legacyToken

        override suspend fun clearLegacyToken() {
            legacyToken = null
        }
    }

    private class FakeSessionFactory(
        remoteDataSource: GithubRemoteDataSource,
    ) : GithubSessionFactory {
        private val session = GithubSession(
            remoteDataSource = remoteDataSource,
            branchDownloader = FakeBranchDownloader(),
        )

        override fun sessionFor(accountId: Long): GithubSession = session

        override fun sessionForToken(token: String): GithubSession = session
    }

    private class FakeRepositoryStore : RepositoryStore {
        private val repositories = mutableListOf<GithubRepository>()
        private val branches = mutableMapOf<Long, List<GithubBranch>>()
        private val commits = mutableMapOf<Pair<Long, String>, List<GithubCommitSummary>>()
        private val details = mutableMapOf<String, GithubCommitDetails>()
        private val downloadJobs = mutableListOf<BranchDownloadJob>()
        private val accounts = linkedMapOf<Long, GithubAccount>()
        private val activationOrder = mutableListOf<Long>()
        private val accountRepositories = mutableMapOf<Long, MutableSet<Long>>()
        private var selection: StoredSelection? = null

        override suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local> {
            accounts[account.id] = account
            return Result.Success(Unit)
        }

        override suspend fun getAccounts(): Result<List<GithubAccount>, DataError.Local> =
            Result.Success(accounts.values.sortedBy { it.login })

        override suspend fun getActiveAccount(): Result<GithubAccount?, DataError.Local> =
            Result.Success(activationOrder.lastOrNull()?.let { accounts[it] })

        override suspend fun setActiveAccount(accountId: Long): EmptyResult<DataError.Local> {
            activationOrder.remove(accountId)
            activationOrder += accountId
            return Result.Success(Unit)
        }

        override suspend fun removeAccount(accountId: Long): EmptyResult<DataError.Local> {
            val visible = accountRepositories.remove(accountId).orEmpty()
            val stillVisible = accountRepositories.values.flatten().toSet()
            repositories.removeAll { it.id in visible && it.id !in stillVisible }
            accounts.remove(accountId)
            activationOrder.remove(accountId)
            return Result.Success(Unit)
        }

        override suspend fun replaceAccountRepositories(
            accountId: Long,
            repositoryIds: List<Long>,
        ): EmptyResult<DataError.Local> {
            accountRepositories[accountId] = repositoryIds.toMutableSet()
            return Result.Success(Unit)
        }

        override suspend fun getRepositoriesForAccount(
            accountId: Long,
        ): Result<List<GithubRepository>, DataError.Local> =
            Result.Success(repositories.filter { it.id in accountRepositories[accountId].orEmpty() })

        override suspend fun getSelection(): Result<StoredSelection?, DataError.Local> = Result.Success(selection)

        override suspend fun saveSelection(selection: StoredSelection): EmptyResult<DataError.Local> {
            this.selection = selection
            return Result.Success(Unit)
        }

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
        private val account: GithubAccount = GithubAccount(id = 7, login = "octo", avatarUrl = null),
    ) : GithubRemoteDataSource {
        override suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network> = Result.Success(account)

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
