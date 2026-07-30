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
                navArgument("originLat") { type = NavType.FloatType },
                navArgument("originLng") { type = NavType.FloatType },
                navArgument("destLat") { type = NavType.FloatType },
                navArgument("destLng") { type = NavType.FloatType },
            )
        ) { backStackEntry ->
            ResultsScreen(
                originLat = backStackEntry.arguments?.getFloat("originLat")?.toDouble() ?: 0.0,
                originLng = backStackEntry.arguments?.getFloat("originLng")?.toDouble() ?: 0.0,
                destLat = backStackEntry.arguments?.getFloat("destLat")?.toDouble() ?: 0.0,
                destLng = backStackEntry.arguments?.getFloat("destLng")?.toDouble() ?: 0.0,
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
