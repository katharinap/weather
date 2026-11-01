package com.katharina.weather.ui.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatRelativeTime(
    time: Instant,
    now: Instant,
    zoneId: ZoneId = ZoneId.of("Europe/Berlin")
): String {
    val duration = Duration.between(time, now)
    val seconds = duration.seconds
    if (seconds < 60) {
        return "just now"
    }

    val minutes = duration.toMinutes()
    if (minutes < 60) {
        return "$minutes min ago"
    }

    val hours = duration.toHours()
    if (hours < 24) {
        return "$hours h ago"
    }

    val dateAtTime = time.atZone(zoneId).toLocalDate()
    val dateAtNow = now.atZone(zoneId).toLocalDate()

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val formattedTime = timeFormatter.format(time.atZone(zoneId))

    if (dateAtTime == dateAtNow.minusDays(1)) {
        return "yesterday $formattedTime"
    }

    val dateFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
    return dateFormatter.format(time.atZone(zoneId))
}
