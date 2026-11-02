package com.katharina.weather.ui.debug

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.katharina.weather.data.work.RefreshWeatherWorker

fun triggerDebugWorker(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        "weather-refresh-debug",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<RefreshWeatherWorker>().build()
    )
}
