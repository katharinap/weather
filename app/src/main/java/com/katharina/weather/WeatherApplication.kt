package com.katharina.weather

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.katharina.weather.data.notification.AlertNotifierImpl
import com.katharina.weather.data.work.RefreshWeatherWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WeatherApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var alertNotifierImpl: AlertNotifierImpl

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        alertNotifierImpl.createNotificationChannel()
        RefreshWeatherWorker.schedule(this)
    }
}
