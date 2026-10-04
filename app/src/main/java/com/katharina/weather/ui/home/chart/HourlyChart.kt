package com.katharina.weather.ui.home.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katharina.weather.R
import com.katharina.weather.domain.model.HourlyForecast
import com.katharina.weather.domain.model.WeatherCondition
import com.katharina.weather.ui.theme.WeatherTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@Composable
fun HourlyChart(
    hours: List<HourlyForecast>,
    modifier: Modifier = Modifier
) {
    val chartHours = hours.take(48)
    if (chartHours.isEmpty()) return

    val temps = chartHours.mapNotNull { it.temperature }
    val minTemp = if (temps.isNotEmpty()) temps.minOrNull()!! - 1.0 else 0.0
    val maxTemp = if (temps.isNotEmpty()) temps.maxOrNull()!! + 1.0 else 10.0

    val precips = chartHours.mapNotNull { it.precipitation }
    val totalRain = precips.sum()
    val maxPrecip = max(1.0, precips.maxOrNull() ?: 0.0)

    val rainDescription = if (totalRain > 0.0) {
        stringResource(R.string.chart_rain_expected, totalRain)
    } else {
        stringResource(R.string.chart_no_rain)
    }
    val summary = stringResource(
        R.string.chart_title
    ) + ": " + (if (temps.isNotEmpty()) "${temps.minOrNull()!!.toInt()} to ${temps.maxOrNull()!!.toInt()}°" else "–") + ", $rainDescription"

    val lineColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Small Precipitation Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(barColor)
                    )
                    Text(
                        text = stringResource(R.string.chart_precipitation_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height
                val paddingLeft = 32.dp.toPx()
                val paddingRight = 16.dp.toPx()
                val paddingTop = 16.dp.toPx()
                val paddingBottom = 24.dp.toPx()

                val graphWidth = width - paddingLeft - paddingRight
                val graphHeight = height - paddingTop - paddingBottom

                if (chartHours.size < 2) return@Canvas

                val stepX = graphWidth / (chartHours.size - 1)

                // 1. Draw Precipitation Bars at the bottom
                val maxBarHeight = graphHeight * 0.4f
                val barWidth = (stepX * 0.6f).coerceAtLeast(2f)

                chartHours.forEachIndexed { i, hour ->
                    val p = hour.precipitation ?: 0.0
                    if (p > 0.0) {
                        val barH = scaleBar(p, maxPrecip, maxBarHeight)
                        val x = paddingLeft + i * stepX - (barWidth / 2f)
                        val y = paddingTop + graphHeight - barH
                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barH)
                        )
                    }
                }

                // 2. Draw Temperature Line (leaving gaps for nulls)
                val path = Path()
                var inPath = false

                chartHours.forEachIndexed { i, hour ->
                    val t = hour.temperature
                    val x = paddingLeft + i * stepX

                    if (t != null) {
                        val y = paddingTop + scale(t, minTemp, maxTemp, graphHeight)
                        if (!inPath) {
                            path.moveTo(x, y)
                            inPath = true
                        } else {
                            path.lineTo(x, y)
                        }
                    } else {
                        inPath = false
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 3. Draw X-axis Time Labels every 6 hours (Midnight shows Weekday name)
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor.hashCode()
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.of("Europe/Berlin"))

                chartHours.forEachIndexed { i, hour ->
                    if (i % 6 == 0) {
                        val x = paddingLeft + i * stepX
                        val zdt = hour.timestamp.atZone(ZoneId.of("Europe/Berlin"))
                        val timeStr = if (zdt.hour == 0) {
                            zdt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        } else {
                            timeFormatter.format(hour.timestamp)
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            timeStr,
                            x,
                            height - 4.dp.toPx(),
                            textPaint
                        )
                        // Grid line
                        drawLine(
                            color = gridColor,
                            start = Offset(x, paddingTop),
                            end = Offset(x, paddingTop + graphHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // 4. Y-axis Temp Labels (min and max)
                if (temps.isNotEmpty()) {
                    val yPaint = android.graphics.Paint().apply {
                        color = labelColor.hashCode()
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                        isAntiAlias = true
                    }
                    val maxTempStr = "${temps.maxOrNull()!!.toInt()}°"
                    val minTempStr = "${temps.minOrNull()!!.toInt()}°"

                    val maxY = paddingTop + scale(temps.maxOrNull()!!, minTemp, maxTemp, graphHeight)
                    val minY = paddingTop + scale(temps.minOrNull()!!, minTemp, maxTemp, graphHeight)

                    drawContext.canvas.nativeCanvas.drawText(maxTempStr, 0f, maxY + 4.dp.toPx(), yPaint)
                    drawContext.canvas.nativeCanvas.drawText(minTempStr, 0f, minY + 4.dp.toPx(), yPaint)
                }
            }
        }
    }
}

// Previews
@Preview(showBackground = true)
@Composable
fun HourlyChartNormalPreview() {
    WeatherTheme {
        HourlyChart(
            hours = (0..24).map { h ->
                HourlyForecast(
                    timestamp = Instant.now().plusSeconds(h * 3600L),
                    temperature = 12.0 + (h % 8),
                    condition = WeatherCondition.CLOUDY,
                    icon = "cloudy",
                    precipitation = if (h in 10..14) 0.5 else 0.0,
                    precipitationProbability = 20,
                    windSpeed = 5.0,
                    windGustSpeed = 10.0,
                    windDirection = 0,
                    cloudCover = 50,
                    sunshine = 0.0,
                    relativeHumidity = 50
                )
            }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HourlyChartDarkPreview() {
    WeatherTheme {
        HourlyChart(
            hours = (0..24).map { h ->
                HourlyForecast(
                    timestamp = Instant.now().plusSeconds(h * 3600L),
                    temperature = 15.0 + (h % 5),
                    condition = WeatherCondition.PARTLY_CLOUDY_DAY,
                    icon = "partly-cloudy-day",
                    precipitation = 0.0,
                    precipitationProbability = 0,
                    windSpeed = 5.0,
                    windGustSpeed = 10.0,
                    windDirection = 0,
                    cloudCover = 30,
                    sunshine = 0.0,
                    relativeHumidity = 50
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HourlyChartNullTempsPreview() {
    WeatherTheme {
        HourlyChart(
            hours = (0..24).map { h ->
                HourlyForecast(
                    timestamp = Instant.now().plusSeconds(h * 3600L),
                    temperature = null,
                    condition = WeatherCondition.UNKNOWN,
                    icon = "unknown",
                    precipitation = null,
                    precipitationProbability = null,
                    windSpeed = null,
                    windGustSpeed = null,
                    windDirection = null,
                    cloudCover = null,
                    sunshine = null,
                    relativeHumidity = null
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HourlyChartRainyDayPreview() {
    WeatherTheme {
        HourlyChart(
            hours = (0..24).map { h ->
                HourlyForecast(
                    timestamp = Instant.now().plusSeconds(h * 3600L),
                    temperature = 10.0 + (h % 3),
                    condition = WeatherCondition.RAIN,
                    icon = "rain",
                    precipitation = 2.5,
                    precipitationProbability = 80,
                    windSpeed = 12.0,
                    windGustSpeed = 25.0,
                    windDirection = 180,
                    cloudCover = 100,
                    sunshine = 0.0,
                    relativeHumidity = 90
                )
            }
        )
    }
}
