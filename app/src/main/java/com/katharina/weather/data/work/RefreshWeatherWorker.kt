package com.katharina.weather.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.katharina.weather.domain.usecase.RefreshResult
import com.katharina.weather.domain.usecase.RefreshSelectedPlaceUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RefreshWeatherWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshSelectedPlaceUseCase: RefreshSelectedPlaceUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (refreshSelectedPlaceUseCase()) {
            RefreshResult.Done, RefreshResult.GiveUp -> Result.success()
            RefreshResult.Retry -> {
                if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME = "weather-refresh"
        const val REFRESH_INTERVAL_MINUTES = 60L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWeatherWorker>(
                REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
