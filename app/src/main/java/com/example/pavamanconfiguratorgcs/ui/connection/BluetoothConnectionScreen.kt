package com.example.pavamanconfiguratorgcs.ui.connection

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConnectionScreen(
    onConnectionSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryRepository = remember { TelemetryRepository() }
    val viewModel = remember { BluetoothConnectionViewModel(context, telemetryRepository) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val heartbeatReceived by viewModel.droneHeartbeatReceived.collectAsStateWithLifecycle()

    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showPopup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var connectionJob by remember { mutableStateOf<Job?>(null) }

    // Request Bluetooth permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.loadPairedDevices()
        }
    }

    // Navigate to home when connection is verified
    LaunchedEffect(connectionState) {
        if (connectionState is TelemetryRepository.ConnectionState.HeartbeatVerified) {
            isConnecting = false
            connectionJob?.cancel()
            onConnectionSuccess()
        }
    }

    // Monitor for connection errors
    LaunchedEffect(connectionState) {
        if (connectionState is TelemetryRepository.ConnectionState.Error) {
            isConnecting = false
            connectionJob?.cancel()
            errorMessage = (connectionState as TelemetryRepository.ConnectionState.Error).message
            showPopup = true
        }
    }

    fun startConnection(device: BluetoothConnectionViewModel.BluetoothDeviceInfo) {
        isConnecting = true
        errorMessage = ""
        connectionJob?.cancel()
        connectionJob = coroutineScope.launch {
            viewModel.connectToDevice(device)

            // Set a timeout for the connection attempt
            delay(10000) // 10-second timeout

            // If we are still connecting after timeout, it failed
            if (isConnecting && connectionState !is TelemetryRepository.ConnectionState.HeartbeatVerified) {
                isConnecting = false
                errorMessage = "Connection timed out. Please check your settings and try again."
                showPopup = true
            }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        isConnecting = false
        errorMessage = ""
        // Note: disconnect functionality can be added to viewModel if needed
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Connect to Drone",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (uiState) {
                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Initial,
                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White
                    )
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.BluetoothNotSupported -> {
                    ErrorMessageInline(
                        message = "Bluetooth is not supported on this device"
                    )
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.BluetoothDisabled -> {
                    ErrorMessageInline(
                        message = "Please enable Bluetooth to continue"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry")
                    }
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.PermissionDenied -> {
                    ErrorMessageInline(
                        message = "Bluetooth permissions are required"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            } else {
                                arrayOf(Manifest.permission.BLUETOOTH)
                            }
                            permissionLauncher.launch(permissions)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Grant Permissions")
                    }
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Ready -> {
                    BluetoothDeviceList(
                        devices = pairedDevices,
                        selectedDevice = null,
                        onDeviceClick = { device ->
                            startConnection(device)
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            // Connect button - device must be selected from list
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // Disabled since we connect on device click
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Select a device above to connect")
                    }
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Connecting -> {
                    ConnectingStateInline(connectionState = connectionState)

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { cancelConnection() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB00020)
                        )
                    ) {
                        Text("Cancel")
                    }
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Connected -> {
                    ConnectedStateInline(
                        heartbeatReceived = heartbeatReceived,
                        connectionState = connectionState
                    )
                }

                is BluetoothConnectionViewModel.BluetoothConnectionUiState.Error -> {
                    ErrorMessageInline(
                        message = (uiState as BluetoothConnectionViewModel.BluetoothConnectionUiState.Error).message
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retry() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }

            if (errorMessage.isNotEmpty() && !showPopup) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = Color(0xFFFF6B6B))
            }
        }

        if (showPopup) {
            AlertDialog(
                onDismissRequest = { showPopup = false },
                title = { Text("Connection Failed") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = { showPopup = false }) {
                        Text("OK")
                    }
                },
                containerColor = Color(0xFF333330)
            )
        }
    }
}

@Composable
private fun BluetoothDeviceList(
    devices: List<BluetoothConnectionViewModel.BluetoothDeviceInfo>,
    selectedDevice: BluetoothConnectionViewModel.BluetoothDeviceInfo?,
    onDeviceClick: (BluetoothConnectionViewModel.BluetoothDeviceInfo) -> Unit
) {
    if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No paired Bluetooth devices found.", color = Color.White)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            items(devices) { device ->
                DeviceRow(
                    device = device,
                    isSelected = device.address == selectedDevice?.address,
                    onClick = { onDeviceClick(device) }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: BluetoothConnectionViewModel.BluetoothDeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                device.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                device.address,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ConnectingStateInline(connectionState: TelemetryRepository.ConnectionState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(24.dp))

        val statusText = when (connectionState) {
            is TelemetryRepository.ConnectionState.Connecting -> "Connecting to device..."
            is TelemetryRepository.ConnectionState.Connected -> "Connection established"
            is TelemetryRepository.ConnectionState.VerifyingHeartbeat -> "Verifying drone heartbeat..."
            is TelemetryRepository.ConnectionState.HeartbeatVerified -> "Heartbeat verified!"
            else -> "Connecting..."
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (connectionState is TelemetryRepository.ConnectionState.VerifyingHeartbeat) {
            Text(
                text = "Waiting for drone response...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ConnectedStateInline(
    heartbeatReceived: Boolean,
    connectionState: TelemetryRepository.ConnectionState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (heartbeatReceived) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connected Successfully!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Drone heartbeat verified",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = "Verifying connection...",
                modifier = Modifier.padding(top = 16.dp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun ErrorMessageInline(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFB00020).copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
