package com.katharina.weather.ui.home.chart

fun scale(value: Double, min: Double, max: Double, size: Float): Float =
    if (max <= min) size / 2f else (size * (1 - (value - min) / (max - min))).toFloat()

fun scaleBar(value: Double, max: Double, size: Float): Float =
    if (max <= 0.0) 0f else (size * (value / max)).coerceIn(0.0, size.toDouble()).toFloat()
