# Offline GitHub Viewer KMP Plan

## Goal

Build a Kotlin Multiplatform app, Android-first, that connects to a GitHub account, downloads repository data for offline use, lets the user choose a branch, browses commits, and views source changes in a Git-style diff viewer.

Primary UI target: Android. On phones the flow is list-to-detail navigation (repos -> branches -> commits -> diff); on tablets and later desktop, a two-pane layout with repository/source context on the left and commit changes on the right, with Android Studio-like coloring and highlighting. KMP keeps a desktop target open at little extra cost.

## Current implementation status

Last updated: 2026-07-06. Latest implementation commit: `1a9b59c Cache HEAD snapshot tarball`.

Implemented:

- KMP Gradle project with `:shared` and Android Compose app in `:app-android`.
- Token-based development auth, GitHub API client, and endpoints for user, repositories, branches, commit lists, commit details, blobs, and tarballs.
- SQLDelight metadata storage for accounts, repositories, branches, commits, changed files, and branch download jobs.
- Okio-backed blob and patch caches.
- Branch download orchestration with progress reporting, persisted job status, cached GitHub patches, SHA-deduplicated blob downloads, and HEAD snapshot tarball indexing.
- Tarball snapshot indexing that handles GitHub gzipped tarballs, strips archive root paths, skips binary blobs for snapshot caching, and computes Git blob SHA-1 values locally.
- Diff pipeline with cached GitHub patch rendering first and local blob-based fallback when patches are missing.
- Android UI for token entry, repository and branch loading, branch download, local repository/commit/file browsing, diff rendering, and download status display.
- Real Android viewer UI with phone list-to-detail navigation, larger-screen two-pane layout, unified and side-by-side diff modes, colors, line numbers, and paired side-by-side scrolling.
- GitHub Device Flow sign-in, Android Keystore-backed encrypted token storage, sign-out, sync refresh controls, commit-limit control for large repositories, and clearer error banners.
- Unit tests for API calls, auth, storage, branch downloads, tarball indexing, diff parsing/providers, and the Android ViewModel.

Still to improve:

- Use persisted `DownloadJob` state to resume or skip already-completed branch downloads instead of only recording status.
- Improve old-blob SHA resolution for modified files so local blob fallback works more often across commit history.
- Add a desktop Compose target if desktop support becomes wanted.

Validation currently passes with:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :shared:jvmTest
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app-android:testDebugUnitTest :app-android:compileDebugKotlin
ANDROID_HOME=$HOME/Android/Sdk ./gradlew check :app-android:assembleDebug
```

## Core decisions

1. **Start with GitHub API, not a full Git client.**
   - MVP downloads repository, branch, commit, patch, and blob data through GitHub APIs.
   - This keeps shared Kotlin portable.
   - Add a native Git backend only if GitHub API limits become a proven blocker.

2. **Do not make GitHub patch payloads the only diff source.**
   - Cache raw GitHub patches when available.
   - Also cache old/new blobs needed to generate diffs locally.
   - Put all diff loading behind a `DiffProvider` boundary so the app can switch sources later.

3. **Keep the first repository simple, but Android-visible from day one.**
   - Start with one `:shared` KMP module and clear package boundaries.
   - Include a thin `:app-android` Compose shell from the first PR so minSdk, SQLDelight/Okio driver, storage-path, and packaging problems surface early instead of at milestone 7.
   - Split into `:core:*` / `:feature:*` modules only after the boundaries are proven.

## MVP scope

1. Connect to GitHub with token-based development auth.
2. List user repositories.
3. Select repository and branch.
4. Download selected branch for offline viewing.
5. Show downloaded commits.
6. Show changed files for a commit.
7. Render unified Git diffs with colors:
   - additions: green
   - deletions: red
   - context: normal
   - hunk headers: accent color
8. Cache enough file content to support side-by-side diff for downloaded commits.

## Non-goals for first version

- Editing files.
- Creating commits.
- Pushing/pulling like a real Git client.
- Full repository history by default.
- Perfect rename detection beyond GitHub commit metadata.
- Binary/LFS diff rendering.
- Custom syntax-highlighting engine before the diff renderer works.
- Full OAuth UI before the API/data layers work.
- Review annotations (comments, approvals). Viewer models should still be shaped so annotations can attach later without schema surgery.

## Suggested stack

- Kotlin Multiplatform shared module first.
- Coroutines + Flow for async work and observable state.
- Ktor Client for GitHub API.
- kotlinx.serialization for DTOs.
- Okio `FileSystem` for cross-platform file/blob cache.
- SQLite metadata store via SQLDelight for KMP-first persistence.
- Constructor injection first; add Koin only when wiring becomes painful.
- Compose Multiplatform for UI: Android app from the start, desktop target later.

Ktor, SQLDelight, and Okio must stay inside data/storage implementations. Domain and viewer-facing APIs should use app models and simple Kotlin types only.

## Initial project shape

Start small:

```text
:app-android   (thin Compose shell; proves :shared links into an APK)
:shared
  src/commonMain/kotlin
    domain/
    data/github/
    data/storage/
    sync/
    diff/
    viewer/
  src/commonTest/kotlin
