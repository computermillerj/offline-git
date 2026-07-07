package dev.jem.offlinegit.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= WideLayoutBreakpoint) {
            WideOfflineGitScreen(
                state = state,
                onAction = onAction,
            )
        } else {
            PhoneOfflineGitScreen(
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PhoneOfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header(
                isLoading = state.isLoading,
                error = state.error,
                syncStatus = state.syncStatus,
                onRetryClick = { onAction(OfflineGitAction.OnRetryClick) },
            )
        }
        when (state.phonePane) {
            PhonePane.REPOSITORIES -> {
                item {
                    GithubSyncCard(
                        state = state,
                        onAction = onAction,
                    )
                }
                item {
                    RepositorySection(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            PhonePane.BRANCHES -> {
                item {
                    PhoneBackHeader(
                        title = "Branches",
                        subtitle = state.selectedRepositoryLabel(),
                        onBackClick = { onAction(OfflineGitAction.OnPhoneBackClick) },
                    )
                }
                item {
                    GithubSyncCard(
                        state = state,
                        onAction = onAction,
                    )
                }
                item {
                    BranchSection(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            PhonePane.COMMITS -> {
                item {
                    PhoneBackHeader(
                        title = "Commits",
                        subtitle = state.selectedBranchLabel(),
                        onBackClick = { onAction(OfflineGitAction.OnPhoneBackClick) },
                    )
                }
                item {
                    GithubSyncCard(
                        state = state,
                        onAction = onAction,
                    )
                }
                item {
                    CommitSection(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            PhonePane.FILES -> {
                item {
                    PhoneBackHeader(
                        title = "Changed files",
                        subtitle = state.selectedCommitLabel(),
                        onBackClick = { onAction(OfflineGitAction.OnPhoneBackClick) },
                    )
                }
                item {
                    ChangedFilesSection(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            PhonePane.DIFF -> {
                item {
                    PhoneBackHeader(
                        title = "Diff",
                        subtitle = state.selectedFilePath.orEmpty(),
                        onBackClick = { onAction(OfflineGitAction.OnPhoneBackClick) },
                    )
                }
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

@Composable
private fun WideOfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            navigationItems(
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

private fun LazyListScope.navigationItems(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    item {
        Header(
            isLoading = state.isLoading,
            error = state.error,
            syncStatus = state.syncStatus,
            onRetryClick = { onAction(OfflineGitAction.OnRetryClick) },
        )
    }
    item {
        GithubSyncCard(
            state = state,
            onAction = onAction,
        )
    }
    item {
        RepositorySection(
            state = state,
            onAction = onAction,
        )
    }
    item {
        BranchSection(
            state = state,
            onAction = onAction,
        )
    }
    item {
        CommitSection(
            state = state,
            onAction = onAction,
        )
    }
    item {
        ChangedFilesSection(
            state = state,
            onAction = onAction,
        )
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
private fun Header(
    isLoading: Boolean,
    error: String?,
    syncStatus: String?,
    onRetryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Offline GitHub Viewer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Local repository metadata and cached patches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (syncStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ErrorBanner(
                    message = error,
                    onRetryClick = onRetryClick,
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator()
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

@Composable
private fun PhoneBackHeader(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onBackClick) {
            Text(text = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
    }
}

@Composable
private fun GithubSyncCard(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    val hasUsableToken = state.hasToken || state.githubToken.isNotBlank()
    SectionCard(title = "GitHub sync") {
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
        OutlinedTextField(
            value = state.commitLimitText,
            onValueChange = { onAction(OfflineGitAction.OnCommitLimitChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Commit download limit") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = hasUsableToken && !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchRepositoriesClick) },
            ) {
                Text(text = "Refresh repos")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = hasUsableToken && state.selectedRepositoryId != null && !state.isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchBranchesClick) },
            ) {
                Text(text = "Refresh branches")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = hasUsableToken && state.selectedRepositoryId != null && state.selectedBranchName != null && !state.isLoading,
            onClick = { onAction(OfflineGitAction.OnDownloadBranchClick) },
        ) {
            Text(text = "Download / refresh selected branch")
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.hasToken && !state.isLoading,
            onClick = { onAction(OfflineGitAction.OnSignOutClick) },
        ) {
            Text(text = "Sign out")
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
    SectionCard(title = "Repositories") {
        if (state.repositories.isEmpty()) {
            EmptySectionText(text = "Fetch repositories or use the demo data.")
        }
        state.repositories.forEach { repository ->
            SelectableRow(
                title = "${repository.owner}/${repository.name}",
                subtitle = "default: ${repository.defaultBranch}",
                isSelected = repository.id == state.selectedRepositoryId,
                onClick = { onAction(OfflineGitAction.OnRepositoryClick(repository.id)) },
            )
        }
    }
}

@Composable
private fun BranchSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    SectionCard(title = "Branches") {
        if (state.branches.isEmpty()) {
            EmptySectionText(text = "Select a repository and fetch branches.")
        }
        state.branches.forEach { branch ->
            SelectableRow(
                title = branch.name,
                subtitle = branch.headSha.take(12),
                isSelected = branch.name == state.selectedBranchName,
                onClick = { onAction(OfflineGitAction.OnBranchClick(branch.name)) },
            )
        }
    }
}

@Composable
private fun CommitSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    SectionCard(title = "Commits") {
        if (state.commits.isEmpty()) {
            EmptySectionText(text = "Download a branch to browse commits offline.")
        }
        state.commits.forEach { commit ->
            SelectableRow(
                title = commit.message.lineSequence().firstOrNull().orEmpty(),
                subtitle = listOfNotNull(commit.authorName, commit.sha.take(12)).joinToString(" • "),
                isSelected = commit.sha == state.selectedCommitSha,
                onClick = { onAction(OfflineGitAction.OnCommitClick(commit.sha)) },
            )
        }
    }
}

@Composable
private fun ChangedFilesSection(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    SectionCard(title = "Changed files") {
        if (state.files.isEmpty()) {
            EmptySectionText(text = "Select a commit to see changed files.")
        }
        state.files.forEach { file ->
            SelectableRow(
                title = file.path,
                subtitle = "+${file.additions} -${file.deletions} • ${file.status}",
                isSelected = file.path == state.selectedFilePath,
                onClick = { onAction(OfflineGitAction.OnFileClick(file.path)) },
            )
        }
    }
}

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
private fun SelectableRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
            Text(
                text = diff.path,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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

private fun OfflineGitState.selectedRepositoryLabel(): String = repositories
    .firstOrNull { it.id == selectedRepositoryId }
    ?.let { "${it.owner}/${it.name}" }
    .orEmpty()

private fun OfflineGitState.selectedBranchLabel(): String = listOf(
    selectedRepositoryLabel(),
    selectedBranchName.orEmpty(),
)
    .filter { it.isNotBlank() }
    .joinToString(" • ")

private fun OfflineGitState.selectedCommitLabel(): String = commits
    .firstOrNull { it.sha == selectedCommitSha }
    ?.let { commit ->
        listOf(
            commit.message.lineSequence().firstOrNull().orEmpty(),
            commit.sha.take(12),
        )
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
    .orEmpty()

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
