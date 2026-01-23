package com.example.pavamanconfiguratorgcs.ui.connection

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

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

    // Colors matching HomeScreen theme
    val backgroundColor = Color(0xFF0A0E21)
    val cardColor = Color(0xFF1C2541)
    val accentColor = Color(0xFF00D4AA)
    val waveColor = Color(0xFF1E3A5F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        ConnectionWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        ConnectionStarDecorations()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header matching HomeScreen style - compact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONNECT",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "TO YOUR DRONE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Row with updated styling - compact
            val tabs = listOf("TCP/IP", "Bluetooth", "USB")
            TabRow(
                selectedTabIndex = connectionType.ordinal,
                containerColor = cardColor,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = connectionType.ordinal == index,
                        onClick = { viewModel.onConnectionTypeChange(ConnectionType.entries[index]) },
                        modifier = Modifier.height(40.dp),
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                color = if (connectionType.ordinal == index) accentColor else Color.White.copy(alpha = 0.6f),
                                fontWeight = if (connectionType.ordinal == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable content area with card background - takes more space now
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (connectionType) {
                        ConnectionType.TCP -> TcpConnectionContent(viewModel)
                        ConnectionType.BLUETOOTH -> BluetoothConnectionContent(viewModel)
                        ConnectionType.USB -> UsbConnectionContent(viewModel, isConnecting) { startConnection() }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Connect and Cancel buttons with updated styling - compact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { startConnection() },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    enabled = isConnectEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black,
                        disabledContainerColor = accentColor.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isConnecting) "Connecting..." else "Connect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(
                    onClick = { cancelConnection() },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    enabled = isConnecting,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }
            }

            if (errorMessage.isNotEmpty() && !showPopup) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }

        if (showPopup) {
            AlertDialog(
                onDismissRequest = { showPopup = false },
                title = { Text("Connection Failed", color = Color.White) },
                text = { Text(errorMessage, color = Color.White.copy(alpha = 0.8f)) },
                confirmButton = {
                    Button(
                        onClick = { showPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("OK", color = Color.Black)
                    }
                },
                containerColor = cardColor
            )
        }
    }
}

/**
 * Wave background for connection screen
 */
@Composable
fun ConnectionWaveBackground(
    waveColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val waveHeight = height * 0.15f
        val baseY = height * 0.75f

        for (i in 0..5) {
            val path = Path()
            val offsetY = i * 12f
            val alpha = (0.3f - i * 0.04f).coerceAtLeast(0.05f)

            path.moveTo(0f, baseY + offsetY)

            var x = 0f
            while (x <= width) {
                val y = baseY + offsetY + sin((x / width * 4 + animatedOffset / 60f + i * 0.5f).toDouble()).toFloat() * waveHeight * 0.3f
                path.lineTo(x, y)
                x += 5f
            }

            drawPath(
                path = path,
                color = waveColor.copy(alpha = alpha),
                style = Stroke(width = 2f)
            )
        }
    }
}

/**
 * Star decorations for connection screen
 */
@Composable
fun ConnectionStarDecorations() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )

    val starPositions = listOf(
        Offset(0.1f, 0.2f),
        Offset(0.85f, 0.15f),
        Offset(0.15f, 0.85f),
        Offset(0.9f, 0.8f),
        Offset(0.5f, 0.1f)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val starColor = Color.White

        starPositions.forEachIndexed { index, pos ->
            val x = size.width * pos.x
            val y = size.height * pos.y
            val alpha = if (index % 2 == 0) starAlpha else 1.3f - starAlpha

            // Draw 4-point star
            val starSize = 6f
            val path = Path().apply {
                moveTo(x, y - starSize)
                lineTo(x, y + starSize)
                moveTo(x - starSize, y)
                lineTo(x + starSize, y)
                val smallSize = starSize * 0.5f
                moveTo(x - smallSize, y - smallSize)
                lineTo(x + smallSize, y + smallSize)
                moveTo(x + smallSize, y - smallSize)
                lineTo(x - smallSize, y + smallSize)
            }

            drawPath(
                path = path,
                color = starColor.copy(alpha = alpha.coerceIn(0.2f, 1f)),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

@Composable
fun TcpConnectionContent(viewModel: ConnectionViewModel) {
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val accentColor = Color(0xFF00D4AA)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Enter Connection Details",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.8f)
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { viewModel.onIpAddressChange(it) },
            label = { Text("IP Address", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("192.168.1.1", color = Color.White.copy(alpha = 0.3f)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = accentColor
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = port,
            onValueChange = { viewModel.onPortChange(it) },
            label = { Text("Port", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("14550", color = Color.White.copy(alpha = 0.3f)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = accentColor
            ),
            singleLine = true
        )

        // Helper text
        Text(
            text = "Default MAVLink port: 14550",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun BluetoothConnectionContent(viewModel: ConnectionViewModel) {
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val accentColor = Color(0xFF00D4AA)
    val cardColor = Color(0xFF1C2541)

    // Load paired devices when this composable is displayed
    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Paired Device",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.8f)
        )

        if (pairedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No paired Bluetooth devices found.\nPlease pair a device in system settings.",
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 180.dp)
            ) {
                items(pairedDevices) { device ->
                    DeviceRow(
                        device = device,
                        isSelected = device.address == selectedDevice?.address,
                        onClick = { viewModel.onDeviceSelected(device) },
                        accentColor = accentColor,
                        cardColor = cardColor
                    )
                }
            }
        }

        // Refresh devices button
        OutlinedButton(
            onClick = { viewModel.loadPairedDevices() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
        ) {
            Text("Refresh Devices", fontSize = 14.sp)
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
    val accentColor = Color(0xFF00D4AA)
    val cardColor = Color(0xFF1C2541)

    // Common baud rates for serial communication
    val baudRates = listOf(9600, 57600, 115200, 230400, 460800, 921600)

    // Trigger device discovery when this composable is first displayed
    LaunchedEffect(Unit) {
        viewModel.discoverUsbDevices()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select USB Device",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.8f)
        )

        // Device list
        if (usbDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No compatible USB serial devices found.\nPlease connect via USB-OTG cable.",
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                items(usbDevices) { device ->
                    UsbDeviceRow(
                        device = device,
                        isSelected = device.deviceId == selectedUsbDevice?.deviceId,
                        onClick = { viewModel.onUsbDeviceSelected(device) },
                        accentColor = accentColor,
                        cardColor = cardColor
                    )
                }
            }
        }

        // Baud rate dropdown with larger size
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = baudRate.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Baud Rate", color = Color.White.copy(alpha = 0.7f)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = cardColor
            ) {
                baudRates.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.toString(), color = Color.White, fontSize = 15.sp) },
                        onClick = {
                            viewModel.onBaudRateChange(rate)
                            expanded = false
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
        }

        // Refresh devices button
        OutlinedButton(
            onClick = { viewModel.discoverUsbDevices() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
        ) {
            Text("Refresh Devices", fontSize = 14.sp)
        }
    }
}

@Composable
fun DeviceRow(
    device: PairedDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = Color(0xFF00D4AA),
    cardColor: Color = Color(0xFF1C2541)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else cardColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = if (isSelected) accentColor else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Text(
                    device.address,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Text("✓", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun UsbDeviceRow(
    device: UsbSerialDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = Color(0xFF00D4AA),
    cardColor: Color = Color(0xFF1C2541)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else cardColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    color = if (isSelected) accentColor else Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Text(
                    "VID: ${device.vendorId} PID: ${device.productId}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Text("✓", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
