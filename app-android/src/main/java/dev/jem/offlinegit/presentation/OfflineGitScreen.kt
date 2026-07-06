package dev.jem.offlinegit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.jem.offlinegit.domain.DiffFile
import dev.jem.offlinegit.domain.DiffHunk
import dev.jem.offlinegit.domain.DiffLine
import dev.jem.offlinegit.domain.DiffLineType
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository

@Composable
fun OfflineGitRoot(
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: OfflineGitViewModel = viewModel(factory = viewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MaterialTheme {
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
        item {
            GithubSyncCard(
                token = state.githubToken,
                hasRepository = state.selectedRepositoryId != null,
                hasBranch = state.selectedBranchName != null,
                isLoading = state.isLoading,
                onAction = onAction,
            )
        }
        item {
            SectionCard(title = "Repositories") {
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
        item {
            SectionCard(title = "Branches") {
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
        item {
            SectionCard(title = "Commits") {
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
        item {
            SectionCard(title = "Changed files") {
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
        state.diff?.let { diff ->
            item {
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = diff.hunks,
                key = { hunk -> "${hunk.oldStart}:${hunk.newStart}" },
            ) { hunk ->
                DiffHunkCard(hunk = hunk)
            }
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
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onRetryClick) {
                    Text(text = "Retry")
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun GithubSyncCard(
    token: String,
    hasRepository: Boolean,
    hasBranch: Boolean,
    isLoading: Boolean,
    onAction: (OfflineGitAction) -> Unit,
) {
    SectionCard(title = "GitHub sync") {
        OutlinedTextField(
            value = token,
            onValueChange = { onAction(OfflineGitAction.OnTokenChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Personal access token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = token.isNotBlank() && !isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchRepositoriesClick) },
            ) {
                Text(text = "Fetch repos")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = token.isNotBlank() && hasRepository && !isLoading,
                onClick = { onAction(OfflineGitAction.OnFetchBranchesClick) },
            ) {
                Text(text = "Fetch branches")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = token.isNotBlank() && hasRepository && hasBranch && !isLoading,
            onClick = { onAction(OfflineGitAction.OnDownloadBranchClick) },
        ) {
            Text(text = "Download selected branch")
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
private fun DiffHunkCard(hunk: DiffHunk) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
            .background(line.backgroundColor())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldLineNumber?.toString().orEmpty(),
            modifier = Modifier.weight(0.12f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.newLineNumber?.toString().orEmpty(),
            modifier = Modifier.weight(0.12f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.prefix(),
            modifier = Modifier.weight(0.08f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = line.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DiffLine.backgroundColor(): Color = when (type) {
    DiffLineType.ADDITION -> Color(0xFFE6F4EA)
    DiffLineType.DELETION -> Color(0xFFFFEBEE)
    DiffLineType.HUNK_HEADER -> MaterialTheme.colorScheme.tertiaryContainer
    DiffLineType.CONTEXT,
    DiffLineType.NO_NEWLINE,
    -> Color.Transparent
}

private fun DiffLine.prefix(): String = when (type) {
    DiffLineType.ADDITION -> "+"
    DiffLineType.DELETION -> "-"
    DiffLineType.HUNK_HEADER -> "@"
    DiffLineType.CONTEXT -> " "
    DiffLineType.NO_NEWLINE -> "\\"
}

@Preview(showBackground = true)
@Composable
private fun OfflineGitScreenPreview() {
    MaterialTheme {
        OfflineGitScreen(
            state = previewState(),
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
        isLoading = false,
    )
}
