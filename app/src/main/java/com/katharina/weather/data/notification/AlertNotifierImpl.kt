package com.katharina.weather.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.katharina.weather.MainActivity
import com.katharina.weather.R
import com.katharina.weather.domain.model.Place
import com.katharina.weather.domain.model.WeatherAlert
import com.katharina.weather.domain.notification.AlertNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlertNotifier {

    init {
        createNotificationChannel()
    }

    @Suppress("MissingPermission")
    override suspend fun postNotification(place: Place, alert: WeatherAlert) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "alerts")
            putExtra("place_id", place.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "${alert.severity.name.lowercase().replaceFirstChar { it.titlecase() }} weather warning · ${place.name}"
        val text = alert.headline ?: alert.event

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val now = Instant.now()
        val expires = alert.expires
        if (expires != null && expires > now) {
            val timeoutMillis = Duration.between(now, expires).toMillis()
            if (timeoutMillis > 0) {
                builder.setTimeoutAfter(timeoutMillis)
            }
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(alert.id.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Weather warnings"
            val descriptionText = "Official DWD weather warning notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "weather_warnings"
    }
}
