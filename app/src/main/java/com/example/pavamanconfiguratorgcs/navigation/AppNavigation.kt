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
import com.example.pavamanconfiguratorgcs.ui.configurations.EscCalibrationScreen
import com.example.pavamanconfiguratorgcs.ui.configurations.EscCalibrationViewModel
import com.example.pavamanconfiguratorgcs.ui.configurations.FlightModesScreen
import com.example.pavamanconfiguratorgcs.ui.configurations.FlightModesViewModel
import com.example.pavamanconfiguratorgcs.ui.connection.ConnectionScreen
import com.example.pavamanconfiguratorgcs.ui.connection.ConnectionViewModel
import com.example.pavamanconfiguratorgcs.ui.fullparams.ParametersScreen
import com.example.pavamanconfiguratorgcs.ui.fullparams.ParametersViewModel
import com.example.pavamanconfiguratorgcs.ui.home.HomeScreen
import com.example.pavamanconfiguratorgcs.ui.home.HomeViewModel
import com.example.pavamanconfiguratorgcs.ui.configurations.FailsafeScreen
import com.example.pavamanconfiguratorgcs.ui.configurations.BatteryMonitorScreen

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object Home : Screen("home")
    object Configurations : Screen("configurations")
    object FullParams : Screen("full_params")
    object EscCalibration : Screen("esc_calibration")
    object FrameType : Screen("frame_type")
    object FlightModes : Screen("flight_modes")
    object MotorTest : Screen("motor_test")
    object Failsafe : Screen("failsafe")
    object BatteryMonitor : Screen("battery_monitor")
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
                },
                onNavigateToEscCalibration = {
                    navController.navigate(Screen.EscCalibration.route)
                },
                onNavigateToFrameType = {
                    navController.navigate(Screen.FrameType.route)
                },
                onNavigateToFlightModes = {
                    navController.navigate(Screen.FlightModes.route)
                },
                onNavigateToMotorTest = {
                    navController.navigate(Screen.MotorTest.route)
                },
                onNavigateToFailsafe = {
                    navController.navigate(Screen.Failsafe.route)
                },
                onNavigateToBatteryMonitor = {
                    navController.navigate(Screen.BatteryMonitor.route)
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

        composable(Screen.EscCalibration.route) {
            // Create EscCalibrationViewModel with dependencies
            val escCalibrationViewModel: EscCalibrationViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = com.example.pavamanconfiguratorgcs.data.ParameterRepository(telemetryRepository)
                        return EscCalibrationViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )

            EscCalibrationScreen(
                viewModel = escCalibrationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.FrameType.route) {
            // Create FrameTypeViewModel with dependencies using fully-qualified name to avoid import issues
            val frameTypeViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.FrameTypeViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = com.example.pavamanconfiguratorgcs.data.ParameterRepository(telemetryRepository)
                        return com.example.pavamanconfiguratorgcs.ui.configurations.FrameTypeViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )

            // Use fully-qualified composable reference
            com.example.pavamanconfiguratorgcs.ui.configurations.FrameTypeScreen(
                viewModel = frameTypeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.FlightModes.route) {
            // Create FlightModesViewModel with dependencies
            val flightModesViewModel: FlightModesViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = com.example.pavamanconfiguratorgcs.data.ParameterRepository(telemetryRepository)
                        return FlightModesViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )

            FlightModesScreen(
                viewModel = flightModesViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.MotorTest.route) {
            // Create MotorTestViewModel with dependencies
            val motorTestViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.MotorTestViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = com.example.pavamanconfiguratorgcs.data.ParameterRepository(telemetryRepository)
                        return com.example.pavamanconfiguratorgcs.ui.configurations.MotorTestViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.MotorTestScreen(
                viewModel = motorTestViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Failsafe.route) {
            val failsafeViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.FailsafeViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = com.example.pavamanconfiguratorgcs.data.ParameterRepository(telemetryRepository)
                        return com.example.pavamanconfiguratorgcs.ui.configurations.FailsafeViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )
            FailsafeScreen(
                viewModel = failsafeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.BatteryMonitor.route) {
            BatteryMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
