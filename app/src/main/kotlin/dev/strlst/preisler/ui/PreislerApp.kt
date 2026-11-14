package dev.strlst.preisler.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.strlst.preisler.PreislerApplication
import dev.strlst.preisler.ui.home.HomeScreen
import dev.strlst.preisler.ui.result.ResultScreen
import dev.strlst.preisler.ui.scan.ScanScreen
import dev.strlst.preisler.ui.scan.ScanViewModel

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val RESULT = "result"
}

@Composable
fun PreislerApp() {
    val container = (LocalContext.current.applicationContext as PreislerApplication).container
    // Created here (activity scope) so the scan and result screens share one state.
    val factory = viewModelFactory { initializer { ScanViewModel(container.ocrEngine, container.extractor) } }
    val scanViewModel: ScanViewModel = viewModel(factory = factory)
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScanClick = {
                    scanViewModel.reset()
                    navController.navigate(Routes.SCAN)
                },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                viewModel = scanViewModel,
                onBack = { navController.popBackStack() },
                onResult = {
                    navController.navigate(Routes.RESULT) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                viewModel = scanViewModel,
                onRetry = {
                    scanViewModel.reset()
                    navController.navigate(Routes.SCAN) { popUpTo(Routes.RESULT) { inclusive = true } }
                },
                onClose = {
                    scanViewModel.reset()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}
