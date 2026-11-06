package com.katharina.weather.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.katharina.weather.R
import com.katharina.weather.domain.model.AlertSeverity

data class AlertSeverityStyle(
    val color: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val label: String,
    val iconRes: Int
)

@Composable
fun AlertSeverity.getStyle(isDark: Boolean = false): AlertSeverityStyle {
    return when (this) {
        AlertSeverity.MINOR -> AlertSeverityStyle(
            color = if (isDark) Color(0xFFFDD835) else Color(0xFFFBC02D),
            containerColor = if (isDark) Color(0xFF332F00) else Color(0xFFFFFDE7),
            onContainerColor = if (isDark) Color(0xFFFFF59D) else Color(0xFF574200),
            label = "Weather warning",
            iconRes = R.drawable.ic_warning
        )
        AlertSeverity.MODERATE -> AlertSeverityStyle(
            color = if (isDark) Color(0xFFFB8C00) else Color(0xFFF57C00),
            containerColor = if (isDark) Color(0xFF331D00) else Color(0xFFFFF3E0),
            onContainerColor = if (isDark) Color(0xFFFFCC80) else Color(0xFF5A2A00),
            label = "Marked weather warning",
            iconRes = R.drawable.ic_warning
        )
        AlertSeverity.SEVERE -> AlertSeverityStyle(
            color = if (isDark) Color(0xFFE53935) else Color(0xFFD32F2F),
            containerColor = if (isDark) Color(0xFF330A0A) else Color(0xFFFFEBEE),
            onContainerColor = if (isDark) Color(0xFFEF9A9A) else Color(0xFF5C0000),
            label = "Severe weather warning",
            iconRes = R.drawable.ic_warning
        )
        AlertSeverity.EXTREME -> AlertSeverityStyle(
            color = if (isDark) Color(0xFF8E24AA) else Color(0xFF7B1FA2),
            containerColor = if (isDark) Color(0xFF260033) else Color(0xFFF3E5F5),
            onContainerColor = if (isDark) Color(0xFFCE93D8) else Color(0xFF4A0060),
            label = "Extreme weather warning",
            iconRes = R.drawable.ic_warning
        )
        AlertSeverity.UNKNOWN -> AlertSeverityStyle(
            color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
            containerColor = if (isDark) Color(0xFF212121) else Color(0xFFF5F5F5),
            onContainerColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF424242),
            label = "Weather notice",
            iconRes = R.drawable.ic_info
        )
    }
}
