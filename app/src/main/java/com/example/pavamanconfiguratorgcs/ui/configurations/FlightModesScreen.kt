package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.FlightMode
import com.example.pavamanconfiguratorgcs.data.models.FlightModeConfiguration
import com.example.pavamanconfiguratorgcs.data.models.FlightModeSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightModesScreen(
    viewModel: FlightModesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // Show snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Flight Modes",
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
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2A2A28),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connection status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "Connected" else "Disconnected",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Save button
                    Button(
                        onClick = { viewModel.saveFlightModes() },
                        enabled = isConnected && uiState.hasUnsavedChanges && !uiState.isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF555555)
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (uiState.isSaving) "Saving..." else "SAVE MODES",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A38))
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading flight modes...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A38))
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Text(
                        text = "Flight Mode Configuration",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Current mode indicator
                item {
                    CurrentModeCard(
                        configuration = uiState.configuration,
                        availableModes = uiState.availableModes
                    )
                }

                // Flight mode slots
                itemsIndexed(uiState.configuration.slots) { index, slot ->
                    FlightModeSlotCard(
                        slotNumber = index + 1,
                        slot = slot,
                        availableModes = uiState.availableModes,
                        showSimpleModes = uiState.showSimpleModes,
                        isCurrentMode = index == uiState.configuration.currentModeIndex,
                        onModeSelected = { modeKey ->
                            viewModel.updateFlightMode(index, modeKey)
                        },
                        onSimpleToggled = { enabled ->
                            viewModel.updateSimpleMode(index, enabled)
                        },
                        onSuperSimpleToggled = { enabled ->
                            viewModel.updateSuperSimpleMode(index, enabled)
                        }
                    )
                }

                // Info section
                if (uiState.showSimpleModes) {
                    item {
                        SimpleModeInfoCard()
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentModeCard(
    configuration: FlightModeConfiguration,
    availableModes: List<FlightMode>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A28)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Current Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Active Mode:",
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    val currentSlot = configuration.slots.getOrNull(configuration.currentModeIndex)
                    val currentMode = availableModes.find { it.key == currentSlot?.mode }
                    Text(
                        text = currentMode?.value ?: "Unknown",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Switch PWM:",
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Text(
                        text = "CH${configuration.switchChannel}: ${configuration.switchPwm}µs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun FlightModeSlotCard(
    slotNumber: Int,
    slot: FlightModeSlot,
    availableModes: List<FlightMode>,
    showSimpleModes: Boolean,
    isCurrentMode: Boolean,
    onModeSelected: (Int) -> Unit,
    onSimpleToggled: (Boolean) -> Unit,
    onSuperSimpleToggled: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMode = availableModes.find { it.key == slot.mode }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentMode) Color(0xFF4A4A48) else Color.Black
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentMode) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slot label
                Text(
                    text = "FM$slotNumber:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentMode) Color(0xFF4CAF50) else Color.White,
                    modifier = Modifier.width(60.dp)
                )

                // Mode dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF2A2A28)
                        )
                    ) {
                        Text(
                            text = selectedMode?.value ?: "Select Mode",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = "▼", color = Color.White)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .background(Color(0xFF2A2A28))
                    ) {
                        availableModes.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.value,
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    onModeSelected(mode.key)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Simple mode checkboxes (ArduCopter only)
            if (showSimpleModes) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = slot.simpleEnabled,
                            onCheckedChange = onSimpleToggled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4CAF50),
                                uncheckedColor = Color(0xFFAAAAAA)
                            )
                        )
                        Text(
                            text = "Simple",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = slot.superSimpleEnabled,
                            onCheckedChange = onSuperSimpleToggled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4CAF50),
                                uncheckedColor = Color(0xFFAAAAAA)
                            )
                        )
                        Text(
                            text = "Super Simple",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleModeInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A28)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "ℹ️ Simple Mode Info",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "• Simple Mode: Control inputs are always relative to heading at arming\n" +
                       "• Super Simple: Control inputs are always relative to home position",
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC),
                lineHeight = 20.sp
            )
        }
    }
}
