package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorTestScreen(
    viewModel: MotorTestViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Motor Test",
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A38))
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not connected to autopilot",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A38))
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                item {
                    Text(
                        text = "Motor Test",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Frame Information
                item {
                    FrameInfoCard(
                        frameClass = uiState.frameClassName,
                        frameType = uiState.frameTypeName
                    )
                }

                // Control Panel
                item {
                    ControlPanelCard(
                        throttlePercent = uiState.throttlePercent,
                        duration = uiState.duration,
                        onThrottleChange = { viewModel.updateThrottlePercent(it) },
                        onDurationChange = { viewModel.updateDuration(it) },
                        isTestingAll = uiState.isTestingAll
                    )
                }

                // Motor Test Buttons
                item {
                    Text(
                        text = "Individual Motors",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.motors) { motor ->
                    MotorTestButton(
                        motor = motor,
                        isTesting = uiState.isTestingMotor == motor.number,
                        enabled = !uiState.isTestingAll && uiState.isTestingMotor == null,
                        onClick = { viewModel.testMotor(motor.number) }
                    )
                }

                // Bulk Actions
                item {
                    Text(
                        text = "Bulk Actions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testAllMotors() },
                            enabled = !uiState.isTestingAll && uiState.isTestingMotor == null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test All Motors", color = Color.White)
                        }

                        Button(
                            onClick = { viewModel.testAllInSequence() },
                            enabled = !uiState.isTestingAll && uiState.isTestingMotor == null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test in Sequence", color = Color.White)
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.stopAllMotors() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("STOP ALL MOTORS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Spin Parameters
                item {
                    Text(
                        text = "Spin Parameters",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setSpinArm() },
                            enabled = uiState.throttlePercent < 20f && !uiState.isTestingAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9C27B0)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Set Arm Throttle", color = Color.White, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.setSpinMin() },
                            enabled = uiState.throttlePercent < 20f && !uiState.isTestingAll,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Set Min Throttle", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                // Safety Warning
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5722).copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "⚠️ SAFETY WARNING",
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Remove all propellers before testing\n" +
                                        "• Ensure aircraft is secured\n" +
                                        "• Keep safe distance from motors\n" +
                                        "• Be ready to stop motors immediately",
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Loading indicator
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrameInfoCard(
    frameClass: String,
    frameType: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Frame Configuration",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frame Class:",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = frameClass,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frame Type:",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = frameType,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ControlPanelCard(
    throttlePercent: Float,
    duration: Float,
    onThrottleChange: (Float) -> Unit,
    onDurationChange: (Float) -> Unit,
    isTestingAll: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Test Parameters",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Throttle Percent
            Text(
                text = "Throttle: ${throttlePercent.toInt()}%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = throttlePercent,
                onValueChange = onThrottleChange,
                valueRange = -100f..100f,
                enabled = !isTestingAll,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4CAF50),
                    activeTrackColor = Color(0xFF4CAF50)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Duration
            Text(
                text = "Duration: ${"%.1f".format(duration)}s",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = duration,
                onValueChange = onDurationChange,
                valueRange = 0f..10f,
                enabled = !isTestingAll,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2196F3),
                    activeTrackColor = Color(0xFF2196F3)
                )
            )
        }
    }
}

@Composable
fun MotorTestButton(
    motor: MotorInfo,
    isTesting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isTesting,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTesting) Color(0xFF4CAF50) else Color(0xFF616161),
            disabledContainerColor = Color(0xFF424242)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = motor.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Motor ${motor.number}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Text(
                    text = motor.rotation,
                    color = if (motor.rotation == "CW") Color(0xFFFF9800) else Color(0xFF2196F3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
