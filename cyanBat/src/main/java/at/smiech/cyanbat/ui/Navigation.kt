package at.smiech.cyanbat.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String) {
    data object Main : Screen("main")

    data object Settings : Screen("settings")

    data object Credits : Screen("credits")
}

@Composable
fun MainNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainMenuScreen(navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Credits.route) {
            CreditsScreen()
        }
    }
}