package dev.jem.offlinegit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.DiffSource
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository

private val WideLayoutBreakpoint = 840.dp

@Composable
fun OfflineGitRoot(
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: OfflineGitViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OfflineGitTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            OfflineGitScreen(
                state = state,
                onAction = viewModel::onAction,
            )
        }
    }
}

@Composable
fun OfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    var showConnectSheet by remember { mutableStateOf(false) }

    // Close the connect sheet once a token is stored (sign-in succeeded).
    LaunchedEffect(state.hasToken) {
        if (state.hasToken && !state.isAuthPolling) {
            showConnectSheet = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= WideLayoutBreakpoint) {
            WideOfflineGitScreen(
                state = state,
                onAction = onAction,
                onConnectClick = { showConnectSheet = true },
            )
        } else {
            PhoneOfflineGitScreen(
                state = state,
                onAction = onAction,
                onConnectClick = { showConnectSheet = true },
            )
        }
    }

    if (showConnectSheet) {
        ConnectAccountSheet(
            state = state,
            onAction = onAction,
            onDismiss = { showConnectSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneOfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onConnectClick: () -> Unit,
) {
    Scaffold(
        topBar = { PhoneTopBar(state = state, onAction = onAction) },
        floatingActionButton = {
            if (state.phonePane == PhonePane.REPOSITORIES) {
                ExtendedFloatingActionButton(
                    text = { Text(text = if (state.hasToken) "Account" else "Connect account") },
                    icon = { Text(text = if (state.hasToken) "◉" else "＋") },
                    onClick = onConnectClick,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            statusStripItem(
                error = state.error,
                syncStatus = state.syncStatus,
                onRetryClick = { onAction(OfflineGitAction.OnRetryClick) },
            )
            when (state.phonePane) {
                PhonePane.REPOSITORIES -> {
                    item {
                        RepositorySection(
                            state = state,
                            onAction = onAction,
                        )
                    }
                    item {
                        SyncControls(
                            state = state,
                            onAction = onAction,
                            showBranchControls = false,
                        )
                    }
                }
                PhonePane.BRANCHES -> {
                    item {
                        BranchSection(
                            state = state,
                            onAction = onAction,
                        )
                    }
                    item {
                        DownloadOptionsCard(
                            state = state,
                            onAction = onAction,
                        )
                    }
                }
                PhonePane.COMMITS -> {
                    item {
                        CommitSection(
                            state = state,
                            onAction = onAction,
                        )
                    }
                    item {
                        SyncControls(
                            state = state,
                            onAction = onAction,
                            showRefreshRepos = false,
                        )
                    }
                }
                PhonePane.FILES -> {
                    item {
                        ChangedFilesSection(
                            state = state,
                            onAction = onAction,
                        )
                    }
                }
                PhonePane.DIFF -> {
                    val diff = state.diff
                    if (diff == null) {
                        item { EmptyDiffCard() }
                    } else {
                        item {
                            DiffViewerHeader(
                                diff = diff,
                                selectedFile = state.selectedFile(),
                                viewerMode = state.viewerMode,
                                onAction = onAction,
                            )
                        }
                        diffItems(
                            diff = diff,
                            viewerMode = state.viewerMode,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTopBar(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    val onBack = { onAction(OfflineGitAction.OnPhoneBackClick) }
    when (state.phonePane) {
        PhonePane.REPOSITORIES -> OfflineGitTopBar(
            title = "Repositories",
            subtitle = state.repositoriesSubtitle(),
            isLoading = state.isLoading,
            actions = { StatusChip("Offline ready", ChipStyle.OK, showDot = true) },
        )
        PhonePane.BRANCHES -> OfflineGitTopBar(
            title = state.selectedRepositoryName(),
            subtitle = "Choose a branch to download",
            onBackClick = onBack,
            isLoading = state.isLoading,
        )
        PhonePane.COMMITS -> OfflineGitTopBar(
            title = state.selectedBranchName ?: "Commits",
            subtitle = state.commitsSubtitle(),
            onBackClick = onBack,
            isLoading = state.isLoading,
            actions = state.offlineChip(),
        )
        PhonePane.FILES -> OfflineGitTopBar(
            title = state.selectedCommitSha?.take(7) ?: "Changed files",
            titleMono = state.selectedCommitSha != null,
            subtitle = "${state.files.size} files changed",
            onBackClick = onBack,
            isLoading = state.isLoading,
            actions = state.offlineChip(),
        )
        PhonePane.DIFF -> OfflineGitTopBar(
            title = state.selectedFilePath?.substringAfterLast('/') ?: "Diff",
            titleMono = true,
            subtitle = state.selectedFilePath.orEmpty(),
            onBackClick = onBack,
            isLoading = state.isLoading,
            actions = { StatusChip(state.viewerMode.label(), ChipStyle.ACCENT) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineGitTopBar(
    title: String,
    subtitle: String,
    onBackClick: (() -> Unit)? = null,
    titleMono: Boolean = false,
    isLoading: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (titleMono) FontFamily.Monospace else FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            actions()
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

private fun DiffViewerMode.label(): String = when (this) {
    DiffViewerMode.UNIFIED -> "unified"
    DiffViewerMode.SIDE_BY_SIDE -> "side-by-side"
}

private fun DiffSource.sourceLabel(): String = when (this) {
    DiffSource.CACHED_PATCH -> "source: cached patch"
    DiffSource.LOCAL_BLOB -> "source: local blob diff"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideOfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onConnectClick: () -> Unit,
) {
    Scaffold(
        topBar = { WideTopBar(state = state, onAction = onAction, onConnectClick = onConnectClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leftPaneItems(
                    state = state,
                    onAction = onAction,
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            DiffViewerPane(
                state = state,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideTopBar(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onConnectClick: () -> Unit,
) {
    val title = listOfNotNull(
        state.selectedRepository()?.name,
        state.selectedBranchName,
    ).joinToString(" · ").ifBlank { "Offline GitHub Viewer" }
    OfflineGitTopBar(
        title = title,
        subtitle = state.commitsSubtitle(),
        isLoading = state.isLoading,
        actions = {
            DiffViewerMode.entries.forEach { mode ->
                StatusChip(
                    text = mode.label(),
                    style = if (state.viewerMode == mode) ChipStyle.ACCENT else ChipStyle.NEUTRAL,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable { onAction(OfflineGitAction.OnViewerModeClick(mode)) },
                )
            }
            state.offlineChip()()
            TextButton(onClick = onConnectClick) {
                Text(text = if (state.hasToken) "Account" else "Connect")
            }
        },
    )
}

private fun LazyListScope.leftPaneItems(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    statusStripItem(
        error = state.error,
        syncStatus = state.syncStatus,
        onRetryClick = { onAction(OfflineGitAction.OnRetryClick) },
    )
    item {
        SectionLabel(
            text = "Repositories",
            action = if (state.hasToken) {
                {
                    TextButton(
                        onClick = { onAction(OfflineGitAction.OnFetchRepositoriesClick) },
                        enabled = !state.isLoading,
                    ) {
                        Text(text = "Refresh")
                    }
                }
            } else {
                null
            },
        )
    }
    item { RepositorySection(state = state, onAction = onAction) }
    item { SectionLabel(text = "Branches") }
    item { BranchSection(state = state, onAction = onAction) }
    item { DownloadOptionsCard(state = state, onAction = onAction) }
    item { SectionLabel(text = "Commits") }
    item { CommitSection(state = state, onAction = onAction) }
    item { SectionLabel(text = "Files · ${state.files.size} changed") }
    item { ChangedFilesSection(state = state, onAction = onAction) }
}

@Composable
private fun SectionLabel(
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        action?.invoke()
    }
}

private fun LazyListScope.diffItems(
    diff: DiffFile,
    viewerMode: DiffViewerMode,
) {
    when (viewerMode) {
        DiffViewerMode.UNIFIED -> items(
            items = diff.hunks,
            key = { hunk -> "unified:${hunk.oldStart}:${hunk.newStart}" },
        ) { hunk ->
            UnifiedDiffHunkCard(hunk = hunk)
        }
        DiffViewerMode.SIDE_BY_SIDE -> items(
            items = diff.hunks,
            key = { hunk -> "side:${hunk.oldStart}:${hunk.newStart}" },
        ) { hunk ->
            SideBySideDiffHunkCard(hunk = hunk)
        }
    }
}

@Composable
private fun DiffViewerPane(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val diff = state.diff
        if (diff == null) {
            EmptyDiffCard()
            return@Column
        }

        DiffViewerHeader(
            diff = diff,
            selectedFile = state.selectedFile(),
            viewerMode = state.viewerMode,
            onAction = onAction,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            diffItems(
                diff = diff,
                viewerMode = state.viewerMode,
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetryClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onRetryClick) {
                Text(text = "Retry")
            }
        }
    }
}

private fun LazyListScope.statusStripItem(
    error: String?,
    syncStatus: String?,
    onRetryClick: () -> Unit,
) {
    if (error != null) {
        item {
            ErrorBanner(
                message = error,
                onRetryClick = onRetryClick,
            )
        }
    }
    if (syncStatus != null) {
        item {
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectAccountSheet(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connect a GitHub account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.hasToken) {
                Text(
                    text = "GitHub token saved securely",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = state.authClientId,
                onValueChange = { onAction(OfflineGitAction.OnAuthClientIdChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "GitHub OAuth client ID") },
                singleLine = true,
                enabled = !state.isAuthPolling,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = state.authClientId.isNotBlank() && !state.isAuthPolling && !state.isLoading,
                    onClick = { onAction(OfflineGitAction.OnStartDeviceAuthClick) },
                ) {
                    Text(text = "Device sign in")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = state.isAuthPolling,
                    onClick = { onAction(OfflineGitAction.OnCancelDeviceAuthClick) },
                ) {
                    Text(text = "Cancel")
                }
            }
            if (state.deviceUserCode != null && state.deviceVerificationUri != null) {
                DeviceCodeCard(
                    userCode = state.deviceUserCode,
                    verificationUri = state.deviceVerificationUri,
                )
            }
            state.authMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.githubToken,
                onValueChange = { onAction(OfflineGitAction.OnTokenChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Personal access token fallback") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.githubToken.isNotBlank() && !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchRepositoriesClick) },
            ) {
                Text(text = "Save token & sync")
            }
            if (state.hasToken) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    onClick = {
                        onAction(OfflineGitAction.OnSignOutClick)
                        onDismiss()
                    },
                ) {
                    Text(text = "Sign out")
                }
            }
        }
    }
}

@Composable
private fun SyncControls(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    showRefreshRepos: Boolean = true,
    showBranchControls: Boolean = true,
) {
    if (!state.hasToken) {
        return
    }
    SectionCard(title = "Sync") {
        if (showRefreshRepos) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchRepositoriesClick) },
            ) {
                Text(text = "Refresh repositories")
            }
        }
        if (showBranchControls && state.selectedRepositoryId != null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchBranchesClick) },
            ) {
                Text(text = "Refresh branches")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedBranchName != null && !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnDownloadBranchClick) },
            ) {
                Text(text = "Download / refresh selected branch")
            }
        }
        state.downloadJob?.let { downloadJob ->
            Text(
                text = downloadJob.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = when (downloadJob.status) {
                    BranchDownloadJobStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    BranchDownloadJobStatus.FAILED -> MaterialTheme.colorScheme.error
                    BranchDownloadJobStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DeviceCodeCard(
    userCode: String,
    verificationUri: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = userCode,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = verificationUri,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RepositorySection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.repositorySearchQuery,
            onValueChange = { onAction(OfflineGitAction.OnRepositorySearchChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Search repositories…") },
            singleLine = true,
        )
        val query = state.repositorySearchQuery.trim()
        val repositories = if (query.isEmpty()) {
            state.repositories
        } else {
            state.repositories.filter {
                it.name.contains(query, ignoreCase = true) || it.owner.contains(query, ignoreCase = true)
            }
        }
        if (repositories.isEmpty()) {
            EmptySectionText(
                text = if (state.repositories.isEmpty()) {
                    "Connect a GitHub account to sync repositories."
                } else {
                    "No repositories match \"$query\"."
                },
            )
        }
        repositories.forEach { repository ->
            RepositoryRow(
                repository = repository,
                isSelected = repository.id == state.selectedRepositoryId,
                isDownloaded = repository.id in state.downloadedRepositoryIds,
                onClick = { onAction(OfflineGitAction.OnRepositoryClick(repository.id)) },
            )
        }
    }
}

@Composable
private fun RepositoryRow(
    repository: GithubRepository,
    isSelected: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarTile(label = repository.name)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = repository.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDownloaded) {
                    StatusChip(text = "downloaded", style = ChipStyle.ACCENT)
                }
                if (repository.isPrivate) {
                    StatusChip(text = "private", style = ChipStyle.NEUTRAL)
                }
            }
            Text(
                text = repository.metadataLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun GithubRepository.metadataLine(): String {
    val updated = relativeTime(updatedAt)?.let { "updated $it" }
    return listOfNotNull(defaultBranch, updated).joinToString(" · ")
}

@Composable
private fun BranchSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    val defaultBranch = state.selectedRepository()?.defaultBranch
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.branches.isEmpty()) {
            EmptySectionText(text = "Refresh branches to choose one to download.")
        }
        state.branches.forEach { branch ->
            BranchRow(
                branch = branch,
                isDefault = branch.name == defaultBranch,
                isSelected = branch.name == state.selectedBranchName,
                onClick = { onAction(OfflineGitAction.OnBranchClick(branch.name)) },
            )
        }
        if (state.hasToken) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedRepositoryId != null && !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchBranchesClick) },
            ) {
                Text(text = "Refresh branches")
            }
        }
    }
}

@Composable
private fun BranchRow(
    branch: GithubBranch,
    isDefault: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDefault) {
                    StatusChip(text = "default", style = ChipStyle.ACCENT)
                }
            }
            Text(
                text = "HEAD ${branch.headSha.take(7)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (isSelected) {
            StatusChip(text = "selected", style = ChipStyle.OK, showDot = true)
        }
    }
}

private val CommitDepthOptions = listOf(50, 100, 250)

@Composable
private fun DownloadOptionsCard(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    SectionCard(title = "Download options") {
        val selectedDepth = state.commitLimitText.toIntOrNull()
        SegmentedControl(
            options = CommitDepthOptions,
            selected = selectedDepth,
            label = { if (it == 100) "100 commits" else it.toString() },
            onSelect = { onAction(OfflineGitAction.OnCommitLimitChange(it.toString())) },
        )
        OptionToggleRow(text = "Skip binary & LFS files", checked = true)
        OptionToggleRow(text = "Cache blobs for side-by-side diff", checked = true)
        Text(
            text = "Binary/LFS files are always skipped and text blobs cached in this build.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.hasToken && state.selectedBranchName != null && !state.isLoading,
            onClick = { onAction(OfflineGitAction.OnDownloadBranchClick) },
        ) {
            Text(
                text = state.selectedBranchName
                    ?.let { "Download $it" }
                    ?: "Select a branch to download",
            )
        }
        state.downloadJob?.let { downloadJob ->
            DownloadProgress(job = downloadJob, syncStatus = state.syncStatus)
        }
    }
}

@Composable
private fun DownloadProgress(
    job: BranchDownloadJob,
    syncStatus: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (job.status == BranchDownloadJobStatus.RUNNING) {
            LinearProgressIndicator(
                progress = { job.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = syncStatus ?: job.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = when (job.status) {
                    BranchDownloadJobStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    BranchDownloadJobStatus.FAILED -> MaterialTheme.colorScheme.error
                    BranchDownloadJobStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (job.status == BranchDownloadJobStatus.RUNNING) {
                Text(
                    text = "${(job.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isOn = option == selected
            val border = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            val container = if (isOn) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val content = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(container)
                    .border(1.dp, border, RoundedCornerShape(9.dp))
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun OptionToggleRow(
    text: String,
    checked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun CommitSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.commits.isEmpty()) {
            EmptySectionText(text = "Download a branch to browse commits offline.")
        }
        state.commits.forEach { commit ->
            CommitRow(
                commit = commit,
                isSelected = commit.sha == state.selectedCommitSha,
                onClick = { onAction(OfflineGitAction.OnCommitClick(commit.sha)) },
            )
        }
    }
}

@Composable
private fun CommitRow(
    commit: GithubCommitSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AvatarTile(label = commit.authorName ?: commit.sha)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = commit.message.lineSequence().firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (commit.isMerge) {
                    StatusChip(text = "merge · first-parent", style = ChipStyle.NEUTRAL)
                }
            }
            Text(
                text = commit.metadataLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GithubCommitSummary.metadataLine() = buildAnnotatedString {
    val parts = listOfNotNull(sha.take(7), authorName, relativeTime(committedAt))
    append(parts.joinToString(" · "))
    if (!isMerge && (additions > 0 || deletions > 0)) {
        append("  ")
        withStyle(SpanStyle(color = OfflineGitPalette.Add)) { append("+$additions") }
        append(" ")
        withStyle(SpanStyle(color = OfflineGitPalette.Del)) { append("−$deletions") }
    }
}

@Composable
private fun ChangedFilesSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.selectedCommit()?.let { commit ->
            CommitMessageBlock(commit)
        }
        if (state.files.isEmpty()) {
            EmptySectionText(text = "Select a commit to see changed files.")
        }
        state.files.forEach { file ->
            ChangedFileRow(
                file = file,
                isSelected = file.path == state.selectedFilePath,
                onClick = { onAction(OfflineGitAction.OnFileClick(file.path)) },
            )
        }
    }
}

@Composable
private fun CommitMessageBlock(commit: GithubCommitSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = commit.message.lineSequence().firstOrNull().orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val byline = listOfNotNull(
            commit.authorName,
            commit.authorEmail,
            absoluteDateTime(commit.committedAt),
        ).joinToString(" · ")
        if (byline.isNotBlank()) {
            Text(
                text = byline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChangedFileRow(
    file: GithubCommitFile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusSquare(status = file.status)
        Text(
            text = file.previousPath?.let { "$it → ${file.path}" } ?: file.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = OfflineGitPalette.Add)) { append("+${file.additions}") }
                append(" ")
                withStyle(SpanStyle(color = OfflineGitPalette.Del)) { append("−${file.deletions}") }
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun OfflineGitState.selectedCommit(): GithubCommitSummary? =
    commits.firstOrNull { it.sha == selectedCommitSha }

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyDiffCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Diff viewer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Select a commit file to view cached changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiffViewerHeader(
    diff: DiffFile,
    selectedFile: GithubCommitFile?,
    viewerMode: DiffViewerMode,
    onAction: (OfflineGitAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(text = diff.source.sourceLabel(), style = ChipStyle.ACCENT)
            }
            Text(
                text = diff.subtitle(selectedFile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiffModeSelector(
                selectedMode = viewerMode,
                onModeClick = { mode -> onAction(OfflineGitAction.OnViewerModeClick(mode)) },
            )
        }
    }
}

@Composable
private fun DiffModeSelector(
    selectedMode: DiffViewerMode,
    onModeClick: (DiffViewerMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiffModeButton(
            text = "Unified",
            selected = selectedMode == DiffViewerMode.UNIFIED,
            onClick = { onModeClick(DiffViewerMode.UNIFIED) },
        )
        DiffModeButton(
            text = "Side-by-side",
            selected = selectedMode == DiffViewerMode.SIDE_BY_SIDE,
            onClick = { onModeClick(DiffViewerMode.SIDE_BY_SIDE) },
        )
    }
}

@Composable
private fun DiffModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text = text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text = text)
        }
    }
}

@Composable
private fun UnifiedDiffHunkCard(hunk: DiffHunk) {
    val horizontalScrollState = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .padding(vertical = 8.dp),
        ) {
            hunk.lines.forEach { line ->
                DiffLineRow(line = line)
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(line.type.backgroundColor())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldLineNumber?.toString().orEmpty(),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.newLineNumber?.toString().orEmpty(),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.prefix(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = line.type.textColor(),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            color = line.type.textColor(),
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

@Composable
private fun SideBySideDiffHunkCard(hunk: DiffHunk) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            HunkHeaderRow(text = hunk.headerText())
            hunk.toSideBySideRows().forEach { row ->
                SideBySideLineRow(row = row)
            }
        }
    }
}

@Composable
private fun HunkHeaderRow(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(DiffLineType.HUNK_HEADER.backgroundColor())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = DiffLineType.HUNK_HEADER.textColor(),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
    )
}

@Composable
private fun SideBySideLineRow(row: SideBySideDiffRow) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SideBySideCell(
            lineNumber = row.oldLineNumber,
            text = row.oldText,
            type = row.oldType,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        SideBySideCell(
            lineNumber = row.newLineNumber,
            text = row.newText,
            type = row.newType,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SideBySideCell(
    lineNumber: Int?,
    text: String?,
    type: DiffLineType?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(type.backgroundColor())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = lineNumber?.toString().orEmpty(),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = type.prefix(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = type.textColor(),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = type.textColor(),
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}

private fun DiffLineType?.backgroundColor(): Color = when (this) {
    DiffLineType.ADDITION -> DiffPalette.AdditionBackground
    DiffLineType.DELETION -> DiffPalette.DeletionBackground
    DiffLineType.HUNK_HEADER -> DiffPalette.HunkBackground
    DiffLineType.CONTEXT,
    DiffLineType.NO_NEWLINE,
    null,
    -> Color.Transparent
}

private fun DiffLineType?.textColor(): Color = when (this) {
    DiffLineType.ADDITION -> DiffPalette.AdditionText
    DiffLineType.DELETION -> DiffPalette.DeletionText
    DiffLineType.HUNK_HEADER -> DiffPalette.HunkText
    DiffLineType.CONTEXT,
    DiffLineType.NO_NEWLINE,
    null,
    -> DiffPalette.ContextText
}

private fun DiffLine.prefix(): String = type.prefix()

private fun DiffLineType?.prefix(): String = when (this) {
    DiffLineType.ADDITION -> "+"
    DiffLineType.DELETION -> "-"
    DiffLineType.HUNK_HEADER -> "@"
    DiffLineType.CONTEXT -> " "
    DiffLineType.NO_NEWLINE -> "\\"
    null -> " "
}

private fun DiffHunk.headerText(): String = lines
    .firstOrNull { it.type == DiffLineType.HUNK_HEADER }
    ?.text
    ?: "@@ -$oldStart,$oldLineCount +$newStart,$newLineCount @@"

private fun DiffHunk.toSideBySideRows(): List<SideBySideDiffRow> {
    val rows = mutableListOf<SideBySideDiffRow>()
    val pendingDeletions = ArrayDeque<DiffLine>()

    fun flushDeletions() {
        while (pendingDeletions.isNotEmpty()) {
            val deletion = pendingDeletions.removeFirst()
            rows += SideBySideDiffRow(
                oldLineNumber = deletion.oldLineNumber,
                oldText = deletion.text,
                oldType = deletion.type,
                newLineNumber = null,
                newText = null,
                newType = null,
            )
        }
    }

    lines.forEach { line ->
        when (line.type) {
            DiffLineType.DELETION -> pendingDeletions += line
            DiffLineType.ADDITION -> {
                val deletion = pendingDeletions.removeFirstOrNull()
                rows += SideBySideDiffRow(
                    oldLineNumber = deletion?.oldLineNumber,
                    oldText = deletion?.text,
                    oldType = deletion?.type,
                    newLineNumber = line.newLineNumber,
                    newText = line.text,
                    newType = line.type,
                )
            }
            DiffLineType.CONTEXT,
            DiffLineType.NO_NEWLINE,
            -> {
                flushDeletions()
                rows += SideBySideDiffRow(
                    oldLineNumber = line.oldLineNumber,
                    oldText = line.text,
                    oldType = line.type,
                    newLineNumber = line.newLineNumber,
                    newText = line.text,
                    newType = line.type,
                )
            }
            DiffLineType.HUNK_HEADER -> Unit
        }
    }
    flushDeletions()
    return rows
}

private fun <T> ArrayDeque<T>.removeFirstOrNull(): T? = if (isEmpty()) null else removeFirst()

private fun DiffFile.subtitle(selectedFile: GithubCommitFile?): String = buildList {
    add(status)
    if (selectedFile != null) {
        add("+${selectedFile.additions} -${selectedFile.deletions}")
    }
    previousPath?.let { add("from $it") }
}.joinToString(" • ")

private fun OfflineGitState.selectedFile(): GithubCommitFile? = files.firstOrNull { it.path == selectedFilePath }

private fun OfflineGitState.selectedRepository(): GithubRepository? = repositories
    .firstOrNull { it.id == selectedRepositoryId }

private fun OfflineGitState.selectedRepositoryName(): String = selectedRepository()?.name ?: "Repository"

private fun OfflineGitState.repositoriesSubtitle(): String =
    "${repositories.size} ${if (repositories.size == 1) "repo" else "repos"}"

private fun OfflineGitState.commitsSubtitle(): String = listOf(
    selectedRepository()?.name.orEmpty(),
    "${commits.size} commits",
)
    .filter { it.isNotBlank() }
    .joinToString(" · ")

private fun OfflineGitState.isOffline(): Boolean =
    downloadJob?.status == BranchDownloadJobStatus.COMPLETED || commits.isNotEmpty()

private fun OfflineGitState.offlineChip(): @Composable () -> Unit = {
    if (isOffline()) {
        StatusChip("Offline", ChipStyle.OK, showDot = true)
    }
}

private fun BranchDownloadJob.statusText(): String = when (status) {
    BranchDownloadJobStatus.RUNNING -> "Download running: ${(progress * 100).toInt()}%"
    BranchDownloadJobStatus.COMPLETED -> "Downloaded for offline use"
    BranchDownloadJobStatus.FAILED -> "Download failed: ${errorMessage.orEmpty()}"
}

private data class SideBySideDiffRow(
    val oldLineNumber: Int?,
    val oldText: String?,
    val oldType: DiffLineType?,
    val newLineNumber: Int?,
    val newText: String?,
    val newType: DiffLineType?,
)

@Preview(showBackground = true)
@Composable
private fun OfflineGitScreenPreview() {
    OfflineGitTheme {
        OfflineGitScreen(
            state = previewState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun WideOfflineGitScreenPreview() {
    OfflineGitTheme {
        OfflineGitScreen(
            state = previewState().copy(viewerMode = DiffViewerMode.SIDE_BY_SIDE),
            onAction = {},
        )
    }
}

private fun previewState(): OfflineGitState {
    val repository = GithubRepository(
        id = 1,
        owner = "octocat",
        name = "offline-demo",
        defaultBranch = "main",
        isPrivate = false,
        updatedAt = null,
    )
    val branch = GithubBranch(name = "main", headSha = "demo-commit")
    val commit = GithubCommitSummary(
        sha = "demo-commit",
        firstParentSha = "demo-parent",
        authorName = "Offline Git",
        authorEmail = "offline@example.local",
        message = "Render cached unified diff",
        committedAt = null,
    )
    val file = GithubCommitFile(
        path = "README.md",
        previousPath = null,
        status = "modified",
        additions = 2,
        deletions = 1,
        blobSha = "demo-blob",
        patch = null,
    )
    val diff = DiffFile(
        path = file.path,
        previousPath = null,
        status = file.status,
        hunks = listOf(
            DiffHunk(
                oldStart = 1,
                oldLineCount = 3,
                newStart = 1,
                newLineCount = 4,
                lines = listOf(
                    DiffLine(DiffLineType.HUNK_HEADER, null, null, "@@ -1,3 +1,4 @@"),
                    DiffLine(DiffLineType.CONTEXT, 1, 1, "# Offline GitHub Viewer"),
                    DiffLine(DiffLineType.DELETION, 2, null, "Placeholder Android shell."),
                    DiffLine(DiffLineType.ADDITION, null, 2, "Local SQLDelight metadata is wired."),
                    DiffLine(DiffLineType.ADDITION, null, 3, "Cached patches render as unified diffs."),
                    DiffLine(DiffLineType.CONTEXT, 3, 4, "More sync work comes next."),
                ),
            ),
        ),
    )
    return OfflineGitState(
        repositories = listOf(repository),
        selectedRepositoryId = repository.id,
        branches = listOf(branch),
        selectedBranchName = branch.name,
        commits = listOf(commit),
        selectedCommitSha = commit.sha,
        files = listOf(file),
        selectedFilePath = file.path,
        diff = diff,
        downloadJob = BranchDownloadJob(
            id = "1:main",
            repositoryId = repository.id,
            branchName = branch.name,
            status = BranchDownloadJobStatus.COMPLETED,
            progress = 1.0,
            errorMessage = null,
        ),
        isLoading = false,
    )
}
