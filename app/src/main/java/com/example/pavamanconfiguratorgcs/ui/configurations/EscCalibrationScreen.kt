package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun EscCalibrationScreen(
    viewModel: EscCalibrationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // Show error dialog
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(uiState.errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ESC Calibration",
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Connection status
            if (!isConnected) {
                ConnectionWarningCard()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title
            Text(
                text = "ESC Calibration Configuration",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Description
            Text(
                text = "Configure Electronic Speed Controller (ESC) parameters and perform calibration. " +
                       "Ensure propellers are removed before calibration.",
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Motor PWM Type Section
            MotorPwmTypeSection(
                currentType = uiState.motPwmType,
                onTypeChange = { viewModel.updateMotPwmType(it) },
                enabled = !uiState.isCalibrating && isConnected
            )

            Spacer(modifier = Modifier.height(20.dp))

            // PWM Range Section
            PwmRangeSection(
                pwmMin = uiState.motPwmMin,
                pwmMax = uiState.motPwmMax,
                onPwmMinChange = { viewModel.updateMotPwmMin(it) },
                onPwmMaxChange = { viewModel.updateMotPwmMax(it) },
                enabled = !uiState.isCalibrating && isConnected
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Spin Throttle Section
            SpinThrottleSection(
                spinArm = uiState.motSpinArm,
                spinMin = uiState.motSpinMin,
                spinMax = uiState.motSpinMax,
                onSpinArmChange = { viewModel.updateMotSpinArm(it) },
                onSpinMinChange = { viewModel.updateMotSpinMin(it) },
                onSpinMaxChange = { viewModel.updateMotSpinMax(it) },
                enabled = !uiState.isCalibrating && isConnected
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Save Parameters Button
                Button(
                    onClick = { viewModel.saveParameters() },
                    enabled = !uiState.isSaving && !uiState.isCalibrating && isConnected,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
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
                    Text("Save Parameters")
                }

                // Reload Button
                Button(
                    onClick = { viewModel.loadParameters() },
                    enabled = !uiState.isLoading && !uiState.isCalibrating && isConnected,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Reload")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Calibration Section
            CalibrationSection(
                isCalibrating = uiState.isCalibrating,
                calibrationStep = uiState.calibrationStep,
                calibrationMessage = uiState.calibrationMessage,
                onStartCalibration = { viewModel.startCalibration() },
                onStopCalibration = { viewModel.stopCalibration() },
                enabled = isConnected
            )
        }
    }
}

@Composable
fun ConnectionWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF6B6B)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = "Not connected to drone. Please establish connection first.",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorPwmTypeSection(
    currentType: Int,
    onTypeChange: (Int) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val pwmTypes = PwmType.entries
    val selectedType = PwmType.fromValue(currentType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Motor PWM Type",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text("PWM Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.Gray,
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    pwmTypes.forEach { pwmType ->
                        DropdownMenuItem(
                            text = { Text(pwmType.displayName) },
                            onClick = {
                                onTypeChange(pwmType.value)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = "Select the PWM signal type for your ESCs",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun PwmRangeSection(
    pwmMin: Int,
    pwmMax: Int,
    onPwmMinChange: (Int) -> Unit,
    onPwmMaxChange: (Int) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "PWM Range (µs)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // PWM Min
            ParameterSlider(
                label = "Minimum PWM",
                value = pwmMin,
                onValueChange = { onPwmMinChange(it.toInt()) },
                valueRange = 0f..1500f,
                steps = 149,
                enabled = enabled,
                unit = "µs"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PWM Max
            ParameterSlider(
                label = "Maximum PWM",
                value = pwmMax,
                onValueChange = { onPwmMaxChange(it.toInt()) },
                valueRange = 1500f..2200f,
                steps = 69,
                enabled = enabled,
                unit = "µs"
            )

            Text(
                text = "Typical range: Min=1000µs, Max=2000µs",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SpinThrottleSection(
    spinArm: Float,
    spinMin: Float,
    spinMax: Float,
    onSpinArmChange: (Float) -> Unit,
    onSpinMinChange: (Float) -> Unit,
    onSpinMaxChange: (Float) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Spin Throttle Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Spin Arm
            ParameterSlider(
                label = "Spin Armed",
                value = (spinArm * 100).toInt(),
                onValueChange = { onSpinArmChange(it / 100f) },
                valueRange = 0f..100f,
                steps = 99,
                enabled = enabled,
                unit = "%"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Spin Min
            ParameterSlider(
                label = "Spin Minimum",
                value = (spinMin * 100).toInt(),
                onValueChange = { onSpinMinChange(it / 100f) },
                valueRange = 0f..100f,
                steps = 99,
                enabled = enabled,
                unit = "%"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Spin Max
            ParameterSlider(
                label = "Spin Maximum",
                value = (spinMax * 100).toInt(),
                onValueChange = { onSpinMaxChange(it / 100f) },
                valueRange = 0f..100f,
                steps = 99,
                enabled = enabled,
                unit = "%"
            )

            Text(
                text = "Throttle percentage when armed (Arm), minimum in flight (Min), and maximum (Max)",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ParameterSlider(
    label: String,
    value: Int,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    unit: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$value $unit",
                fontSize = 14.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4CAF50),
                activeTrackColor = Color(0xFF4CAF50),
                inactiveTrackColor = Color(0xFF555555)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CalibrationSection(
    isCalibrating: Boolean,
    calibrationStep: CalibrationStep,
    calibrationMessage: String?,
    onStartCalibration: () -> Unit,
    onStopCalibration: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "ESC Calibration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Warning message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF9800)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "⚠ IMPORTANT SAFETY WARNINGS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Remove all propellers before calibration\n" +
                               "• Secure the vehicle safely\n" +
                               "• Motors will spin during calibration\n" +
                               "• Disconnect battery after completion\n" +
                               "• Requires ArduCopter 3.3+",
                        fontSize = 12.sp,
                        color = Color.Black,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calibration status
            if (calibrationMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCalibrating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = calibrationMessage,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Calibration button
            Button(
                onClick = {
                    if (isCalibrating) {
                        onStopCalibration()
                    } else {
                        onStartCalibration()
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCalibrating) Color(0xFFFF5252) else Color(0xFF4CAF50),
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isCalibrating) "STOP CALIBRATION" else "START CALIBRATION",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Text(
                text = "Click to begin full ESC calibration sequence",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