```

Package ownership:

- `domain/`: app models, repository/store contracts, error types, result types.
- `data/github/`: Ktor client, GitHub DTOs, DTO mappers, pagination.
- `data/storage/`: SQLDelight, file/blob/patch cache, storage mappers.
- `sync/`: branch download orchestration and progress.
- `diff/`: diff providers, unified diff parser, local text diff generation.
- `viewer/`: UI-ready models for commit/file/diff display, with no DTO/entity leakage.

Likely future shape, only when needed:

```text
:app-android
:app-desktop
:core:domain
:core:data
:core:diff
:feature:auth
:feature:repo-browser
:feature:commit-viewer
```

## Dependency rules

Inside `:shared`, use package boundaries as architecture boundaries:

```text
viewer -> domain
sync -> domain
sync -> data/github + data/storage + diff implementations
data/github -> domain
data/storage -> domain
diff -> domain
```

Rules:

- `domain` depends on no data, storage, Ktor, SQLDelight, Okio, Compose, or platform APIs.
- `viewer` models do not reference DTOs, SQLDelight entities, Ktor responses, or storage paths.
- UI/presentation code never fetches remote data directly; it calls domain/sync APIs.
- Platform-specific auth and file details sit behind interfaces.
- Data modules map DTO/entity/blob data into domain models before returning them.

## Public API shape

Define domain-facing interfaces early, even while everything lives in one module:

```kotlin
interface TokenStore
interface GithubRemoteDataSource
interface RepositoryStore
interface BlobStore
interface PatchStore
interface BranchDownloader
interface DiffProvider
interface DiffParser
```

Implementation names should describe what they wrap:

- `KtorGithubRemoteDataSource`
- `SqlDelightRepositoryStore`
- `OkioBlobStore`
- `OkioPatchStore`
- `GithubPatchDiffProvider`
- `LocalBlobDiffProvider`

Avoid `Impl` suffixes and avoid exposing library-specific types from these interfaces.

## Data ownership

Metadata lives in SQLite. Blob and patch payloads live on disk.

SQLite owns:

```text
Account(id, login, avatarUrl)
Repository(id, owner, name, defaultBranch, isPrivate, updatedAt)
Branch(repositoryId, name, headSha, downloadedAt)
Commit(sha, repositoryId, firstParentSha, authorName, authorEmail, message, committedAt)
BranchCommit(repositoryId, branchName, sha, position)
CommitFile(commitSha, path, previousPath, status, additions, deletions, patchId, oldBlobSha, newBlobSha)
DownloadJob(id, repositoryId, branchName, status, progress, errorMessage)
```

`Commit` is keyed by SHA so a commit reachable from several downloaded branches is stored once; `BranchCommit` records per-branch membership and order. Diffs are first-parent only: GitHub's commit detail for a merge commit is the diff against the first parent. The MVP renders that and labels merge commits rather than attempting per-parent diffs.

Blob store owns:

```text
Blob(sha, localPath, size, mimeType, isText)
```

Patch store owns:

```text
Patch(id, commitSha, path, localPath, source, downloadedAt)
```

The sync layer coordinates all writes. Network errors must not delete existing offline data.

## GitHub API flow

### Auth

Preferred real app flow: GitHub OAuth Device Flow.

First implementation:

- Define `TokenStore` in domain/common code.
- Provide a development token implementation backed by local config/env for JVM tests/demo only.
- Keep token access out of `GithubRemoteDataSource` callers.

Production token storage later:

- Android: DataStore (or SharedPreferences) with values encrypted via an Android Keystore-backed key. Do not use `EncryptedSharedPreferences`; androidx.security-crypto is deprecated.
- iOS/macOS: Keychain.
- Desktop JVM: OS keychain if available, otherwise clearly documented local secure-ish fallback.

### Repository discovery

- `GET /user/repos`
- paginate all results
- store repo metadata locally

### Branch selection

- `GET /repos/{owner}/{repo}/branches`
- store branch names and HEAD SHAs

### Offline branch download

For selected branch, `BranchDownloader` coordinates:

1. Fetch branch HEAD.
2. Download the HEAD snapshot in one request via the archive endpoint
   (`GET /repos/{owner}/{repo}/tarball/{ref}`) instead of per-blob Git API calls.
3. Index extracted text files into the blob store, computing git blob SHAs locally
   (SHA-1 of `"blob <size>\0"` + content) so cache lookups by SHA work.
4. Fetch recent commits for the branch, with a user-selected limit.
5. Fetch commit details and available patches.
6. For each changed text file, cache old/new blobs when needed for side-by-side or fallback local diff. Always check the blob store by SHA before fetching; blobs repeat heavily across commits.
7. Persist metadata last enough to resume partial downloads safely.

Start with a default commit limit, e.g. last 100 commits. Let the user request more later.

API budget matters: naive per-blob downloads for 100 commits can reach thousands of requests against the 5,000/hour authenticated limit. The tarball snapshot plus SHA-deduplicated blob fetches are what keep the flow inside it.

## Diff architecture

Do not let UI care where diff data came from.

```text
DiffProvider
  -> GithubPatchDiffProvider
       raw cached patch -> UnifiedDiffParser -> DiffFile
  -> LocalBlobDiffProvider
       old/new cached blob text -> local diff -> UnifiedDiffParser/DiffFile
