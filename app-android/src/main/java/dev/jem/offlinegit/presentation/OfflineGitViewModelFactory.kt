package dev.jem.offlinegit.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.jem.offlinegit.auth.AndroidEncryptedTokenStore
import dev.jem.offlinegit.data.github.GithubHttpClientFactory
import dev.jem.offlinegit.data.github.KtorGithubDeviceAuthDataSource
import dev.jem.offlinegit.data.storage.createAndroidBlobStore
import dev.jem.offlinegit.data.storage.createAndroidPatchStore
import dev.jem.offlinegit.data.storage.createAndroidRepositoryStore
import dev.jem.offlinegit.diff.FallbackDiffProvider
import dev.jem.offlinegit.diff.GithubPatchDiffProvider
import dev.jem.offlinegit.diff.LocalBlobDiffProvider
import dev.jem.offlinegit.sync.KtorGithubSessionFactory
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
        val tokenStore = AndroidEncryptedTokenStore(context)
        val httpClient = GithubHttpClientFactory.create(OkHttp.create())
        return OfflineGitViewModel(
            repositoryStore = repositoryStore,
            patchStore = patchStore,
            diffProvider = diffProvider,
            sessionFactory = KtorGithubSessionFactory(
                httpClient = httpClient,
                accountTokenStore = tokenStore,
                repositoryStore = repositoryStore,
                patchStore = patchStore,
                blobStore = blobStore,
            ),
            authDataSource = KtorGithubDeviceAuthDataSource(httpClient),
            accountTokenStore = tokenStore,
            closeRemoteResources = { httpClient.close() },
        ) as T
    }
}
