package dev.jem.offlinegit.data.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.Result
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
        assertEquals(Result.Success(commits), store.getCommits(repositoryId = repository.id, branchName = "main"))
        assertEquals(
            expected = Result.Success(details.copy(files = details.files.map { it.copy(patch = null) })),
            actual = store.getCommitDetails("commit-sha"),
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

    private fun createStore(): SqlDelightRepositoryStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OfflineGitDatabase.Schema.create(driver)
        return SqlDelightRepositoryStore(OfflineGitDatabase(driver))
    }
}
