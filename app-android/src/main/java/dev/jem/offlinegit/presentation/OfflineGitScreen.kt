package dev.jem.offlinegit.presentation

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
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
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import kotlin.math.abs
import kotlinx.coroutines.launch

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
                    val entry = state.selectedFileDiff()
                    val diff = entry?.diff
                    if (diff == null) {
                        item { EmptyDiffCard(message = entry?.errorMessage?.let { "Unable to load diff: $it" }) }
                    } else {
                        item {
                            DiffViewerHeader(
                                diff = diff,
                                selectedFile = entry.file,
                                commitMessage = state.selectedCommit()?.message,
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
    navigationIcon: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    titleMono: Boolean = false,
    isLoading: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Column(
                modifier = if (onTitleClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTitleClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (titleMono) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onTitleClick != null) {
                        Text(
                            text = "⌄",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
            } else {
                navigationIcon?.invoke()
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

// A glanceable badge for how the diff was produced: an exact cached GitHub
// patch (green ✓) vs. an approximate diff computed locally from blobs (amber ≈).
private fun DiffSource.chipLabel(): String = when (this) {
    DiffSource.CACHED_PATCH -> "✓ GitHub patch"
    DiffSource.LOCAL_BLOB -> "≈ Local diff"
}

private fun DiffSource.chipStyle(): ChipStyle = when (this) {
    DiffSource.CACHED_PATCH -> ChipStyle.OK
    DiffSource.LOCAL_BLOB -> ChipStyle.NEUTRAL
}

// Minimum width a sidebar can be dragged down to before it stops shrinking.
private const val MinSidebarWidthDp = 180f
private val SidebarDividerWidth = 16.dp
private val CollapsedRailWidth = 28.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideOfflineGitScreen(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onConnectClick: () -> Unit,
) {
    var showSourceSheet by remember { mutableStateOf(false) }
    var commitsCollapsed by rememberSaveable { mutableStateOf(false) }
    var filesCollapsed by rememberSaveable { mutableStateOf(false) }
    var commitsWidthDp by rememberSaveable { mutableStateOf(280f) }
    var filesWidthDp by rememberSaveable { mutableStateOf(280f) }
    // Hoisted above each pane's collapse: state remembered inside the pane
    // content would be discarded when it collapses, which would reset its scroll.
    val commitsListState = rememberLazyListState()
    val filesListState = rememberLazyListState()
    // A restored selection can sit far down the commit list, so bring it into
    // view once when commits first load — not on every selection change, since
    // tapping a commit shouldn't yank the list around.
    var hasScrolledToRestoredCommit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.commits) {
        if (!hasScrolledToRestoredCommit && state.commits.isNotEmpty()) {
            hasScrolledToRestoredCommit = true
            state.sidebarCommitIndex(state.selectedCommitSha)?.let { index ->
                // A couple of items of context above the selected commit.
                commitsListState.scrollToItem((index - 2).coerceAtLeast(0))
            }
        }
    }
    // Keep the selected file visible in its sidebar, whether the selection
    // changed from clicking here or from scrolling the diff pane.
    LaunchedEffect(state.selectedFilePath) {
        val index = state.files.indexOfFirst { it.path == state.selectedFilePath }
        if (index >= 0) {
            filesListState.animateScrollToItem(index)
        }
    }
    Scaffold(
        topBar = {
            WideTopBar(
                state = state,
                onAction = onAction,
                onConnectClick = onConnectClick,
                onSourceClick = { showSourceSheet = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Neither sidebar may grow past half the available width, so the
            // diff pane always keeps room to be useful.
            val maxPaneWidthDp = maxWidth.value / 2f
            Row(modifier = Modifier.fillMaxSize()) {
                ResizableSidebar(
                    widthDp = commitsWidthDp,
                    onWidthChange = { commitsWidthDp = it },
                    collapsed = commitsCollapsed,
                    onToggleCollapsed = { commitsCollapsed = !commitsCollapsed },
                    maxWidthDp = maxPaneWidthDp,
                ) {
                    LazyColumn(
                        state = commitsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        commitsPaneItems(
                            state = state,
                            onAction = onAction,
                            onChooseSourceClick = { showSourceSheet = true },
                        )
                    }
                }
                ResizableSidebar(
                    widthDp = filesWidthDp,
                    onWidthChange = { filesWidthDp = it },
                    collapsed = filesCollapsed,
                    onToggleCollapsed = { filesCollapsed = !filesCollapsed },
                    maxWidthDp = maxPaneWidthDp,
                ) {
                    LazyColumn(
                        state = filesListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filesPaneItems(state = state, onAction = onAction)
                    }
                }
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

    if (showSourceSheet) {
        SourceSheet(
            state = state,
            onAction = onAction,
            onDismiss = { showSourceSheet = false },
        )
    }
}

// A sidebar pane that can be dragged wider/narrower via its trailing divider,
// or collapsed to a thin rail. Width/collapsed state is hoisted so callers can
// persist it with rememberSaveable, the same way isSidebarVisible worked
// before the sidebar was split into independent commits/files panes.
@Composable
private fun ResizableSidebar(
    widthDp: Float,
    onWidthChange: (Float) -> Unit,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    maxWidthDp: Float,
    content: @Composable () -> Unit,
) {
    if (collapsed) {
        Box(
            modifier = Modifier
                .width(CollapsedRailWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onToggleCollapsed),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = "›",
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val density = LocalDensity.current
    Row(modifier = Modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .width(widthDp.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
        Column(
            modifier = Modifier
                .width(SidebarDividerWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = onToggleCollapsed,
                modifier = Modifier.size(SidebarDividerWidth),
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val deltaDp = with(density) { delta.toDp().value }
                            onWidthChange((widthDp + deltaDp).coerceIn(MinSidebarWidthDp, maxWidthDp))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

// Repo/branch/download selection, moved out of the sidebar: picking a source is
// an occasional task, so it lives behind the app-bar title instead of crowding
// the commit list.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideTopBar(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onConnectClick: () -> Unit,
    onSourceClick: () -> Unit,
) {
    val title = listOfNotNull(
        state.selectedRepository()?.name,
        state.selectedBranchName,
    ).joinToString(" · ").ifBlank { "Choose repository" }
    OfflineGitTopBar(
        title = title,
        subtitle = state.commitsSubtitle(),
        onTitleClick = onSourceClick,
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
                Text(text = state.activeAccount()?.login ?: "Connect")
            }
        },
    )
}

// The commits sidebar: status strip plus the commit list. Repo/branch/download
// selection lives in the app-bar SourceSheet.
private fun LazyListScope.commitsPaneItems(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    onChooseSourceClick: () -> Unit,
) {
    statusStripItem(
        error = state.error,
        syncStatus = state.syncStatus,
        onRetryClick = { onAction(OfflineGitAction.OnRetryClick) },
    )
    item { SectionLabel(text = "Commits") }
    if (state.commits.isEmpty()) {
        item { EmptySectionText(text = "Download a branch to browse commits offline.") }
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseSourceClick,
            ) {
                Text(text = "Choose repository & branch")
            }
        }
    } else {
        // Individual items (not one Column) so the list can lazily compose 100+
        // commits and scroll straight to the restored selection.
        items(state.commits, key = { commit -> "commit:${commit.sha}" }) { commit ->
            CommitRow(
                commit = commit,
                isSelected = commit.sha == state.selectedCommitSha,
                onClick = { onAction(OfflineGitAction.OnCommitClick(commit.sha)) },
            )
        }
    }
}

// The files sidebar: a jump index into DiffViewerPane's stacked file diffs.
// Selection follows either a direct click here or scrolling the diff pane.
private fun LazyListScope.filesPaneItems(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
) {
    item { SectionLabel(text = "Files · ${state.files.size} changed") }
    if (state.files.isEmpty()) {
        item { EmptySectionText(text = "Select a commit to see changed files.") }
    } else {
        items(state.files, key = { file -> "file:${file.path}" }) { file ->
            ChangedFileRow(
                file = file,
                isSelected = file.path == state.selectedFilePath,
                onClick = { onAction(OfflineGitAction.OnFileClick(file.path)) },
            )
        }
    }
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
    // The whole file is one continuous surface: the outer LazyColumn scrolls
    // vertically, while a single shared ScrollState scrolls every line
    // horizontally in unison so long lines are reachable instead of clipped.
    item(key = "diff:${diff.path}:$viewerMode") {
        DiffSurface(diff = diff, viewerMode = viewerMode)
    }
}

// The wide layout shows the WHOLE commit: a commit header, then every changed
// file stacked as header + diff with a gap in between, like a PR page. The
// sidebar file list doubles as a jump index into this stack.
@Composable
private fun DiffViewerPane(
    state: OfflineGitState,
    onAction: (OfflineGitAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val commit = state.selectedCommit()
    val fileDiffs = state.fileDiffs
    if (commit == null || fileDiffs.isEmpty()) {
        Column(modifier = modifier.padding(16.dp)) {
            EmptyDiffCard()
        }
        return
    }

    val listState = rememberLazyListState()
    // Guards the scroll-driven selection below from fighting this effect's own
    // programmatic scroll while a file click is still animating into place.
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    // Sidebar file clicks land here. Item 0 is the commit header, so file i
    // sits at item i + 1; the first file scrolls to the top so the header
    // stays visible.
    LaunchedEffect(commit.sha, state.selectedFilePath) {
        isProgrammaticScroll = true
        try {
            val index = fileDiffs.indexOfFirst { it.file.path == state.selectedFilePath }
            if (index > 0) {
                listState.animateScrollToItem(index + 1)
            } else {
                listState.scrollToItem(0)
            }
        } finally {
            isProgrammaticScroll = false
        }
    }

    // Whichever file diff dominates the viewport is "the file being shown" —
    // the reverse of the click-to-scroll effect above, so scrolling drives the
    // sidebar's selection instead of only the other way around.
    val currentFileIndexState = remember(fileDiffs) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            layoutInfo.visibleItemsInfo
                .filter { it.index > 0 }
                .maxByOrNull { visibleItem ->
                    val top = maxOf(visibleItem.offset, viewportStart)
                    val bottom = minOf(visibleItem.offset + visibleItem.size, viewportEnd)
                    (bottom - top).coerceAtLeast(0)
                }
                ?.index
                ?.minus(1)
        }
    }
    LaunchedEffect(fileDiffs) {
        snapshotFlow { currentFileIndexState.value }
            .collect { index ->
                if (isProgrammaticScroll || index == null) return@collect
                val path = fileDiffs.getOrNull(index)?.file?.path ?: return@collect
                if (path != state.selectedFilePath) {
                    onAction(OfflineGitAction.OnFileClick(path))
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "commit-header") {
            CommitDiffHeader(commit = commit, files = state.files)
        }
        fileDiffs.forEach { entry ->
            item(key = "file:${entry.file.path}") {
                FileDiffBlock(
                    entry = entry,
                    viewerMode = state.viewerMode,
                    isSelected = entry.file.path == state.selectedFilePath,
                )
            }
        }
    }
}

@Composable
private fun CommitDiffHeader(
    commit: GithubCommitSummary,
    files: List<GithubCommitFile>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CommitMessageAccordion(message = commit.message)
            val byline = listOfNotNull(
                commit.sha.take(7),
                commit.authorName,
                absoluteDateTime(commit.committedAt),
            ).joinToString(" · ")
            Text(
                text = byline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildAnnotatedString {
                    append("${files.size} ${if (files.size == 1) "file" else "files"} changed  ")
                    withStyle(SpanStyle(color = OfflineGitPalette.Add)) { append("+${files.sumOf { it.additions }}") }
                    append(" ")
                    withStyle(SpanStyle(color = OfflineGitPalette.Del)) { append("−${files.sumOf { it.deletions }}") }
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileDiffBlock(
    entry: CommitFileDiff,
    viewerMode: DiffViewerMode,
    isSelected: Boolean,
) {
    // Tapping the header collapses the diff — a way to fold away a huge file
    // (a new schema dump, generated code) while still reviewing the rest of the
    // commit. State is remembered per file path so it survives scroll recycling.
    var collapsed by remember(entry.file.path) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FileDiffHeader(
            entry = entry,
            isSelected = isSelected,
            collapsed = collapsed,
            onToggleCollapsed = { collapsed = !collapsed },
        )
        if (collapsed) {
            return@Column
        }
        val diff = entry.diff
        if (diff != null) {
            DiffSurface(diff = diff, viewerMode = viewerMode)
        } else {
            EmptySectionText(
                text = entry.errorMessage?.let { "Unable to load diff: $it" } ?: "No diff available.",
            )
        }
    }
}

@Composable
private fun FileDiffHeader(
    entry: CommitFileDiff,
    isSelected: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
) {
    val file = entry.file
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onToggleCollapsed)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (collapsed) "▸" else "▾",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusSquare(status = file.status)
            Text(
                text = file.previousPath?.let { "$it → ${file.path}" } ?: file.path,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
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
            entry.diff?.source?.let { source ->
                StatusChip(text = source.chipLabel(), style = source.chipStyle())
            }
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
                text = if (state.accounts.isEmpty()) "Connect a GitHub account" else "GitHub accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            state.accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isActive = account.id == state.activeAccountId,
                    onClick = {
                        onAction(OfflineGitAction.OnAccountClick(account.id))
                        onDismiss()
                    },
                    onRemoveClick = { onAction(OfflineGitAction.OnRemoveAccountClick(account.id)) },
                )
            }
            if (state.accounts.isNotEmpty()) {
                Text(
                    text = "Add another account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

@Composable
private fun AccountRow(
    account: GithubAccount,
    isActive: Boolean,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val background = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarTile(label = account.login)
        Text(
            text = account.login,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isActive) {
            StatusChip(text = "active", style = ChipStyle.OK, showDot = true)
        }
        TextButton(onClick = onRemoveClick) {
            Text(text = "Remove")
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
    // The wide layout hides this: its diff pane already leads with the commit
    // message, so the sidebar keeps just the jump list.
    showCommitMessage: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showCommitMessage) {
            state.selectedCommit()?.let { commit ->
                CommitMessageBlock(commit)
            }
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
            // Middle ellipsis: the filename at the end of a long path is the
            // part worth keeping visible.
            overflow = TextOverflow.MiddleEllipsis,
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
private fun EmptyDiffCard(message: String? = null) {
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
                text = message ?: "Select a commit to view its cached changes.",
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
    commitMessage: String?,
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
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(text = diff.source.chipLabel(), style = diff.source.chipStyle())
            }
            Text(
                text = diff.subtitle(selectedFile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            commitMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
                CommitMessageAccordion(message = message)
            }
            DiffModeSelector(
                selectedMode = viewerMode,
                onModeClick = { mode -> onAction(OfflineGitAction.OnViewerModeClick(mode)) },
            )
        }
    }
}

/**
 * Commit message shown between the file name and the mode buttons. Collapsed to
 * the subject line; tapping expands the body. When there is no body the row is
 * inert (no chevron, not clickable).
 */
@Composable
private fun CommitMessageAccordion(message: String) {
    val subject = message.lineSequence().firstOrNull().orEmpty()
    val body = message.substringAfter('\n', missingDelimiterValue = "").trim()
    val hasBody = body.isNotEmpty()
    var expanded by remember(message) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .then(if (hasBody) Modifier.clickable { expanded = !expanded } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasBody) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (hasBody && expanded) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun DiffSurface(
    diff: DiffFile,
    viewerMode: DiffViewerMode,
) {
    val horizontalScroll = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        // horizontalScroll(enabled = false) keeps the overflow layout, clipping,
        // and programmatic scrolling but drops its greedy gesture handling; the
        // biased modifier claims only clearly-sideways drags in its place.
        //
        // widthIn(min = viewport) floors the content at the card width so a file
        // whose longest line is narrower than the screen still stretches every
        // row's tinted background to the edge instead of stopping mid-screen;
        // width(IntrinsicSize.Max) still lets wider files overflow and scroll.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minRowWidth = maxWidth
            Column(
                modifier = rememberSidewaysDragModifier(horizontalScroll)
                    .horizontalScroll(horizontalScroll, enabled = false)
                    .widthIn(min = minRowWidth)
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 8.dp),
            ) {
                when (viewerMode) {
                    DiffViewerMode.UNIFIED -> diff.hunks.forEach { hunk ->
                        hunk.lines.forEach { line -> DiffLineRow(line = line) }
                    }
                    DiffViewerMode.SIDE_BY_SIDE -> SideBySideDiff(rows = diff.toSideBySideRows())
                }
            }
        }
    }
}

// Diff cards are wide and fill most of the viewport, so nearly every swipe
// starts on one. The stock horizontalScroll claims any drag whose x component
// passes touch slop first — including mostly-vertical swipes — which makes a
// multi-file commit fight the user. This claims a drag only when it is clearly
// sideways; everything else falls through to the outer vertical list.
@Composable
private fun rememberSidewaysDragModifier(state: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val flingDecay = rememberSplineBasedDecay<Float>()
    return Modifier.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPointerInputChange(down)
            val drag = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                // Not consuming keeps waiting, so a mostly-vertical drag is
                // eventually claimed by the outer list and this gesture cancels.
                if (abs(overSlop.x) > abs(overSlop.y) * SidewaysDragBias) {
                    change.consume()
                    scope.launch { state.scrollBy(-overSlop.x) }
                }
            }
            if (drag != null) {
                velocityTracker.addPointerInputChange(drag)
                horizontalDrag(drag.id) { change ->
                    velocityTracker.addPointerInputChange(change)
                    val delta = change.positionChange().x
                    change.consume()
                    scope.launch { state.scrollBy(-delta) }
                }
                val flingDistance = flingDecay.calculateTargetValue(
                    initialValue = 0f,
                    initialVelocity = -velocityTracker.calculateVelocity().x,
                )
                if (abs(flingDistance) > 1f) {
                    scope.launch { state.animateScrollBy(flingDistance) }
                }
            }
        }
    }
}

private const val SidewaysDragBias = 1.5f

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
private fun SideBySideDiff(rows: List<SideBySideDiffRow>) {
    // The whole file is one pair of columns rather than one pair per hunk, so
    // the left column's IntrinsicSize.Max is the widest old line in the ENTIRE
    // file and the divider sits at a single, fixed x for every row. (Per-hunk
    // sizing made the divider jump around.) Hunk headers are split across the
    // two columns so they don't interrupt that continuous divider.
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            rows.forEach { row ->
                SideBySideCell(
                    lineNumber = row.oldLineNumber,
                    text = row.oldText,
                    type = row.oldType,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            rows.forEach { row ->
                SideBySideCell(
                    lineNumber = row.newLineNumber,
                    text = row.newText,
                    type = row.newType,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SideBySideCell(
    lineNumber: Int?,
    text: String?,
    type: DiffLineType?,
    modifier: Modifier = Modifier,
) {
    if (type == DiffLineType.HUNK_HEADER) {
        Text(
            text = text.orEmpty(),
            modifier = modifier
                .background(type.backgroundColor())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = type.textColor(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        return
    }
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

// One flat row list for the entire file. Each hunk contributes a header row —
// split so the old side shows "@@ -a,b @@" and the new side "@@ +c,d @@" — then
// its aligned data rows, keeping both columns row-for-row parallel.
private fun DiffFile.toSideBySideRows(): List<SideBySideDiffRow> = hunks.flatMap { hunk ->
    val header = SideBySideDiffRow(
        oldLineNumber = null,
        oldText = "@@ -${hunk.oldStart},${hunk.oldLineCount} @@",
        oldType = DiffLineType.HUNK_HEADER,
        newLineNumber = null,
        newText = "@@ +${hunk.newStart},${hunk.newLineCount} @@",
        newType = DiffLineType.HUNK_HEADER,
    )
    listOf(header) + hunk.toSideBySideRows()
}

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

private fun OfflineGitState.selectedFileDiff(): CommitFileDiff? =
    fileDiffs.firstOrNull { it.file.path == selectedFilePath }

private fun OfflineGitState.activeAccount(): GithubAccount? = accounts
    .firstOrNull { it.id == activeAccountId }

// Sidebar item index of a commit row. Must mirror commitsPaneItems' order: the
// optional error and sync-status rows, then the "Commits" label, then commits.
private fun OfflineGitState.sidebarCommitIndex(sha: String?): Int? {
    val commitIndex = commits.indexOfFirst { it.sha == sha }
    if (commitIndex < 0) {
        return null
    }
    val itemsBeforeCommits = listOfNotNull(error, syncStatus).size + 1
    return itemsBeforeCommits + commitIndex
}

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
        message = "Render cached unified diff\n\nWire the local blob fallback and show which diff source produced the view.",
        committedAt = null,
    )
    val file = GithubCommitFile(
        path = "README.md",
        previousPath = null,
        status = "modified",
        additions = 3,
        deletions = 2,
        blobSha = "demo-blob",
        patch = null,
    )
    val diff = DiffFile(
        path = file.path,
        previousPath = null,
        status = file.status,
        // Two hunks whose old sides differ in width, so the preview shows the
        // side-by-side divider staying at one fixed x across the collapsed gap.
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
            DiffHunk(
                oldStart = 42,
                oldLineCount = 3,
                newStart = 43,
                newLineCount = 3,
                lines = listOf(
                    DiffLine(DiffLineType.HUNK_HEADER, null, null, "@@ -42,3 +43,3 @@"),
                    DiffLine(DiffLineType.CONTEXT, 42, 43, "## Status"),
                    DiffLine(DiffLineType.DELETION, 43, null, "- TODO"),
                    DiffLine(DiffLineType.ADDITION, null, 44, "- Done"),
                    DiffLine(DiffLineType.CONTEXT, 44, 45, "See milestones."),
                ),
            ),
        ),
    )
    val secondFile = GithubCommitFile(
        path = "docs/setup.md",
        previousPath = null,
        status = "added",
        additions = 2,
        deletions = 0,
        blobSha = "demo-blob-2",
        patch = null,
    )
    val secondDiff = DiffFile(
        path = secondFile.path,
        previousPath = null,
        status = secondFile.status,
        hunks = listOf(
            DiffHunk(
                oldStart = 0,
                oldLineCount = 0,
                newStart = 1,
                newLineCount = 2,
                lines = listOf(
                    DiffLine(DiffLineType.HUNK_HEADER, null, null, "@@ -0,0 +1,2 @@"),
                    DiffLine(DiffLineType.ADDITION, null, 1, "# Setup"),
                    DiffLine(DiffLineType.ADDITION, null, 2, "Install the app."),
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
        files = listOf(file, secondFile),
        selectedFilePath = file.path,
        fileDiffs = listOf(
            CommitFileDiff(file = file, diff = diff),
            CommitFileDiff(file = secondFile, diff = secondDiff),
        ),
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
