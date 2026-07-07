package dev.jem.offlinegit.data.storage

import dev.jem.offlinegit.domain.DataError
import kotlinx.io.IOException

// The stores map exceptions to a bare enum, which hides the root cause behind
// "local storage error" in the UI. Print the trace so logcat keeps it, and
// surface a full disk as DISK_FULL — the one local failure the user can fix.
internal fun IOException.toLocalError(operation: String): DataError.Local {
    println("OfflineGit: $operation failed: ${stackTraceToString()}")
    val isDiskFull = message.orEmpty().contains("ENOSPC") ||
        message.orEmpty().contains("no space left", ignoreCase = true)
    return if (isDiskFull) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN
}
