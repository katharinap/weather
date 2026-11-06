package com.katharina.weather.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAlertValidity(
    onset: Instant?,
    expires: Instant?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.of("Europe/Berlin"),
    locale: Locale = Locale.US
): String {
    if (onset == null && expires == null) return ""

    val today = now.atZone(zoneId).toLocalDate()
    val tomorrow = today.plusDays(1)

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    val onsetZdt = onset?.atZone(zoneId)
    val expiresZdt = expires?.atZone(zoneId)

    val onsetDate = onsetZdt?.toLocalDate()
    val expiresDate = expiresZdt?.toLocalDate()

    fun formatDateLabel(date: java.time.LocalDate): String {
        return when (date) {
            today -> "Today"
            tomorrow -> "Tomorrow"
            else -> dateFormatter.format(date)
        }
    }

    if (onsetZdt != null && expiresZdt != null) {
        val onTime = timeFormatter.format(onsetZdt)
        val expTime = timeFormatter.format(expiresZdt)

        return if (onsetDate == expiresDate) {
            val label = formatDateLabel(onsetDate!!)
            if (onsetDate == today || onsetDate == tomorrow) {
                "$label $onTime – $expTime"
            } else {
                "$label, $onTime – $expTime"
            }
        } else {
            val onLabel = formatDateLabel(onsetDate!!)
            val expLabel = formatDateLabel(expiresDate!!)
            val onStr = if (onsetDate == today || onsetDate == tomorrow) "$onLabel $onTime" else "$onLabel, $onTime"
            val expStr = if (expiresDate == today || expiresDate == tomorrow) "$expLabel $expTime" else "$expLabel, $expTime"
            "$onStr – $expStr"
        }
    }

    if (expiresZdt != null) {
        val expTime = timeFormatter.format(expiresZdt)
        val label = formatDateLabel(expiresDate!!)
        val expStr = if (expiresDate == today || expiresDate == tomorrow) "$label $expTime" else "$label, $expTime"
        return "Valid until $expStr"
    }

    if (onsetZdt != null) {
        val onTime = timeFormatter.format(onsetZdt)
        val label = formatDateLabel(onsetDate!!)
        val onStr = if (onsetDate == today || onsetDate == tomorrow) "$label $onTime" else "$label, $onTime"
        return "From $onStr"
    }

    return ""
}
