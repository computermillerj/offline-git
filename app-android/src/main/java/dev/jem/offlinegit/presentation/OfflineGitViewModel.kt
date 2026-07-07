package dev.jem.offlinegit.presentation

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jem.offlinegit.domain.AccountTokenStore
import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadProgress
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.Error
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubDeviceAuthDataSource
import dev.jem.offlinegit.domain.GithubDeviceAuthError
import dev.jem.offlinegit.domain.GithubDeviceCode
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.GithubSessionFactory
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.StoredSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OfflineGitViewModel(
    private val repositoryStore: RepositoryStore,
    private val patchStore: PatchStore,
    private val diffProvider: DiffProvider,
    private val sessionFactory: GithubSessionFactory,
    private val authDataSource: GithubDeviceAuthDataSource,
    private val accountTokenStore: AccountTokenStore,
    private val closeRemoteResources: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(OfflineGitState())
    val state = _state.asStateFlow()

    private var deviceAuthJob: Job? = null
    private var hasRestoredSelection = false

    init {
        load()
    }

    override fun onCleared() {
        deviceAuthJob?.cancel()
        closeRemoteResources()
        super.onCleared()
    }

    fun onAction(action: OfflineGitAction) {
        when (action) {
            OfflineGitAction.OnRetryClick -> load()
            OfflineGitAction.OnFetchRepositoriesClick -> fetchRemoteRepositories()
            OfflineGitAction.OnFetchBranchesClick -> fetchRemoteBranches()
            OfflineGitAction.OnDownloadBranchClick -> downloadSelectedBranch()
            OfflineGitAction.OnStartDeviceAuthClick -> startDeviceAuth()
            OfflineGitAction.OnCancelDeviceAuthClick -> cancelDeviceAuth()
            is OfflineGitAction.OnAccountClick -> switchAccount(action.accountId)
            is OfflineGitAction.OnRemoveAccountClick -> removeAccount(action.accountId)
            OfflineGitAction.OnPhoneBackClick -> _state.update { it.copy(phonePane = it.phonePane.previous()) }
            is OfflineGitAction.OnTokenChange -> _state.update { it.copy(githubToken = action.token) }
            is OfflineGitAction.OnAuthClientIdChange -> _state.update { it.copy(authClientId = action.clientId) }
            is OfflineGitAction.OnCommitLimitChange -> _state.update { it.copy(commitLimitText = action.limit) }
            is OfflineGitAction.OnRepositorySearchChange -> _state.update { it.copy(repositorySearchQuery = action.query) }
            is OfflineGitAction.OnRepositoryClick -> load(
                repositoryId = action.repositoryId,
                branchName = null,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.BRANCHES,
            )
            is OfflineGitAction.OnBranchClick -> load(
                branchName = action.branchName,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.COMMITS,
            )
            is OfflineGitAction.OnCommitClick -> load(
                commitSha = action.commitSha,
                filePath = null,
                phonePane = PhonePane.FILES,
            )
            // Every file diff is loaded when the commit is selected, so a file
            // click only moves the selection (the wide layout scrolls to it).
            is OfflineGitAction.OnFileClick -> {
                _state.update {
                    it.copy(selectedFilePath = action.path, phonePane = PhonePane.DIFF)
                }
                persistSelection()
            }
            is OfflineGitAction.OnViewerModeClick -> _state.update { it.copy(viewerMode = action.mode) }
        }
    }

    private fun load(
        repositoryId: Long? = _state.value.selectedRepositoryId,
        branchName: String? = _state.value.selectedBranchName,
        commitSha: String? = _state.value.selectedCommitSha,
        filePath: String? = _state.value.selectedFilePath,
        phonePane: PhonePane? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            migrateLegacyTokenIfNeeded()
            seedDemoIfEmpty()
            // First load after process death: pick up where the user left off.
            var restored = StoredSelection(repositoryId, branchName, commitSha, filePath)
            if (!hasRestoredSelection) {
                hasRestoredSelection = true
                (repositoryStore.getSelection() as? Result.Success)?.data?.let { stored ->
                    restored = StoredSelection(
                        repositoryId = repositoryId ?: stored.repositoryId,
                        branchName = branchName ?: stored.branchName,
                        commitSha = commitSha ?: stored.commitSha,
                        filePath = filePath ?: stored.filePath,
                    )
                }
            }
            loadSelection(
                repositoryId = restored.repositoryId,
                branchName = restored.branchName,
                commitSha = restored.commitSha,
                filePath = restored.filePath,
                phonePane = phonePane,
            )
        }
    }

    /**
     * The pre-multi-account build stored one token with no owning account.
     * Resolve it to an account via /user, move the token under that account,
     * and link the already-downloaded repositories to it — they were all
     * fetched with this token. Left in place to retry if the probe fails
     * (e.g. offline on first launch after the update).
     */
    private suspend fun migrateLegacyTokenIfNeeded() {
        val legacyToken = accountTokenStore.getLegacyToken() ?: return
        val account = when (val result = sessionFactory.sessionForToken(legacyToken).remoteDataSource.getAuthenticatedUser()) {
            is Result.Error -> return
            is Result.Success -> result.data
        }
        repositoryStore.upsertAccount(account)
        accountTokenStore.saveToken(accountId = account.id, token = legacyToken)
        repositoryStore.setActiveAccount(account.id)
        val repositories = (repositoryStore.getRepositories() as? Result.Success)?.data.orEmpty()
        repositoryStore.replaceAccountRepositories(
            accountId = account.id,
            repositoryIds = repositories.map { it.id },
        )
        accountTokenStore.clearLegacyToken()
    }

    /** Registers the account a token belongs to and makes it the active one. */
    private suspend fun registerAccount(token: String): GithubAccount? {
        val account = sessionFactory.sessionForToken(token).remoteDataSource.getAuthenticatedUser()
            .dataOrNull { "Unable to verify token: ${it.toMessage()}" }
            ?: return null
        repositoryStore.upsertAccount(account)
            .dataOrNull { "Unable to save account: ${it.toMessage()}" }
            ?: return null
        accountTokenStore.saveToken(accountId = account.id, token = token)
        if (accountTokenStore.getToken(account.id).isNullOrBlank()) {
            _state.update { it.copy(isLoading = false, error = "Unable to save token securely") }
            return null
        }
        repositoryStore.setActiveAccount(account.id)
        _state.update {
            it.copy(
                githubToken = "",
                hasToken = true,
                activeAccountId = account.id,
                authMessage = "Signed in as ${account.login}",
            )
        }
        return account
    }

    private fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            repositoryStore.setActiveAccount(accountId)
                .dataOrNull { "Unable to switch account: ${it.toMessage()}" }
                ?: return@launch
            load(
                repositoryId = null,
                branchName = null,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.REPOSITORIES,
            )
        }
    }

    private fun removeAccount(accountId: Long) {
        viewModelScope.launch {
            accountTokenStore.clearToken(accountId)
            repositoryStore.removeAccount(accountId)
                .dataOrNull { "Unable to remove account: ${it.toMessage()}" }
                ?: return@launch
            load(
                repositoryId = null,
                branchName = null,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.REPOSITORIES,
            )
        }
    }

    private fun persistSelection() {
        val current = _state.value
        viewModelScope.launch {
            repositoryStore.saveSelection(
                StoredSelection(
                    repositoryId = current.selectedRepositoryId,
                    branchName = current.selectedBranchName,
                    commitSha = current.selectedCommitSha,
                    filePath = current.selectedFilePath,
                ),
            )
        }
    }

    private fun fetchRemoteRepositories() {
        viewModelScope.launch {
            if (!saveEnteredTokenIfNeeded()) {
                return@launch
            }
            val accountId = _state.value.activeAccountId ?: run {
                _state.update { it.copy(error = "Sign in with GitHub first") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null, syncStatus = "Fetching GitHub repositories…") }
            val repositories = sessionFactory.sessionFor(accountId).remoteDataSource.getRepositories()
                .dataOrNull { "Unable to fetch repositories: ${it.toMessage()}" }
                ?: return@launch
            repositoryStore.upsertRepositories(repositories)
                .dataOrNull { "Unable to save repositories: ${it.toMessage()}" }
                ?: return@launch
            repositoryStore.replaceAccountRepositories(
                accountId = accountId,
                repositoryIds = repositories.map { it.id },
            )
                .dataOrNull { "Unable to link repositories: ${it.toMessage()}" }
                ?: return@launch
            _state.update { it.copy(syncStatus = "Fetched ${repositories.size} repositories") }
            loadSelection(
                repositoryId = repositories.firstOrNull()?.id ?: _state.value.selectedRepositoryId,
                branchName = null,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.REPOSITORIES,
            )
        }
    }

    private fun fetchRemoteBranches() {
        viewModelScope.launch {
            if (!saveEnteredTokenIfNeeded()) {
                return@launch
            }
            val accountId = _state.value.activeAccountId ?: run {
                _state.update { it.copy(error = "Sign in with GitHub first") }
                return@launch
            }
            val repository = selectedRepository() ?: run {
                _state.update { it.copy(error = "Select a repository first") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null, syncStatus = "Fetching branches for ${repository.name}…") }
            val branches = sessionFactory.sessionFor(accountId).remoteDataSource
                .getBranches(owner = repository.owner, repository = repository.name)
                .dataOrNull { "Unable to fetch branches: ${it.toMessage()}" }
                ?: return@launch
            repositoryStore.upsertBranches(repositoryId = repository.id, branches = branches)
                .dataOrNull { "Unable to save branches: ${it.toMessage()}" }
                ?: return@launch
            _state.update { it.copy(syncStatus = "Fetched ${branches.size} branches") }
            loadSelection(
                repositoryId = repository.id,
                branchName = branches.firstOrNull()?.name,
                commitSha = null,
                filePath = null,
                phonePane = PhonePane.BRANCHES,
            )
        }
    }

    private fun downloadSelectedBranch() {
        viewModelScope.launch {
            if (!saveEnteredTokenIfNeeded()) {
                return@launch
            }
            val commitLimit = _state.value.commitLimitText.toIntOrNull()?.takeIf { it in 1..MAX_COMMIT_LIMIT } ?: run {
                _state.update { it.copy(error = "Commit limit must be between 1 and $MAX_COMMIT_LIMIT") }
                return@launch
            }
            val accountId = _state.value.activeAccountId ?: run {
                _state.update { it.copy(error = "Sign in with GitHub first") }
                return@launch
            }
            val repository = selectedRepository() ?: run {
                _state.update { it.copy(error = "Select a repository first") }
                return@launch
            }
            val branch = selectedBranch() ?: run {
                _state.update { it.copy(error = "Select a branch first") }
                return@launch
            }

            sessionFactory.sessionFor(accountId).branchDownloader.downloadBranch(
                repository = repository,
                branch = branch,
                commitLimit = commitLimit,
            ).collect { progress ->
                when (progress) {
                    BranchDownloadProgress.Started -> _state.update {
                        it.copy(isLoading = true, error = null, syncStatus = "Downloading ${repository.name}/${branch.name}…")
                    }
                    is BranchDownloadProgress.CommitsStored -> _state.update {
                        it.copy(syncStatus = "Stored ${progress.count} commits")
                    }
                    is BranchDownloadProgress.CommitStored -> _state.update {
                        it.copy(syncStatus = "Stored ${progress.completed}/${progress.total}: ${progress.sha.take(12)}")
                    }
                    BranchDownloadProgress.Completed -> {
                        _state.update { it.copy(syncStatus = "Download complete") }
                        loadSelection(
                            repositoryId = repository.id,
                            branchName = branch.name,
                            commitSha = null,
                            filePath = null,
                            phonePane = PhonePane.COMMITS,
                        )
                    }
                    is BranchDownloadProgress.Failed -> _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Download failed: ${progress.error.toMessage()}",
                        )
                    }
                }
            }
        }
    }

    private fun startDeviceAuth() {
        val clientId = _state.value.authClientId.trim()
        if (clientId.isBlank()) {
            _state.update { it.copy(error = "Enter a GitHub OAuth app client ID") }
            return
        }

        deviceAuthJob?.cancel()
        deviceAuthJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    error = null,
                    authMessage = "Requesting device code…",
                    isAuthPolling = true,
                    deviceUserCode = null,
                    deviceVerificationUri = null,
                )
            }
            val deviceCode = authDataSource.createDeviceCode(clientId)
                .dataOrNull { "Unable to start device sign-in: ${it.toMessage()}" }
                ?: run {
                    _state.update { it.copy(isAuthPolling = false) }
                    return@launch
                }
            pollDeviceAuth(
                clientId = clientId,
                deviceCode = deviceCode,
            )
        }
    }

    private suspend fun pollDeviceAuth(
        clientId: String,
        deviceCode: GithubDeviceCode,
    ) {
        _state.update {
            it.copy(
                authMessage = "Open ${deviceCode.verificationUri} and enter ${deviceCode.userCode}",
                deviceUserCode = deviceCode.userCode,
                deviceVerificationUri = deviceCode.verificationUri,
            )
        }

        var elapsedSeconds = 0
        var intervalSeconds = deviceCode.intervalSeconds.coerceAtLeast(MIN_DEVICE_AUTH_INTERVAL_SECONDS)
        while (elapsedSeconds < deviceCode.expiresInSeconds) {
            delay(intervalSeconds * 1_000L)
            elapsedSeconds += intervalSeconds
            when (val result = authDataSource.pollAccessToken(clientId = clientId, deviceCode = deviceCode.deviceCode)) {
                is Result.Success -> {
                    val account = registerAccount(result.data.accessToken)
                    _state.update {
                        it.copy(
                            isAuthPolling = false,
                            deviceUserCode = null,
                            deviceVerificationUri = null,
                        )
                    }
                    if (account != null) {
                        fetchRemoteRepositories()
                    }
                    return
                }
                is Result.Error -> when (val error = result.error) {
                    GithubDeviceAuthError.AuthorizationPending -> Unit
                    GithubDeviceAuthError.SlowDown -> intervalSeconds += DEVICE_AUTH_SLOW_DOWN_SECONDS
                    GithubDeviceAuthError.AccessDenied,
                    GithubDeviceAuthError.ExpiredToken,
                    is GithubDeviceAuthError.Network,
                    -> {
                        _state.update {
                            it.copy(
                                isAuthPolling = false,
                                error = "GitHub sign-in failed: ${error.toMessage()}",
                                authMessage = null,
                            )
                        }
                        return
                    }
                }
            }
        }
        _state.update {
            it.copy(
                isAuthPolling = false,
                error = "GitHub sign-in expired. Start again to get a new code.",
                authMessage = null,
            )
        }
    }

    private fun cancelDeviceAuth() {
        deviceAuthJob?.cancel()
        _state.update {
            it.copy(
                isAuthPolling = false,
                authMessage = null,
                deviceUserCode = null,
                deviceVerificationUri = null,
            )
        }
    }

    private suspend fun saveEnteredTokenIfNeeded(): Boolean {
        val token = _state.value.githubToken.trim()
        if (token.isNotEmpty()) {
            return registerAccount(token) != null
        }

        val accountId = _state.value.activeAccountId
        if (accountId != null && !accountTokenStore.getToken(accountId).isNullOrBlank()) {
            return true
        }

        _state.update { it.copy(error = "Enter a token or sign in with GitHub first", isLoading = false) }
        return false
    }

    private suspend fun loadSelection(
        repositoryId: Long?,
        branchName: String?,
        commitSha: String?,
        filePath: String?,
        phonePane: PhonePane?,
    ) {
        val accounts = repositoryStore.getAccounts()
            .dataOrNull { "Unable to load accounts: ${it.toMessage()}" }
            ?: return
        val activeAccount = when (val result = repositoryStore.getActiveAccount()) {
            is Result.Error -> {
                _state.update { it.copy(isLoading = false, error = "Unable to load accounts: ${result.error.toMessage()}") }
                return
            }
            is Result.Success -> result.data
        }
        val hasToken = activeAccount != null && !accountTokenStore.getToken(activeAccount.id).isNullOrBlank()
        // With no signed-in account (fresh install / demo data) fall back to
        // every stored repository instead of an account-scoped view.
        val repositoriesResult = if (activeAccount != null) {
            repositoryStore.getRepositoriesForAccount(activeAccount.id)
        } else {
            repositoryStore.getRepositories()
        }
        val repositories = repositoriesResult
            .dataOrNull { "Unable to load repositories: ${it.toMessage()}" }
            ?: return
        val downloadedRepositoryIds = when (val result = repositoryStore.getDownloadedRepositoryIds()) {
            is Result.Success -> result.data
            is Result.Error -> emptySet()
        }
        val repository = repositories.firstOrNull { it.id == repositoryId } ?: repositories.firstOrNull()
        if (repository == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    accounts = accounts,
                    activeAccountId = activeAccount?.id,
                    hasToken = hasToken,
                    repositories = emptyList(),
                    selectedRepositoryId = null,
                    branches = emptyList(),
                    selectedBranchName = null,
                    commits = emptyList(),
                    selectedCommitSha = null,
                    files = emptyList(),
                    selectedFilePath = null,
                    fileDiffs = emptyList(),
                    downloadJob = null,
                )
            }
            return
        }

        val branches = repositoryStore.getBranches(repository.id)
            .dataOrNull { "Unable to load branches: ${it.toMessage()}" }
            ?: return
        val branch = branches.firstOrNull { it.name == branchName } ?: branches.firstOrNull()

        val commits = if (branch == null) {
            emptyList()
        } else {
            repositoryStore.getCommits(repositoryId = repository.id, branchName = branch.name)
                .dataOrNull { "Unable to load commits: ${it.toMessage()}" }
                ?: return
        }
        val commit = commits.firstOrNull { it.sha == commitSha } ?: commits.firstOrNull()
        val details = if (commit == null) {
            null
        } else {
            repositoryStore.getCommitDetails(commit.sha)
                .dataOrNull { "Unable to load commit details: ${it.toMessage()}" }
                ?: return
        }
        val downloadJob = if (branch == null) {
            null
        } else {
            when (val result = repositoryStore.getDownloadJob(repositoryId = repository.id, branchName = branch.name)) {
                is Result.Error -> {
                    _state.update { it.copy(isLoading = false, error = "Unable to load download status: ${result.error.toMessage()}") }
                    return
                }
                is Result.Success -> result.data
            }
        }
        val files = details?.files.orEmpty()
        val selectedFile = files.firstOrNull { it.path == filePath } ?: files.firstOrNull()
        // Load every file's diff up front so the whole commit renders as one
        // scrollable surface. A file whose diff fails keeps its slot with an
        // inline error instead of aborting the rest of the commit.
        val fileDiffs = if (commit == null) {
            emptyList()
        } else {
            files.map { file ->
                when (val result = diffProvider.getDiff(commitSha = commit.sha, file = file)) {
                    is Result.Success -> CommitFileDiff(file = file, diff = result.data)
                    is Result.Error -> CommitFileDiff(file = file, diff = null, errorMessage = result.error.toMessage())
                }
            }
        }

        _state.update {
            it.copy(
                isLoading = false,
                accounts = accounts,
                activeAccountId = activeAccount?.id,
                hasToken = hasToken,
                repositories = repositories,
                downloadedRepositoryIds = downloadedRepositoryIds,
                selectedRepositoryId = repository.id,
                branches = branches,
                selectedBranchName = branch?.name,
                commits = commits,
                selectedCommitSha = commit?.sha,
                files = files,
                selectedFilePath = selectedFile?.path,
                fileDiffs = fileDiffs,
                phonePane = phonePane ?: it.phonePane,
                downloadJob = downloadJob,
                error = null,
            )
        }
        // Remember the resolved position so a killed process restores it.
        repositoryStore.saveSelection(
            StoredSelection(
                repositoryId = repository.id,
                branchName = branch?.name,
                commitSha = commit?.sha,
                filePath = selectedFile?.path,
            ),
        )
    }

    private suspend fun seedDemoIfEmpty() {
        val repositories = when (val result = repositoryStore.getRepositories()) {
            is Result.Error -> return
            is Result.Success -> result.data
        }
        if (repositories.isNotEmpty()) {
            return
        }

        repositoryStore.upsertRepositories(listOf(DEMO_REPOSITORY))
        repositoryStore.upsertBranches(
            repositoryId = DEMO_REPOSITORY.id,
            branches = listOf(DEMO_BRANCH),
        )
        repositoryStore.upsertCommits(
            repositoryId = DEMO_REPOSITORY.id,
            branchName = DEMO_BRANCH.name,
            commits = listOf(DEMO_COMMIT),
        )
        repositoryStore.upsertCommitDetails(
            repositoryId = DEMO_REPOSITORY.id,
            commit = DEMO_COMMIT_DETAILS,
        )
        patchStore.writePatch(
            commitSha = DEMO_COMMIT.sha,
            path = DEMO_FILE.path,
            patch = DEMO_PATCH,
        )
    }

    private fun selectedRepository(): GithubRepository? = _state.value.repositories
        .firstOrNull { it.id == _state.value.selectedRepositoryId }

    private fun selectedBranch(): GithubBranch? = _state.value.branches
        .firstOrNull { it.name == _state.value.selectedBranchName }

    private fun <T, E : Error> Result<T, E>.dataOrNull(message: (E) -> String): T? = when (this) {
        is Result.Error -> {
            _state.update { it.copy(isLoading = false, error = message(error)) }
            null
        }
        is Result.Success -> data
    }

    private fun DataError.toMessage(): String = when (this) {
        is DataError.Local -> toMessage()
        is DataError.Network -> toMessage()
    }

    private fun DataError.Local.toMessage(): String = when (this) {
        DataError.Local.DISK_FULL -> "disk is full"
        DataError.Local.NOT_FOUND -> "item not found"
        DataError.Local.UNKNOWN -> "local storage error"
    }

    private fun DataError.Network.toMessage(): String = when (this) {
        DataError.Network.BAD_REQUEST -> "bad request"
        DataError.Network.REQUEST_TIMEOUT -> "request timed out"
        DataError.Network.UNAUTHORIZED -> "missing or invalid token"
        DataError.Network.FORBIDDEN -> "forbidden"
        DataError.Network.NOT_FOUND -> "not found"
        DataError.Network.CONFLICT -> "request conflict"
        DataError.Network.TOO_MANY_REQUESTS -> "GitHub rate limit reached"
        DataError.Network.NO_INTERNET -> "no internet connection"
        DataError.Network.PAYLOAD_TOO_LARGE -> "payload too large"
        DataError.Network.SERVER_ERROR -> "GitHub server error"
        DataError.Network.SERVICE_UNAVAILABLE -> "GitHub unavailable"
        DataError.Network.SERIALIZATION -> "unexpected GitHub response"
        DataError.Network.UNKNOWN -> "network error"
    }

    private fun DiffError.toMessage(): String = when (this) {
        DiffError.InvalidPatch -> "invalid patch"
        is DiffError.Storage -> error.toMessage()
    }

    private fun GithubDeviceAuthError.toMessage(): String = when (this) {
        GithubDeviceAuthError.AccessDenied -> "authorization was denied"
        GithubDeviceAuthError.AuthorizationPending -> "authorization is still pending"
        GithubDeviceAuthError.ExpiredToken -> "device code expired"
        is GithubDeviceAuthError.Network -> error.toMessage()
        GithubDeviceAuthError.SlowDown -> "GitHub asked us to poll more slowly"
    }

    private fun PhonePane.previous(): PhonePane = when (this) {
        PhonePane.REPOSITORIES -> PhonePane.REPOSITORIES
        PhonePane.BRANCHES -> PhonePane.REPOSITORIES
        PhonePane.COMMITS -> PhonePane.BRANCHES
        PhonePane.FILES -> PhonePane.COMMITS
        PhonePane.DIFF -> PhonePane.FILES
    }

    private companion object {
        const val DEFAULT_COMMIT_LIMIT = 100
        const val DEVICE_AUTH_SLOW_DOWN_SECONDS = 5
        const val MAX_COMMIT_LIMIT = 500
        const val MIN_DEVICE_AUTH_INTERVAL_SECONDS = 5
        val DEMO_REPOSITORY = GithubRepository(
            id = 1,
            owner = "octocat",
            name = "offline-demo",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = "2026-07-06T00:00:00Z",
        )
        val DEMO_BRANCH = GithubBranch(
            name = "main",
            headSha = "demo-commit",
        )
        val DEMO_COMMIT = GithubCommitSummary(
            sha = "demo-commit",
            firstParentSha = "demo-parent",
            authorName = "Offline Git",
            authorEmail = "offline@example.local",
            message = "Render cached unified diff",
            committedAt = "2026-07-06T00:00:00Z",
        )
        val DEMO_PATCH = """
            @@ -1,3 +1,4 @@
             # Offline GitHub Viewer
            -Placeholder Android shell.
            +Local SQLDelight metadata is wired.
            +Cached GitHub patches render as unified diffs.
             More sync work comes next.
        """.trimIndent()
        val DEMO_FILE = GithubCommitFile(
            path = "README.md",
            previousPath = null,
            status = "modified",
            additions = 2,
            deletions = 1,
            blobSha = "demo-blob",
            patch = DEMO_PATCH,
        )
        val DEMO_COMMIT_DETAILS = GithubCommitDetails(
            sha = DEMO_COMMIT.sha,
            firstParentSha = DEMO_COMMIT.firstParentSha,
            authorName = DEMO_COMMIT.authorName,
            authorEmail = DEMO_COMMIT.authorEmail,
            message = DEMO_COMMIT.message,
            committedAt = DEMO_COMMIT.committedAt,
            files = listOf(DEMO_FILE),
        )
    }
}

