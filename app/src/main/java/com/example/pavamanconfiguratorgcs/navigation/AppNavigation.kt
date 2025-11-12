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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object Home : Screen("home")
    object Configurations : Screen("configurations")
    object FullParams : Screen("full_params")
    object EscCalibration : Screen("esc_calibration")
    object FrameType : Screen("frame_type")
    object FlightModes : Screen("flight_modes")
    object ServoOutput : Screen("servo_output")
    object SerialPorts : Screen("serial_ports")
    object MotorTest : Screen("motor_test")
    object Failsafe : Screen("failsafe")
    object BatteryMonitor : Screen("battery_monitor")
    object CompassCalibration : Screen("compass_calibration")
    object RCCalibration : Screen("rc_calibration")
    object IMUCalibration : Screen("imu_calibration")
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as PavamanApplication

    // Create shared ViewModel with the repository from the Application
    val sharedViewModel: SharedViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SharedViewModel(application.telemetryRepository) as T
            }
        }
    )

    val telemetryRepository = sharedViewModel.getTelemetryRepository()

    // Create app-scoped ParametersViewModel - created once and reused
    val parametersViewModel: ParametersViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ParametersViewModel(telemetryRepository) as T
            }
        }
    )

    // Read FCU detection state from the shared view model
    val fcuDetected by sharedViewModel.fcuDetected.collectAsState(initial = false)

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
                onNavigateToServoOutput = {
                    navController.navigate(Screen.ServoOutput.route)
                },
                onNavigateToSerialPorts = {
                    navController.navigate(Screen.SerialPorts.route)
                },
                onNavigateToMotorTest = {
                    navController.navigate(Screen.MotorTest.route)
                },
                onNavigateToFailsafe = {
                    navController.navigate(Screen.Failsafe.route)
                },
                onNavigateToBatteryMonitor = {
                    navController.navigate(Screen.BatteryMonitor.route)
                },
                onNavigateToCompassCalibration = {
                    navController.navigate(Screen.CompassCalibration.route)
                },
                onNavigateToRCCalibration = {
                    navController.navigate(Screen.RCCalibration.route)
                },
                onNavigateToIMUCalibration = {
                    navController.navigate(Screen.IMUCalibration.route)
                }
            )
        }

        composable(Screen.FullParams.route) {
            // Use app-scoped ParametersViewModel - no recreation on each navigation
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
                        val frameTypeRepository = com.example.pavamanconfiguratorgcs.data.repository.FrameTypeRepository(parameterRepository)
                        return com.example.pavamanconfiguratorgcs.ui.configurations.FrameTypeViewModel(frameTypeRepository) as T
                    }
                }
            )

            // Use fully-qualified composable reference
            com.example.pavamanconfiguratorgcs.ui.configurations.FrameTypeScreen(
                viewModel = frameTypeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                fcuDetected = fcuDetected
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

        composable(Screen.ServoOutput.route) {
            // Create ServoOutputViewModel with dependencies
            val servoOutputViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.ServoOutputViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.pavamanconfiguratorgcs.ui.configurations.ServoOutputViewModel(application.servoRepository) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.ServoOutputScreen(
                viewModel = servoOutputViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.SerialPorts.route) {
            // Create SerialPortsViewModel with dependencies
            val serialPortsViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.SerialPortsViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = telemetryRepository.getParameterRepository()
                        return com.example.pavamanconfiguratorgcs.ui.configurations.SerialPortsViewModel(
                            parameterRepository,
                            application.serialPortRepository
                        ) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.SerialPortsScreen(
                viewModel = serialPortsViewModel,
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

        composable(Screen.CompassCalibration.route) {
            // Create CompassCalibrationViewModel with dependencies
            val compassCalibrationViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.CompassCalibrationViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.pavamanconfiguratorgcs.ui.configurations.CompassCalibrationViewModel(telemetryRepository) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.CompassCalibrationScreen(
                viewModel = compassCalibrationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.RCCalibration.route) {
            // Create RC Calibration ViewModel with dependencies
            val rcCalibrationViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.RCCalibrationViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        val parameterRepository = telemetryRepository.getParameterRepository()
                        return com.example.pavamanconfiguratorgcs.ui.configurations.RCCalibrationViewModel(telemetryRepository, parameterRepository) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.RCCalibrationScreen(
                viewModel = rcCalibrationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.IMUCalibration.route) {
            // Create IMU Calibration ViewModel with dependencies
            val imuCalibrationViewModel: com.example.pavamanconfiguratorgcs.ui.configurations.IMUCalibrationViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.pavamanconfiguratorgcs.ui.configurations.IMUCalibrationViewModel(telemetryRepository) as T
                    }
                }
            )

            com.example.pavamanconfiguratorgcs.ui.configurations.IMUCalibrationScreen(
                viewModel = imuCalibrationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
