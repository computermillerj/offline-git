package dev.jem.offlinegit.presentation

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jem.offlinegit.domain.BranchDownloadProgress
import dev.jem.offlinegit.domain.BranchDownloader
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.DiffError
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffProvider
import dev.jem.offlinegit.domain.Error
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OfflineGitViewModel(
    private val repositoryStore: RepositoryStore,
    private val patchStore: PatchStore,
    private val diffProvider: DiffProvider,
    private val remoteDataSource: GithubRemoteDataSource,
    private val branchDownloader: BranchDownloader,
    private val tokenStore: MutableTokenStore,
    private val closeRemoteResources: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(OfflineGitState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    override fun onCleared() {
        closeRemoteResources()
        super.onCleared()
    }

    fun onAction(action: OfflineGitAction) {
        when (action) {
            OfflineGitAction.OnRetryClick -> load()
            OfflineGitAction.OnFetchRepositoriesClick -> fetchRemoteRepositories()
            OfflineGitAction.OnFetchBranchesClick -> fetchRemoteBranches()
            OfflineGitAction.OnDownloadBranchClick -> downloadSelectedBranch()
            is OfflineGitAction.OnTokenChange -> {
                tokenStore.token = action.token
                _state.update { it.copy(githubToken = action.token) }
            }
            is OfflineGitAction.OnRepositoryClick -> load(
                repositoryId = action.repositoryId,
                branchName = null,
                commitSha = null,
                filePath = null,
            )
            is OfflineGitAction.OnBranchClick -> load(
                branchName = action.branchName,
                commitSha = null,
                filePath = null,
            )
            is OfflineGitAction.OnCommitClick -> load(commitSha = action.commitSha, filePath = null)
            is OfflineGitAction.OnFileClick -> load(filePath = action.path)
        }
    }

    private fun load(
        repositoryId: Long? = _state.value.selectedRepositoryId,
        branchName: String? = _state.value.selectedBranchName,
        commitSha: String? = _state.value.selectedCommitSha,
        filePath: String? = _state.value.selectedFilePath,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            seedDemoIfEmpty()
            loadSelection(
                repositoryId = repositoryId,
                branchName = branchName,
                commitSha = commitSha,
                filePath = filePath,
            )
        }
    }

    private fun fetchRemoteRepositories() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, syncStatus = "Fetching GitHub repositories…") }
            val repositories = remoteDataSource.getRepositories()
                .dataOrNull { "Unable to fetch repositories: ${it.toMessage()}" }
                ?: return@launch
            repositoryStore.upsertRepositories(repositories)
                .dataOrNull { "Unable to save repositories: ${it.toMessage()}" }
                ?: return@launch
            _state.update { it.copy(syncStatus = "Fetched ${repositories.size} repositories") }
            loadSelection(
                repositoryId = repositories.firstOrNull()?.id ?: _state.value.selectedRepositoryId,
                branchName = null,
                commitSha = null,
                filePath = null,
            )
        }
    }

    private fun fetchRemoteBranches() {
        viewModelScope.launch {
            val repository = selectedRepository() ?: run {
                _state.update { it.copy(error = "Select a repository first") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, error = null, syncStatus = "Fetching branches for ${repository.name}…") }
            val branches = remoteDataSource.getBranches(owner = repository.owner, repository = repository.name)
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
            )
        }
    }

    private fun downloadSelectedBranch() {
        viewModelScope.launch {
            val repository = selectedRepository() ?: run {
                _state.update { it.copy(error = "Select a repository first") }
                return@launch
            }
            val branch = selectedBranch() ?: run {
                _state.update { it.copy(error = "Select a branch first") }
                return@launch
            }

            branchDownloader.downloadBranch(
                repository = repository,
                branch = branch,
                commitLimit = DEFAULT_COMMIT_LIMIT,
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

    private suspend fun loadSelection(
        repositoryId: Long?,
        branchName: String?,
        commitSha: String?,
        filePath: String?,
    ) {
        val repositories = repositoryStore.getRepositories()
            .dataOrNull { "Unable to load repositories: ${it.toMessage()}" }
            ?: return
        val repository = repositories.firstOrNull { it.id == repositoryId } ?: repositories.firstOrNull()
        if (repository == null) {
            _state.update { it.copy(isLoading = false, repositories = emptyList()) }
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
        val files = details?.files.orEmpty()
        val selectedFile = files.firstOrNull { it.path == filePath } ?: files.firstOrNull()
        val diff = if (commit == null || selectedFile == null) {
            null
        } else {
            diffProvider.getDiff(commitSha = commit.sha, file = selectedFile)
                .dataOrNull { "Unable to load diff: ${it.toMessage()}" }
        }

        _state.update {
            it.copy(
                isLoading = false,
                repositories = repositories,
                selectedRepositoryId = repository.id,
                branches = branches,
                selectedBranchName = branch?.name,
                commits = commits,
                selectedCommitSha = commit?.sha,
                files = files,
                selectedFilePath = selectedFile?.path,
                diff = diff,
                error = null,
            )
        }
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

    private companion object {
        const val DEFAULT_COMMIT_LIMIT = 25
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

class MutableTokenStore : TokenStore {
    var token: String = ""

    override suspend fun getToken(): String? = token
}

@Stable
data class OfflineGitState(
    val repositories: List<GithubRepository> = emptyList(),
    val selectedRepositoryId: Long? = null,
    val branches: List<GithubBranch> = emptyList(),
    val selectedBranchName: String? = null,
    val commits: List<GithubCommitSummary> = emptyList(),
    val selectedCommitSha: String? = null,
    val files: List<GithubCommitFile> = emptyList(),
    val selectedFilePath: String? = null,
    val diff: DiffFile? = null,
    val githubToken: String = "",
    val syncStatus: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface OfflineGitAction {
    data object OnRetryClick : OfflineGitAction
    data object OnFetchRepositoriesClick : OfflineGitAction
    data object OnFetchBranchesClick : OfflineGitAction
    data object OnDownloadBranchClick : OfflineGitAction
    data class OnTokenChange(val token: String) : OfflineGitAction
    data class OnRepositoryClick(val repositoryId: Long) : OfflineGitAction
    data class OnBranchClick(val branchName: String) : OfflineGitAction
    data class OnCommitClick(val commitSha: String) : OfflineGitAction
    data class OnFileClick(val path: String) : OfflineGitAction
}
