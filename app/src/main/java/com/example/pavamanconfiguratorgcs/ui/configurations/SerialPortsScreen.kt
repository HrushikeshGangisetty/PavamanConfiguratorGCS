package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialPortsScreen(
    viewModel: SerialPortsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serialPorts by viewModel.serialPorts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showBitmaskDialog by viewModel.showBitmaskDialog.collectAsState()
    val showRebootDialog by viewModel.showRebootDialog.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Serial Ports",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF535350),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header
                Text(
                    text = "Serial Port Configuration",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Warning message
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFA726).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 20.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Changes will not take effect until the board is rebooted",
                            fontSize = 14.sp,
                            color = Color(0xFFFFA726),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Error message
                error?.let { errorMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMsg,
                                fontSize = 14.sp,
                                color = Color.Red,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss", color = Color.Red)
                            }
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Discovering serial ports...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else if (serialPorts.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No serial ports found",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.discoverPorts() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                } else {
                    // Serial ports list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(serialPorts) { port ->
                            SerialPortCard(
                                port = port,
                                onBaudChanged = { newBaud ->
                                    viewModel.changeBaudRate(port.portNumber, newBaud)
                                },
                                onProtocolChanged = { newProtocol ->
                                    viewModel.changeProtocol(port.portNumber, newProtocol)
                                },
                                onSetBitmask = {
                                    viewModel.showBitmaskEditor(port)
                                }
                            )
                        }
                    }
                }
            }

            // Feedback snackbar
            feedbackMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearFeedback() }) {
                            Text("OK", color = Color.White)
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }

        // Bitmask editor dialog
        showBitmaskDialog?.let { port ->
            BitmaskEditorDialog(
                port = port,
                onDismiss = { viewModel.hideBitmaskEditor() },
                onSave = { newBitmask ->
                    viewModel.changeOptions(port.portNumber, newBitmask)
                }
            )
        }

        // Reboot dialog
        if (showRebootDialog) {
            RebootConfirmationDialog(
                onDismiss = { viewModel.hideRebootDialog() },
                onConfirm = { viewModel.rebootVehicle() }
            )
        }
    }
}

@Composable
fun SerialPortCard(
    port: SerialPortConfig,
    onBaudChanged: (Int) -> Unit,
    onProtocolChanged: (Int) -> Unit,
    onSetBitmask: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4A4A48)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Port header
            Text(
                text = port.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Baud rate dropdown
            Text(
                text = "Baud Rate",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            BaudRateDropdown(
                currentBaud = port.currentBaud.toInt(),
                onBaudSelected = onBaudChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Protocol dropdown
            Text(
                text = "Protocol",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ProtocolDropdown(
                currentProtocol = port.currentProtocol.toInt(),
                onProtocolSelected = onProtocolChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Options bitmask button
            if (port.optionsParamName != null) {
                Button(
                    onClick = onSetBitmask,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Options Bitmask (${port.currentOptions?.toInt() ?: 0})")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaudRateDropdown(
    currentBaud: Int,
    onBaudSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = baudOptions.find { it.value == currentBaud }
        ?: DropdownOption(currentBaud, currentBaud.toString())

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentOption.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            baudOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onBaudSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolDropdown(
    currentProtocol: Int,
    onProtocolSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentOption = protocolOptions.find { it.value == currentProtocol }
        ?: DropdownOption(currentProtocol, "Unknown ($currentProtocol)")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentOption.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            protocolOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onProtocolSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun BitmaskEditorDialog(
    port: SerialPortConfig,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var selectedFlags by remember {
        mutableStateOf(
            commonSerialOptionFlags.associate { flag ->
                flag.bit to (port.currentOptions?.toInt()?.and(1 shl flag.bit) != 0)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Serial Port ${port.portNumber} Options")
        },
        text = {
            LazyColumn {
                items(commonSerialOptionFlags) { flag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedFlags[flag.bit] ?: false,
                            onCheckedChange = { checked ->
                                selectedFlags = selectedFlags + (flag.bit to checked)
                            }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = flag.label,
                                fontWeight = FontWeight.Medium
                            )
                            if (flag.description.isNotEmpty()) {
                                Text(
                                    text = flag.description,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bitmask = selectedFlags
                        .filter { it.value }
                        .keys
                        .sumOf { 1 shl it }
                    onSave(bitmask)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RebootConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reboot Required")
        },
        text = {
            Text("Serial port changes require a reboot to take effect. Please manually power-cycle the vehicle.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
