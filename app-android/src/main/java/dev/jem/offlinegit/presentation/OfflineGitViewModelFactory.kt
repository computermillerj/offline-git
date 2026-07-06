package dev.jem.offlinegit.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.jem.offlinegit.data.github.GithubHttpClientFactory
import dev.jem.offlinegit.data.github.KtorGithubRemoteDataSource
import dev.jem.offlinegit.data.storage.createAndroidBlobStore
import dev.jem.offlinegit.data.storage.createAndroidPatchStore
import dev.jem.offlinegit.data.storage.createAndroidRepositoryStore
import dev.jem.offlinegit.diff.FallbackDiffProvider
import dev.jem.offlinegit.diff.GithubPatchDiffProvider
import dev.jem.offlinegit.diff.LocalBlobDiffProvider
import dev.jem.offlinegit.sync.GithubBranchDownloader
import io.ktor.client.engine.okhttp.OkHttp

class OfflineGitViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repositoryStore = createAndroidRepositoryStore(context)
        val patchStore = createAndroidPatchStore(context)
        val blobStore = createAndroidBlobStore(context)
        val diffProvider = FallbackDiffProvider(
            primary = GithubPatchDiffProvider(patchStore),
            fallback = LocalBlobDiffProvider(blobStore),
        )
        val tokenStore = MutableTokenStore()
        val httpClient = GithubHttpClientFactory.create(OkHttp.create())
        val remoteDataSource = KtorGithubRemoteDataSource(
            httpClient = httpClient,
            tokenStore = tokenStore,
        )
        return OfflineGitViewModel(
            repositoryStore = repositoryStore,
            patchStore = patchStore,
            diffProvider = diffProvider,
            remoteDataSource = remoteDataSource,
            branchDownloader = GithubBranchDownloader(
                remoteDataSource = remoteDataSource,
                repositoryStore = repositoryStore,
                patchStore = patchStore,
            ),
            tokenStore = tokenStore,
            closeRemoteResources = { httpClient.close() },
        ) as T
    }
}
