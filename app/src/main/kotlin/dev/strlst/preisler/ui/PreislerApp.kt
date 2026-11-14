package dev.strlst.preisler.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.strlst.preisler.ui.home.HomeScreen
import dev.strlst.preisler.ui.scan.ScanScreen

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
}

@Composable
fun PreislerApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onScanClick = { navController.navigate(Routes.SCAN) })
        }
        composable(Routes.SCAN) {
            ScanScreen(onBack = { navController.popBackStack() })
        }
    }
}
