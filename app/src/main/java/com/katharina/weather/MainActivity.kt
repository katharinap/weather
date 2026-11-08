package com.katharina.weather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.katharina.weather.ui.navigation.WeatherNavGraph
import com.katharina.weather.ui.theme.WeatherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateToAlerts = intent?.getStringExtra("navigate_to") == "alerts"

        setContent {
            WeatherTheme {
                WeatherNavGraph(navigateToAlerts = navigateToAlerts)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
