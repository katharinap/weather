package com.katharina.weather.di

import com.katharina.weather.data.notification.AlertNotifierImpl
import com.katharina.weather.data.notification.PermissionCheckerImpl
import com.katharina.weather.domain.notification.AlertNotifier
import com.katharina.weather.domain.notification.PermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindAlertNotifier(
        impl: AlertNotifierImpl
    ): AlertNotifier

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(
        impl: PermissionCheckerImpl
    ): PermissionChecker
}