@Stable
data class OfflineGitState(
    val accounts: List<GithubAccount> = emptyList(),
    val activeAccountId: Long? = null,
    val repositories: List<GithubRepository> = emptyList(),
    val downloadedRepositoryIds: Set<Long> = emptySet(),
    val repositorySearchQuery: String = "",
    val selectedRepositoryId: Long? = null,
    val branches: List<GithubBranch> = emptyList(),
    val selectedBranchName: String? = null,
    val commits: List<GithubCommitSummary> = emptyList(),
    val selectedCommitSha: String? = null,
    val files: List<GithubCommitFile> = emptyList(),
    val selectedFilePath: String? = null,
    val fileDiffs: List<CommitFileDiff> = emptyList(),
    val viewerMode: DiffViewerMode = DiffViewerMode.UNIFIED,
    val phonePane: PhonePane = PhonePane.REPOSITORIES,
    val downloadJob: BranchDownloadJob? = null,
    val githubToken: String = "",
    val hasToken: Boolean = false,
    val authClientId: String = "",
    val authMessage: String? = null,
    val deviceUserCode: String? = null,
    val deviceVerificationUri: String? = null,
    val isAuthPolling: Boolean = false,
    val commitLimitText: String = "100",
    val syncStatus: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** One changed file in the selected commit, with its diff or the reason it failed to load. */
data class CommitFileDiff(
    val file: GithubCommitFile,
    val diff: DiffFile?,
    val errorMessage: String? = null,
)

sealed interface OfflineGitAction {
    data object OnRetryClick : OfflineGitAction
    data object OnFetchRepositoriesClick : OfflineGitAction
    data object OnFetchBranchesClick : OfflineGitAction
    data object OnDownloadBranchClick : OfflineGitAction
    data object OnStartDeviceAuthClick : OfflineGitAction
    data object OnCancelDeviceAuthClick : OfflineGitAction
    data class OnAccountClick(val accountId: Long) : OfflineGitAction
    data class OnRemoveAccountClick(val accountId: Long) : OfflineGitAction
    data object OnPhoneBackClick : OfflineGitAction
    data class OnTokenChange(val token: String) : OfflineGitAction
    data class OnAuthClientIdChange(val clientId: String) : OfflineGitAction
    data class OnCommitLimitChange(val limit: String) : OfflineGitAction
    data class OnRepositorySearchChange(val query: String) : OfflineGitAction
    data class OnRepositoryClick(val repositoryId: Long) : OfflineGitAction
    data class OnBranchClick(val branchName: String) : OfflineGitAction
    data class OnCommitClick(val commitSha: String) : OfflineGitAction
    data class OnFileClick(val path: String) : OfflineGitAction
    data class OnViewerModeClick(val mode: DiffViewerMode) : OfflineGitAction
}

enum class DiffViewerMode {
    UNIFIED,
    SIDE_BY_SIDE,
}

enum class PhonePane {
    REPOSITORIES,
    BRANCHES,
    COMMITS,
    FILES,
    DIFF,
}
