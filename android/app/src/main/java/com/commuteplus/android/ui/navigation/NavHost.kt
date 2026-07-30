package com.commuteplus.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.commuteplus.android.ui.screens.detail.JourneyDetailScreen
import com.commuteplus.android.ui.screens.results.ResultsScreen
import com.commuteplus.android.ui.screens.search.SearchScreen

// Coordinates are passed as String route args (parsed to Double) to preserve full precision.
// NavType.FloatType would silently truncate lat/lng and shift positions by several meters.

/**
 * App navigation graph.
 *
 * Screens:
 *   search   → user enters from/to
 *   results  → list of journey options
 *   detail   → step-by-step legs for a chosen journey
 */
@Composable
fun CommutePlusNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "search") {
        composable("search") {
            SearchScreen(
                onPlanJourney = { originLat, originLng, destLat, destLng ->
                    navController.navigate(
                        "results/$originLat/$originLng/$destLat/$destLng"
                    )
                }
            )
        }

        composable(
            route = "results/{originLat}/{originLng}/{destLat}/{destLng}",
            arguments = listOf(
                navArgument("originLat") { type = NavType.StringType },
                navArgument("originLng") { type = NavType.StringType },
                navArgument("destLat") { type = NavType.StringType },
                navArgument("destLng") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            ResultsScreen(
                originLat = args?.getString("originLat")?.toDoubleOrNull() ?: 0.0,
                originLng = args?.getString("originLng")?.toDoubleOrNull() ?: 0.0,
                destLat = args?.getString("destLat")?.toDoubleOrNull() ?: 0.0,
                destLng = args?.getString("destLng")?.toDoubleOrNull() ?: 0.0,
                onJourneySelected = { journeyIndex ->
                    navController.navigate("detail/$journeyIndex")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "detail/{journeyIndex}",
            arguments = listOf(
                navArgument("journeyIndex") { type = NavType.IntType },
            )
        ) { backStackEntry ->
            JourneyDetailScreen(
                journeyIndex = backStackEntry.arguments?.getInt("journeyIndex") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
