package dev.jem.offlinegit.sync

import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.BranchDownloadJob
import dev.jem.offlinegit.domain.BranchDownloadJobStatus
import dev.jem.offlinegit.domain.BranchDownloadProgress
import dev.jem.offlinegit.domain.DataError
import dev.jem.offlinegit.domain.EmptyResult
import dev.jem.offlinegit.domain.GithubAccount
import dev.jem.offlinegit.domain.GithubBranch
import dev.jem.offlinegit.domain.GithubCommitDetails
import dev.jem.offlinegit.domain.GithubCommitFile
import dev.jem.offlinegit.domain.GithubCommitSummary
import dev.jem.offlinegit.domain.GithubRemoteDataSource
import dev.jem.offlinegit.domain.GithubRepository
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import dev.jem.offlinegit.domain.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubBranchDownloaderTest {

    @Test
    fun `downloadBranch stores branch metadata commit details and patches`() = runTest {
        val repository = aRepository()
        val branch = GithubBranch(name = "main", headSha = "head-sha")
        val commit = aCommitSummary()
        val details = aCommitDetails()
        val remoteDataSource = FakeGithubRemoteDataSource(
            commitsResult = Result.Success(listOf(commit)),
            detailsResults = mapOf(commit.sha to Result.Success(details)),
        )
        val repositoryStore = FakeRepositoryStore()
        val patchStore = FakePatchStore()
        val blobStore = FakeBlobStore()
        val downloader = GithubBranchDownloader(
            remoteDataSource = remoteDataSource,
            repositoryStore = repositoryStore,
            patchStore = patchStore,
            blobStore = blobStore,
        )

        val progress = downloader.downloadBranch(
            repository = repository,
            branch = branch,
            commitLimit = 1,
        ).toList()

        assertEquals(
            expected = listOf(
                BranchDownloadProgress.Started,
                BranchDownloadProgress.CommitsStored(count = 1),
                BranchDownloadProgress.CommitStored(sha = commit.sha, completed = 1, total = 1),
                BranchDownloadProgress.Completed,
            ),
            actual = progress,
        )
        assertEquals(listOf(repository), repositoryStore.repositories)
        assertEquals(listOf(branch), repositoryStore.branches)
        assertEquals(listOf(commit), repositoryStore.commits)
        assertEquals(listOf(details), repositoryStore.commitDetails)
        assertEquals(listOf(WrittenPatch(commitSha = commit.sha, path = "README.md", patch = "@@ -1 +1 @@")), patchStore.patches)
        assertEquals(
            expected = mapOf(
                "8ab67f2e70cc4bf7cc4a0c1f84e8aea1e12039c9" to "snapshot\n".encodeToByteArray().toList(),
                "blob-sha" to "blob-sha".encodeToByteArray().toList(),
            ),
            actual = blobStore.blobs.mapValues { it.value.toList() },
        )
        assertEquals(BranchDownloadJobStatus.COMPLETED, repositoryStore.downloadJobs.last().status)
        assertEquals(1.0, repositoryStore.downloadJobs.last().progress)
        assertEquals(listOf(1), remoteDataSource.commitLimits)
    }

    @Test
    fun `downloadBranch stops when commit details fail`() = runTest {
        val repository = aRepository()
        val branch = GithubBranch(name = "main", headSha = "head-sha")
        val commit = aCommitSummary()
        val remoteDataSource = FakeGithubRemoteDataSource(
            commitsResult = Result.Success(listOf(commit)),
            detailsResults = mapOf(commit.sha to Result.Error(DataError.Network.NOT_FOUND)),
        )
        val repositoryStore = FakeRepositoryStore()
        val patchStore = FakePatchStore()
        val downloader = GithubBranchDownloader(
            remoteDataSource = remoteDataSource,
            repositoryStore = repositoryStore,
            patchStore = patchStore,
            blobStore = FakeBlobStore(),
        )

        val progress = downloader.downloadBranch(
            repository = repository,
            branch = branch,
            commitLimit = 1,
        ).toList()

        assertEquals(
            expected = listOf(
                BranchDownloadProgress.Started,
                BranchDownloadProgress.CommitsStored(count = 1),
                BranchDownloadProgress.Failed(DataError.Network.NOT_FOUND),
            ),
            actual = progress,
        )
        assertEquals(emptyList(), repositoryStore.commitDetails)
        assertEquals(emptyList(), patchStore.patches)
        assertEquals(BranchDownloadJobStatus.FAILED, repositoryStore.downloadJobs.last().status)
        assertEquals(DataError.Network.NOT_FOUND.toString(), repositoryStore.downloadJobs.last().errorMessage)
    }

    private fun aRepository(): GithubRepository = GithubRepository(
        id = 1,
        owner = "octo",
        name = "hello",
        defaultBranch = "main",
        isPrivate = false,
        updatedAt = null,
    )

    private fun aCommitSummary(): GithubCommitSummary = GithubCommitSummary(
        sha = "commit-sha",
        firstParentSha = "parent-sha",
        authorName = "Jane",
        authorEmail = "jane@example.com",
        message = "Add README",
        committedAt = "2026-01-02T03:04:05Z",
    )

    private fun aCommitDetails(): GithubCommitDetails = GithubCommitDetails(
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
                additions = 1,
                deletions = 1,
                blobSha = "blob-sha",
                patch = "@@ -1 +1 @@",
            ),
        ),
    )

    private class FakeGithubRemoteDataSource(
        private val commitsResult: Result<List<GithubCommitSummary>, DataError.Network>,
        private val detailsResults: Map<String, Result<GithubCommitDetails, DataError.Network>>,
    ) : GithubRemoteDataSource {
        val commitLimits = mutableListOf<Int>()

        override suspend fun getAuthenticatedUser(): Result<GithubAccount, DataError.Network> = error("not used")

        override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Network> = error("not used")

        override suspend fun getBranches(
            owner: String,
            repository: String,
        ): Result<List<GithubBranch>, DataError.Network> = error("not used")

        override suspend fun getCommits(
            owner: String,
            repository: String,
            branch: String,
            limit: Int,
        ): Result<List<GithubCommitSummary>, DataError.Network> {
            commitLimits += limit
            return commitsResult
        }

        override suspend fun getCommitDetails(
            owner: String,
            repository: String,
            sha: String,
        ): Result<GithubCommitDetails, DataError.Network> = detailsResults.getValue(sha)

        override suspend fun getBlob(
            owner: String,
            repository: String,
            sha: String,
        ): Result<ByteArray, DataError.Network> = Result.Success(sha.encodeToByteArray())

        override suspend fun getTarball(
            owner: String,
            repository: String,
            ref: String,
        ): Result<ByteArray, DataError.Network> = Result.Success(
            tarOf(entry(path = "repo-root/SNAPSHOT.txt", content = "snapshot\n".encodeToByteArray())),
        )
    }

    private class FakeRepositoryStore : RepositoryStore {
        val repositories = mutableListOf<GithubRepository>()
        val branches = mutableListOf<GithubBranch>()
        val commits = mutableListOf<GithubCommitSummary>()
        val commitDetails = mutableListOf<GithubCommitDetails>()
        val downloadJobs = mutableListOf<BranchDownloadJob>()

        override suspend fun upsertAccount(account: GithubAccount): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun upsertRepositories(
            repositories: List<GithubRepository>,
        ): EmptyResult<DataError.Local> {
            this.repositories += repositories
            return Result.Success(Unit)
        }

        override suspend fun getRepositories(): Result<List<GithubRepository>, DataError.Local> = Result.Success(repositories)

        override suspend fun upsertBranches(
            repositoryId: Long,
            branches: List<GithubBranch>,
        ): EmptyResult<DataError.Local> {
            this.branches += branches
            return Result.Success(Unit)
        }

        override suspend fun getBranches(repositoryId: Long): Result<List<GithubBranch>, DataError.Local> = Result.Success(branches)

        override suspend fun upsertCommits(
            repositoryId: Long,
            branchName: String,
            commits: List<GithubCommitSummary>,
        ): EmptyResult<DataError.Local> {
            this.commits += commits
            return Result.Success(Unit)
        }

        override suspend fun getCommits(
            repositoryId: Long,
            branchName: String,
        ): Result<List<GithubCommitSummary>, DataError.Local> = Result.Success(commits)

        override suspend fun upsertCommitDetails(
            repositoryId: Long,
            commit: GithubCommitDetails,
        ): EmptyResult<DataError.Local> {
            commitDetails += commit
            return Result.Success(Unit)
        }

        override suspend fun getCommitDetails(sha: String): Result<GithubCommitDetails, DataError.Local> =
            Result.Success(commitDetails.first { it.sha == sha })

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
    }

    private class FakeBlobStore : BlobStore {
        val blobs = mutableMapOf<String, ByteArray>()

        override suspend fun hasBlob(sha: String): Result<Boolean, DataError.Local> = Result.Success(blobs.containsKey(sha))

        override suspend fun writeBlob(
            sha: String,
            bytes: ByteArray,
        ): EmptyResult<DataError.Local> {
            blobs[sha] = bytes
            return Result.Success(Unit)
        }

        override suspend fun readBlob(sha: String): Result<ByteArray, DataError.Local> = blobs[sha]
            ?.let { Result.Success(it) }
            ?: Result.Error(DataError.Local.NOT_FOUND)
    }

    private class FakePatchStore : PatchStore {
        val patches = mutableListOf<WrittenPatch>()

        override suspend fun writePatch(
            commitSha: String,
            path: String,
            patch: String,
        ): EmptyResult<DataError.Local> {
            patches += WrittenPatch(commitSha = commitSha, path = path, patch = patch)
            return Result.Success(Unit)
        }

        override suspend fun readPatch(
            commitSha: String,
            path: String,
        ): Result<String, DataError.Local> = patches
            .firstOrNull { it.commitSha == commitSha && it.path == path }
            ?.let { Result.Success(it.patch) }
            ?: Result.Error(DataError.Local.NOT_FOUND)
    }

    private data class WrittenPatch(
        val commitSha: String,
        val path: String,
        val patch: String,
    )

    private companion object {
        const val BLOCK_SIZE = 512

        fun tarOf(vararg entries: ByteArray): ByteArray = entries.fold(byteArrayOf()) { acc, entry ->
            acc + entry
        } + ByteArray(BLOCK_SIZE * 2)

        fun entry(
            path: String,
            content: ByteArray,
        ): ByteArray {
            val header = ByteArray(BLOCK_SIZE)
            header.writeText(offset = 0, length = 100, value = path)
            header.writeText(offset = 124, length = 12, value = content.size.toString(radix = 8).padStart(length = 11, padChar = '0'))
            header[156] = '0'.code.toByte()
            val padding = ByteArray((BLOCK_SIZE - content.size % BLOCK_SIZE) % BLOCK_SIZE)
            return header + content + padding
        }

        fun ByteArray.writeText(
            offset: Int,
            length: Int,
            value: String,
        ) {
            value.encodeToByteArray()
                .take(length)
                .forEachIndexed { index, byte -> this[offset + index] = byte }
        }
    }
}
