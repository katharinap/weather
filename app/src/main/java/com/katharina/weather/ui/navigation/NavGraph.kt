package com.katharina.weather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.katharina.weather.ui.home.HomeRoute
import com.katharina.weather.ui.places.PlacesRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

@Serializable
data object PlacesDestination

@Composable
fun WeatherNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination
    ) {
        composable<HomeDestination> {
            HomeRoute(
                onNavigateToPlaces = { navController.navigate(PlacesDestination) }
            )
        }
        composable<PlacesDestination> {
            PlacesRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
