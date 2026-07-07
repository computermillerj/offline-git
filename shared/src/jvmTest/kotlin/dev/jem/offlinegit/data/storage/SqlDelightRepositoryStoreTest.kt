package dev.jem.offlinegit.data.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.Result
import dev.jem.offlinegit.domain.StoredSelection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightRepositoryStoreTest {

    @Test
    fun `persists repositories branches commits and commit files`() = runTest {
        val store = createStore()
        val repository = GithubRepository(
            id = 1,
            owner = "octo",
            name = "hello",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = "2026-01-01T00:00:00Z",
        )
        val branches = listOf(GithubBranch(name = "main", headSha = "head-sha"))
        val commits = listOf(
            GithubCommitSummary(
                sha = "commit-sha",
                firstParentSha = "parent-sha",
                authorName = "Jane",
                authorEmail = "jane@example.com",
                message = "Add README",
                committedAt = "2026-01-02T03:04:05Z",
            ),
        )
        val details = GithubCommitDetails(
            sha = "commit-sha",
            firstParentSha = "parent-sha",
            authorName = "Jane",
            authorEmail = "jane@example.com",
            message = "Add README",
            committedAt = "2026-01-02T03:04:05Z",
            files = listOf(
                GithubCommitFile(
                    path = "README.md",
                    previousPath = null,
                    status = "modified",
                    additions = 2,
                    deletions = 1,
                    blobSha = "blob-sha",
                    patch = "@@ -1 +1 @@",
                ),
            ),
        )

        assertEquals(Result.Success(Unit), store.upsertRepositories(listOf(repository)))
        assertEquals(Result.Success(Unit), store.upsertBranches(repositoryId = repository.id, branches = branches))
        assertEquals(
            expected = Result.Success(Unit),
            actual = store.upsertCommits(
                repositoryId = repository.id,
                branchName = "main",
                commits = commits,
            ),
        )
        assertEquals(Result.Success(Unit), store.upsertCommitDetails(repositoryId = repository.id, commit = details))

        assertEquals(Result.Success(listOf(repository)), store.getRepositories())
        assertEquals(Result.Success(branches), store.getBranches(repositoryId = repository.id))
        // getCommits sums additions/deletions from the stored commit files.
        assertEquals(
            expected = Result.Success(commits.map { it.copy(additions = 2, deletions = 1) }),
            actual = store.getCommits(repositoryId = repository.id, branchName = "main"),
        )
        assertEquals(
            expected = Result.Success(details.copy(files = details.files.map { it.copy(patch = null) })),
            actual = store.getCommitDetails("commit-sha"),
        )
    }

    @Test
    fun `persists commit parent count for merge detection`() = runTest {
        val store = createStore()
        val repository = GithubRepository(
            id = 1,
            owner = "octo",
            name = "hello",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val merge = GithubCommitSummary(
            sha = "merge-sha",
            firstParentSha = "parent-a",
            authorName = "Jane",
            authorEmail = "jane@example.com",
            message = "Merge pull request #1",
            committedAt = null,
            parentCount = 2,
        )

        store.upsertRepositories(listOf(repository))
        store.upsertBranches(repositoryId = repository.id, branches = listOf(GithubBranch("main", "head")))
        store.upsertCommits(repositoryId = repository.id, branchName = "main", commits = listOf(merge))

        val stored = (store.getCommits(repositoryId = repository.id, branchName = "main") as Result.Success).data
        assertEquals(2, stored.single().parentCount)
        assertEquals(true, stored.single().isMerge)
    }

    @Test
    fun `persists download job state`() = runTest {
        val store = createStore()
        val repository = GithubRepository(
            id = 1,
            owner = "octo",
            name = "hello",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val branch = GithubBranch(name = "main", headSha = "head-sha")
        val job = BranchDownloadJob(
            id = "1:main",
            repositoryId = repository.id,
            branchName = branch.name,
            status = BranchDownloadJobStatus.RUNNING,
            progress = 0.5,
            errorMessage = null,
        )

        store.upsertRepositories(listOf(repository))
        store.upsertBranches(repositoryId = repository.id, branches = listOf(branch))
        assertEquals(Result.Success(Unit), store.upsertDownloadJob(job))

        assertEquals(
            expected = Result.Success(job),
            actual = store.getDownloadJob(repositoryId = repository.id, branchName = branch.name),
        )
    }

    @Test
    fun `missing commit details returns not found`() = runTest {
        val store = createStore()

        assertEquals(
            expected = Result.Error(DataError.Local.NOT_FOUND),
            actual = store.getCommitDetails("missing"),
        )
    }

    @Test
    fun `scopes repositories per account and cleans up on account removal`() = runTest {
        val store = createStore()
        val shared = GithubRepository(
            id = 10,
            owner = "octo",
            name = "shared",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        val bobOnly = shared.copy(id = 20, name = "bob-only")
        store.upsertAccount(GithubAccount(id = 1, login = "alice", avatarUrl = null))
        store.upsertAccount(GithubAccount(id = 2, login = "bob", avatarUrl = null))
        store.upsertRepositories(listOf(shared, bobOnly))
        store.replaceAccountRepositories(accountId = 1, repositoryIds = listOf(10))
        store.replaceAccountRepositories(accountId = 2, repositoryIds = listOf(10, 20))
        store.upsertBranches(repositoryId = 20, branches = listOf(GithubBranch(name = "main", headSha = "head")))

        assertEquals(Result.Success(listOf(shared)), store.getRepositoriesForAccount(1))
        assertEquals(Result.Success(listOf(bobOnly, shared)), store.getRepositoriesForAccount(2))

        store.setActiveAccount(1)
        store.setActiveAccount(2)
        assertEquals("bob", (store.getActiveAccount() as Result.Success).data?.login)

        assertEquals(Result.Success(Unit), store.removeAccount(2))

        // Bob's exclusive repository is gone, the shared one survives, and the
        // remaining account becomes active again.
        assertEquals(Result.Success(listOf(shared)), store.getRepositories())
        assertEquals("alice", (store.getActiveAccount() as Result.Success).data?.login)
        assertEquals(Result.Success(emptyList()), store.getRepositoriesForAccount(2))
        assertEquals(Result.Success(emptyList()), store.getBranches(repositoryId = 20))
    }

    @Test
    fun `re-registering an account keeps it active`() = runTest {
        val store = createStore()
        store.upsertAccount(GithubAccount(id = 1, login = "alice", avatarUrl = null))
        store.upsertAccount(GithubAccount(id = 2, login = "bob", avatarUrl = null))
        store.setActiveAccount(1)

        // Signing in again upserts the same account; activation must survive.
        store.upsertAccount(GithubAccount(id = 1, login = "alice", avatarUrl = "http://avatar"))

        assertEquals("alice", (store.getActiveAccount() as Result.Success).data?.login)
    }

    @Test
    fun `selection round trips`() = runTest {
        val store = createStore()

        assertEquals(Result.Success(null), store.getSelection())

        val selection = StoredSelection(
            repositoryId = 5,
            branchName = "main",
            commitSha = "sha",
            filePath = "README.md",
        )
        store.saveSelection(selection)
        assertEquals(Result.Success(selection), store.getSelection())

        val updated = selection.copy(commitSha = "other-sha", filePath = null)
        store.saveSelection(updated)
        assertEquals(Result.Success(updated), store.getSelection())
    }

    @Test
    fun `migrates a v2 database preserving existing rows`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // The pre-multi-account shape of the tables this migration touches.
        driver.execute(
            null,
            "CREATE TABLE Account (id INTEGER NOT NULL PRIMARY KEY, login TEXT NOT NULL, avatarUrl TEXT)",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE Repository (id INTEGER NOT NULL PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, " +
                "defaultBranch TEXT NOT NULL, isPrivate INTEGER NOT NULL, updatedAt TEXT, UNIQUE(owner, name))",
            0,
        )
        driver.execute(null, "INSERT INTO Account(id, login, avatarUrl) VALUES (7, 'octo', NULL)", 0)
        driver.execute(null, "INSERT INTO Repository VALUES (1, 'octo', 'hello', 'main', 0, NULL)", 0)

        OfflineGitDatabase.Schema.migrate(driver, 2, 3)

        val store = SqlDelightRepositoryStore(OfflineGitDatabase(driver))
        val repository = GithubRepository(
            id = 1,
            owner = "octo",
            name = "hello",
            defaultBranch = "main",
            isPrivate = false,
            updatedAt = null,
        )
        assertEquals(Result.Success(listOf(repository)), store.getRepositories())
        store.setActiveAccount(7)
        assertEquals("octo", (store.getActiveAccount() as Result.Success).data?.login)
        store.replaceAccountRepositories(accountId = 7, repositoryIds = listOf(1))
        assertEquals(Result.Success(listOf(repository)), store.getRepositoriesForAccount(7))
    }

    private fun createStore(): SqlDelightRepositoryStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OfflineGitDatabase.Schema.create(driver)
        return SqlDelightRepositoryStore(OfflineGitDatabase(driver))
    }
}
