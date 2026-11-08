package com.katharina.weather.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.katharina.weather.domain.notification.PermissionChecker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionCheckerImpl @Inject constructor() : PermissionChecker {
    override fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
