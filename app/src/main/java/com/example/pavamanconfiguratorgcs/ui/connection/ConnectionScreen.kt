package com.example.pavamanconfiguratorgcs.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    TCP, BLUETOOTH, USB
}

data class PairedDevice(
    val name: String,
    val address: String
)

data class UsbSerialDevice(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val deviceId: Int
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
        ConnectionType.USB -> {
            val device by viewModel.selectedUsbDevice.collectAsStateWithLifecycle()
            device != null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Connect to Drone",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            val tabs = listOf("TCP/IP", "Bluetooth", "USB")
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

            // Scrollable content area that takes remaining space
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (connectionType) {
                    ConnectionType.TCP -> TcpConnectionContent(viewModel)
                    ConnectionType.BLUETOOTH -> BluetoothConnectionContent(viewModel)
                    ConnectionType.USB -> UsbConnectionContent(viewModel, isConnecting) { startConnection() }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Connect and Cancel buttons - always visible at bottom
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

    // Load paired devices when this composable is displayed
    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pairedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No paired Bluetooth devices found.\nPlease pair a device in system settings.",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
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

        // Refresh devices button
        OutlinedButton(
            onClick = { viewModel.loadPairedDevices() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Devices", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbConnectionContent(viewModel: ConnectionViewModel, @Suppress("UNUSED_PARAMETER") isConnecting: Boolean, @Suppress("UNUSED_PARAMETER") onConnect: () -> Unit) {
    val usbDevices by viewModel.usbDevices.collectAsStateWithLifecycle()
    val selectedUsbDevice by viewModel.selectedUsbDevice.collectAsStateWithLifecycle()
    val baudRate by viewModel.baudRate.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // Common baud rates for serial communication
    val baudRates = listOf(9600, 57600, 115200, 230400, 460800, 921600)

    // Trigger device discovery when this composable is first displayed
    LaunchedEffect(Unit) {
        viewModel.discoverUsbDevices()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device list
        if (usbDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No compatible USB serial devices found.\nPlease ensure the device is connected via USB-OTG cable.",
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                items(usbDevices) { device ->
                    UsbDeviceRow(
                        device = device,
                        isSelected = device.deviceId == selectedUsbDevice?.deviceId,
                        onClick = { viewModel.onUsbDeviceSelected(device) }
                    )
                }
            }
        }

        // Baud rate dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = baudRate.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Baud Rate", color = Color.White) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                baudRates.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.toString()) },
                        onClick = {
                            viewModel.onBaudRateChange(rate)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Refresh devices button
        OutlinedButton(
            onClick = { viewModel.discoverUsbDevices() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Devices", color = Color.White)
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

@Composable
fun UsbDeviceRow(device: UsbSerialDevice, isSelected: Boolean, onClick: () -> Unit) {
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
            Text("VID: ${device.vendorId} PID: ${device.productId}", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
