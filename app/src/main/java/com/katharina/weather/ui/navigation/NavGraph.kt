package com.katharina.weather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.katharina.weather.ui.alerts.AlertsRoute
import com.katharina.weather.ui.home.HomeRoute
import com.katharina.weather.ui.places.PlacesRoute
import kotlinx.serialization.Serializable

@Serializable
object HomeDestination

@Serializable
object PlacesDestination

@Serializable
object AlertsDestination

@Composable
fun WeatherNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier
    ) {
        composable<HomeDestination> {
            HomeRoute(
                onNavigateToPlaces = {
                    navController.navigate(PlacesDestination)
                },
                onNavigateToAlerts = {
                    navController.navigate(AlertsDestination)
                }
            )
        }
        composable<PlacesDestination> {
            PlacesRoute(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AlertsDestination> {
            AlertsRoute(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
