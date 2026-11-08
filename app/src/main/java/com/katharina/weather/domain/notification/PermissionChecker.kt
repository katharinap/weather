package com.katharina.weather.domain.notification

import android.content.Context

interface PermissionChecker {
    fun hasNotificationPermission(context: Context): Boolean
}
