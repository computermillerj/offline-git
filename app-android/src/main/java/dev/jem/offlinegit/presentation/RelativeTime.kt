package dev.jem.offlinegit.presentation

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders an ISO-8601 timestamp (GitHub's `updatedAt` / `committedAt`) as a short
 * relative label like the mockup ("2h ago", "yesterday", "3d ago", "1w ago"),
 * falling back to an absolute date for anything older than a month.
 */
fun relativeTime(iso: String?, now: Instant = Instant.now()): String? {
    val instant = iso.toInstantOrNull() ?: return null
    val seconds = Duration.between(instant, now).seconds
    if (seconds < 0) return "just now"
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "yesterday"
        days < 7 -> "${days}d ago"
        weeks < 5 -> "${weeks}w ago"
        else -> instant.atZone(ZoneId.systemDefault()).format(ABSOLUTE_DATE)
    }
}

/** Absolute label like the diff header "Jul 6, 11:02". */
fun absoluteDateTime(iso: String?, now: Instant = Instant.now()): String? {
    val instant = iso.toInstantOrNull() ?: return null
    val zoned = instant.atZone(ZoneId.systemDefault())
    val nowYear = now.atZone(ZoneId.systemDefault()).year
    val formatter = if (zoned.year == nowYear) SAME_YEAR_DATE_TIME else FULL_DATE_TIME
    return zoned.format(formatter)
}

private fun String?.toInstantOrNull(): Instant? {
    if (this.isNullOrBlank()) return null
    return runCatching { Instant.parse(this) }
        .recoverCatching { OffsetDateTime.parse(this).toInstant() }
        .getOrNull()
}

private val ABSOLUTE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val SAME_YEAR_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private val FULL_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm")
