package com.example.pavamanconfiguratorgcs.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pavamanconfiguratorgcs.PavamanApplication
import com.example.pavamanconfiguratorgcs.ui.SharedViewModel
import com.example.pavamanconfiguratorgcs.ui.ViewModelFactory
import com.example.pavamanconfiguratorgcs.ui.configurations.ConfigurationsScreen
import com.example.pavamanconfiguratorgcs.ui.connection.ConnectionScreen
import com.example.pavamanconfiguratorgcs.ui.connection.ConnectionViewModel
import com.example.pavamanconfiguratorgcs.ui.fullparams.ParametersScreen
import com.example.pavamanconfiguratorgcs.ui.fullparams.ParametersViewModel
import com.example.pavamanconfiguratorgcs.ui.home.HomeScreen
import com.example.pavamanconfiguratorgcs.ui.home.HomeViewModel

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object Home : Screen("home")
    object Configurations : Screen("configurations")
    object FullParams : Screen("full_params")
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as PavamanApplication

    // Create shared ViewModel with the repository from the Application
    val sharedViewModel: SharedViewModel = viewModel(
        factory = ViewModelFactory(application.telemetryRepository)
    )

    val telemetryRepository = sharedViewModel.getTelemetryRepository()

    NavHost(
        navController = navController,
        startDestination = Screen.Connection.route,
        modifier = modifier
    ) {
        composable(Screen.Connection.route) {
            // Create ConnectionViewModel with the shared repository
            val connectionViewModel: ConnectionViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return ConnectionViewModel(context, telemetryRepository) as T
                    }
                }
            )

            ConnectionScreen(
                viewModel = connectionViewModel,
                onConnectionSuccess = {
                    // Navigate to home screen after successful connection
                    navController.navigate(Screen.Home.route) {
                        // Remove connection screen from back stack
                        popUpTo(Screen.Connection.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            // Create HomeViewModel with the shared repository
            val homeViewModel: HomeViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return HomeViewModel(telemetryRepository) as T
                    }
                }
            )

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToConfigurations = {
                    navController.navigate(Screen.Configurations.route)
                },
                onNavigateToFullParams = {
                    navController.navigate(Screen.FullParams.route)
                }
            )
        }

        composable(Screen.Configurations.route) {
            ConfigurationsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.FullParams.route) {
            // Create ParametersViewModel with the shared TelemetryRepository
            val parametersViewModel: ParametersViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return ParametersViewModel(telemetryRepository) as T
                    }
                }
            )

            ParametersScreen(
                viewModel = parametersViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
