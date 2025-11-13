package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Battery Monitor Setup Screen with local caching and parameter management.
 * All changes are cached locally until "Upload Parameters" is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryMonitorScreen(
    viewModel: BatteryMonitorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configState by viewModel.configState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Show snackbar messages
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearMessages()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Monitor Setup", fontWeight = FontWeight.Bold) },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Battery Monitor Setup",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Configure battery monitoring hardware. Changes are cached locally until you click 'Upload Parameters'.",
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp
            )

            if (!isConnected) {
                BatteryConnectionWarningCard()
            }

            // Configuration Section
            BatteryConfigurationCard(
                configState = configState,
                isConnected = isConnected,
                onMonitorChange = viewModel::updateBattMonitor,
                onSensorChange = viewModel::updateSensorSelection,
                onHwVersionChange = viewModel::updateHwVersion,
                onCapacityChange = viewModel::updateBattCapacity
            )

            // Upload Button
            Button(
                onClick = { viewModel.uploadParameters() },
                enabled = isConnected && !configState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                )
            ) {
                if (configState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Upload Parameters to Flight Controller", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Info Section
            InfoCard(configState)
        }
    }
}

@Composable
private fun BatteryConnectionWarningCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚠️", fontSize = 24.sp)
            Text(
                "Not connected to Flight Controller. Please connect first.",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BatteryConfigurationCard(
    configState: BatteryMonitorViewModel.BatteryConfigState,
    isConnected: Boolean,
    onMonitorChange: (Int) -> Unit,
    onSensorChange: (String) -> Unit,
    onHwVersionChange: (Int) -> Unit,
    onCapacityChange: (Float) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Hardware Configuration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Monitor Dropdown
            MonitorDropdown(
                selectedValue = configState.battMonitor,
                enabled = isConnected,
                onValueChange = onMonitorChange
            )

            // Sensor Dropdown
            SensorDropdown(
                selectedValue = configState.sensorSelection,
                enabled = isConnected,
                onValueChange = onSensorChange
            )

            // HW Version Dropdown
            HwVersionDropdown(
                selectedValue = configState.hwVersion,
                enabled = isConnected,
                onValueChange = onHwVersionChange
            )

            // Battery Capacity Input
            BatteryCapacityInput(
                value = configState.battCapacity,
                enabled = isConnected,
                onValueChange = onCapacityChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorDropdown(
    selectedValue: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val monitorOptions = listOf(
        0 to "Disabled",
        3 to "Analog Voltage Only",
        4 to "Analog Voltage and Current",
        5 to "SMBus-Generic",
        6 to "Bebop",
        7 to "SMBus-Maxell",
        8 to "UAVCAN-BatteryInfo",
        9 to "BLHeli ESC",
        10 to "Sum of Selected Monitors"
    )

    Column {
        Text("Monitor (BATT_MONITOR)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = monitorOptions.find { it.first == selectedValue }?.second ?: "Unknown",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                monitorOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.second} (${option.first})") },
                        onClick = {
                            onValueChange(option.first)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorDropdown(
    selectedValue: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val sensorOptions = listOf(
        "APM2.5",
        "Pixhawk",
        "Pixhawk2",
        "3DR Power Module",
        "Other"
    )

    Column {
        Text("Sensor", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sensorOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HwVersionDropdown(
    selectedValue: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val hwVersionOptions = listOf(
        0 to "APM 1",
        1 to "APM 2",
        2 to "APM 2.5 / 2.5+",
        3 to "Pixhawk",
        4 to "Pixhawk2",
        5 to "3DR Power Module",
        6 to "Pixracer",
        7 to "Other/Custom"
    )

    Column {
        Text("HW Ver (Hardware Version)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = hwVersionOptions.find { it.first == selectedValue }?.second ?: "Unknown",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                hwVersionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.second) },
                        onClick = {
                            onValueChange(option.first)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryCapacityInput(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(if (value > 0f) value.toString() else "") }

    Column {
        Text("Battery Capacity (BATT_CAPACITY)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                newValue.toFloatOrNull()?.let { onValueChange(it) }
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text("mAh", color = Color.Gray) },
            placeholder = { Text("e.g., 5000", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.Gray
            )
        )
    }
}

@Composable
private fun InfoCard(configState: BatteryMonitorViewModel.BatteryConfigState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Cached Parameter Values",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "These values are stored locally and will be written to the FC when you click 'Upload Parameters'.",
                fontSize = 12.sp,
                color = Color(0xFFBBBBBB)
            )

            HorizontalDivider(color = Color(0xFF555555), modifier = Modifier.padding(vertical = 8.dp))

            InfoRow("BATT_MONITOR", configState.battMonitor.toString())
            InfoRow("BATT_CAPACITY", "${configState.battCapacity} mAh")
            InfoRow("BATT_VOLT_PIN", configState.voltPin.toString())
            InfoRow("BATT_CURR_PIN", configState.currPin.toString())
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFCCCCCC), fontSize = 14.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
