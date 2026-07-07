package dev.jem.offlinegit.data.storage

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.jem.offlinegit.domain.BlobStore
import dev.jem.offlinegit.domain.PatchStore
import dev.jem.offlinegit.domain.RepositoryStore
import okio.Path.Companion.toPath

fun createAndroidRepositoryStore(
    context: Context,
    databaseName: String = "offline-git.db",
): RepositoryStore {
    // Foreign keys stay OFF deliberately: the store leans on INSERT OR REPLACE,
    // and with enforcement on, each REPLACE deletes the old row first — firing
    // the ON DELETE CASCADEs and silently wiping child rows (e.g. a repository
    // refresh would erase downloaded branches). Cleanup is done with explicit
    // deletes in removeAccount instead.
    val driver = AndroidSqliteDriver(
        schema = OfflineGitDatabase.Schema,
        context = context.applicationContext,
        name = databaseName,
    )
    return SqlDelightRepositoryStore(OfflineGitDatabase(driver))
}

fun createAndroidBlobStore(context: Context): BlobStore = OkioBlobStore(
    root = context.cacheRoot(),
)

fun createAndroidPatchStore(context: Context): PatchStore = OkioPatchStore(
    root = context.cacheRoot(),
)

private fun Context.cacheRoot() = applicationContext.filesDir.resolve("offline-cache").absolutePath.toPath()
