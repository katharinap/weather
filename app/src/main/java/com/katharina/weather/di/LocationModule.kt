package com.katharina.weather.di

import com.katharina.weather.data.location.LocationProviderImpl
import com.katharina.weather.domain.repository.LocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(
        impl: LocationProviderImpl
    ): LocationProvider
}
