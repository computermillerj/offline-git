package dev.jem.offlinegit.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.jem.offlinegit.data.storage.createAndroidPatchStore
import dev.jem.offlinegit.data.storage.createAndroidRepositoryStore
import dev.jem.offlinegit.diff.GithubPatchDiffProvider

class OfflineGitViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repositoryStore = createAndroidRepositoryStore(context)
        val patchStore = createAndroidPatchStore(context)
        return OfflineGitViewModel(
            repositoryStore = repositoryStore,
            patchStore = patchStore,
            diffProvider = GithubPatchDiffProvider(patchStore),
        ) as T
    }
}
