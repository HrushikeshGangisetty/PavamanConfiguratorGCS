package com.example.pavamanconfiguratorgcs.ui.connection

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ConnectionType {
    TCP, BLUETOOTH
}

data class PairedDevice(
    val name: String,
    val address: String
)

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onConnectionSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionType by viewModel.connectionType.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showPopup by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var connectionJob by remember { mutableStateOf<Job?>(null) }

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

    // Load paired Bluetooth devices when page is shown
    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }

    fun startConnection() {
        isConnecting = true
        errorMessage = ""
        connectionJob?.cancel()
        connectionJob = coroutineScope.launch {
            viewModel.connect()

            // Set a timeout for the connection attempt
            delay(10000) // 10-second timeout

            // If we are still in a 'connecting' state after the timeout, it failed.
            if (isConnecting && connectionState !is TelemetryRepository.ConnectionState.HeartbeatVerified) {
                isConnecting = false
                errorMessage = "Connection timed out. Please check your settings and try again."
                showPopup = true
                viewModel.cancelConnection()
            }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        isConnecting = false
        errorMessage = ""
        coroutineScope.launch {
            viewModel.cancelConnection()
        }
    }

    val isConnectEnabled = !isConnecting && when (connectionType) {
        ConnectionType.TCP -> {
            val ip by viewModel.ipAddress.collectAsStateWithLifecycle()
            val port by viewModel.port.collectAsStateWithLifecycle()
            ip.isNotBlank() && port.isNotBlank()
        }
        ConnectionType.BLUETOOTH -> {
            val device by viewModel.selectedDevice.collectAsStateWithLifecycle()
            device != null
        }
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

            Spacer(modifier = Modifier.height(10.dp))

            val tabs = listOf("TCP/IP", "Bluetooth")
            TabRow(
                selectedTabIndex = connectionType.ordinal,
                containerColor = Color(0xFF333330)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = connectionType.ordinal == index,
                        onClick = { viewModel.onConnectionTypeChange(ConnectionType.entries[index]) },
                        text = { Text(title, color = Color.White) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (connectionType) {
                ConnectionType.TCP -> TcpConnectionContent(viewModel)
                ConnectionType.BLUETOOTH -> BluetoothConnectionContent(viewModel)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { startConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = isConnectEnabled
                ) {
                    Text(if (isConnecting) "Connecting..." else "Connect")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { cancelConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = isConnecting
                ) {
                    Text("Cancel")
                }
            }

            if (errorMessage.isNotEmpty() && !showPopup) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
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
                }
            )
        }
    }
}

@Composable
fun TcpConnectionContent(viewModel: ConnectionViewModel) {
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = ipAddress,
        onValueChange = { viewModel.onIpAddressChange(it) },
        label = { Text("IP Address", color = Color.White) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.Gray
        )
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = port,
        onValueChange = { viewModel.onPortChange(it) },
        label = { Text("Port", color = Color.White) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.Gray
        )
    )
}

@Composable
fun BluetoothConnectionContent(viewModel: ConnectionViewModel) {
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

    if (pairedDevices.isEmpty()) {
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
                .height(120.dp)
        ) {
            items(pairedDevices) { device ->
                DeviceRow(
                    device = device,
                    isSelected = device.address == selectedDevice?.address,
                    onClick = { viewModel.onDeviceSelected(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceRow(device: PairedDevice, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(device.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(device.address, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
