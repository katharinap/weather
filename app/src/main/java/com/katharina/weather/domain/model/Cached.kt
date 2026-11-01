package com.katharina.weather.domain.model

import java.time.Instant

data class Cached<T>(
    val value: T,
    val fetchedAt: Instant
)
