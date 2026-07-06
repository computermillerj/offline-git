package dev.jem.offlinegit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.jem.offlinegit.presentation.OfflineGitRoot
import dev.jem.offlinegit.presentation.OfflineGitViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OfflineGitRoot(
                viewModelFactory = OfflineGitViewModelFactory(applicationContext),
            )
        }
    }
}