```

Domain/viewer diff model:

```text
DiffFile
  path
  previousPath
  status
  hunks

DiffHunk
  oldStart
  oldLineCount
  newStart
  newLineCount
  lines

DiffLine
  type: context | addition | deletion | hunkHeader | noNewline
  oldLineNumber
  newLineNumber
  text
```

Viewer modes:

1. Unified diff mode: easiest and most reliable first version.
2. Side-by-side mode: requires old/new blob text.
3. Inline word/character highlights: add after side-by-side works.
4. Syntax highlighting: add last.

## UI flow later

```text
Connect GitHub
  -> Repository picker
  -> Branch picker
  -> Download options
  -> Offline repository view
       left: branches / commits / file tree / source file
       right: selected commit diff or selected file contents
```

Diff viewer should support:

- file list for selected commit
- unified patch rendering
- side-by-side rendering when both blobs are cached
- line numbers
- additions/deletions/context colors
- collapsed unchanged sections later
- search within file/diff later

## Error handling rules

- Network errors must not delete existing offline data.
- Partial downloads should be resumable.
- Large/binary/LFS files should be marked unsupported instead of crashing.
- Missing/truncated GitHub patches should fall back to blob-based local diff when blobs exist.
- Rate limits should be surfaced clearly.
- Private repo failures should distinguish auth, permission, not found, and network cases.

## Testing plan

Unit-test first because the repository starts as Kotlin-only.

Critical tests:

- GitHub DTO mapping.
- pagination handling.
- token injection without leaking token values into logs/errors.
- patch parser fixtures.
- added/deleted/renamed file diff cases.
- binary/no-patch files.
- missing/truncated patch fallback to blob diff.
- blob cache lookup by SHA.
- interrupted download resume behavior.

Use Ktor `MockEngine` for API tests and fixture `.patch` files for diff tests.

## Milestones

### 1. Kotlin foundation

- create KMP Gradle project
- add `:shared`
- add `:app-android` Compose shell that depends on `:shared` and shows a placeholder screen
- add Ktor, serialization, and coroutines
- add basic result/error types
- add `TokenStore` and `GithubRemoteDataSource` interfaces
- implement one Ktor endpoint: authenticated user or repository list
- add one Ktor `MockEngine` test

### 2. GitHub API client

- token-based development auth implementation
- repositories endpoint
- branches endpoint
- commit list endpoint
- commit detail endpoint
- pagination support

### 3. Local storage

- add SQLDelight and Okio
- SQLite schema
- blob cache
- patch cache
- repository/branch/commit persistence

### 4. Branch download

- `BranchDownloader` orchestration
- download HEAD tree
- cache text blobs
- fetch commit list
- fetch commit details and patches
- progress reporting
- resumable partial download behavior

### 5. Diff model

- `DiffProvider` boundary
- parse unified patches
- local blob-based fallback diff
- expose stable viewer models
- support added/deleted/modified/renamed files

### 6. Minimal Android UI

- build in `:app-android` with Compose
- list repos, branches, commits
- render unified diff from local storage only

### 7. Real viewer UI

- polish the Android viewer; add a desktop Compose target if wanted
- phones: list-to-detail navigation; tablets/desktop: two-pane layout
- left pane source/repo navigation, right pane diff
- colors, line numbers, scrolling sync
- side-by-side mode

### 8. Production auth and polish

- GitHub Device Flow
- secure token storage per platform
- sync refresh
- better error UI
- large repo controls

## Migration and maintainability risks

1. GitHub API diff data can be missing or truncated for large commits.
   - Mitigation: `DiffProvider` boundary plus cached old/new blobs for local fallback diff.
2. Full Git correctness is hard in pure KMP.
   - Mitigation: defer real Git backend until MVP proves it is needed; add it behind the same provider/store interfaces.
3. Syntax highlighting across KMP can become a project by itself.
   - Mitigation: ship Git diff coloring first; add syntax highlighting later.
4. Large repositories can explode storage/API calls.
   - Mitigation: user-selected branch and commit depth, skip binaries by default, tarball snapshot for HEAD, SHA-deduplicated blob fetches.
5. Module splits can create churn too early.
   - Mitigation: start with package boundaries, split modules only when package contracts stabilize.

## First implementation PR

Create only the Kotlin/API foundation:

- Gradle KMP skeleton
- `:shared` module
- `:app-android` Compose shell that depends on `:shared` (placeholder screen only)
- Ktor client with token auth through `TokenStore`
- domain interfaces for token and GitHub remote access
- one endpoint: authenticated user or repository list
- one test using Ktor `MockEngine`

Do not build the real viewer UI, database, OAuth, blob cache, patch cache, or diff viewer in the first PR.
