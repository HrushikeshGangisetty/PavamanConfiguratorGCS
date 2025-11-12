package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IMUCalibrationScreen(
    viewModel: IMUCalibrationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show cancel confirmation dialog
    if (uiState.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog(false) },
            title = { Text("Cancel Calibration?") },
            text = { Text("Are you sure you want to cancel the calibration process?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelCalibration()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog(false) }) {
                    Text("Continue Calibration")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
    ) {
        // Header - Fixed at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF535350))
                .padding(16.dp)
        ) {
            CalibrationHeader(
                onBackClick = {
                    if (uiState.calibrationState is IMUCalibrationState.Idle ||
                        uiState.calibrationState is IMUCalibrationState.Success ||
                        uiState.calibrationState is IMUCalibrationState.Failed ||
                        uiState.calibrationState is IMUCalibrationState.Cancelled
                    ) {
                        onNavigateBack()
                    } else {
                        viewModel.showCancelDialog(true)
                    }
                }
            )
        }

        // Progress indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CalibrationProgress(
                currentPosition = uiState.currentPositionIndex,
                totalPositions = uiState.totalPositions,
                calibrationState = uiState.calibrationState
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content area - Flexible height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            CalibrationContent(
                calibrationState = uiState.calibrationState,
                statusText = uiState.statusText,
                isConnected = uiState.isConnected
            )
        }

        // Action buttons - Fixed at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF535350))
                .padding(16.dp)
        ) {
            CalibrationActions(
                calibrationState = uiState.calibrationState,
                isConnected = uiState.isConnected,
                buttonText = uiState.buttonText,
                onButtonClick = { viewModel.onButtonClick() },
                onCancel = { viewModel.showCancelDialog(true) },
                onReset = { viewModel.resetCalibration() }
            )
        }
    }
}

@Composable
private fun CalibrationHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "IMU Calibration",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CalibrationProgress(
    currentPosition: Int,
    totalPositions: Int,
    calibrationState: IMUCalibrationState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Progress text
        if (calibrationState !is IMUCalibrationState.Idle &&
            calibrationState !is IMUCalibrationState.Success &&
            calibrationState !is IMUCalibrationState.Failed &&
            calibrationState !is IMUCalibrationState.Cancelled
        ) {
            Text(
                text = "Position ${currentPosition + 1} of $totalPositions",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Progress bar
        LinearProgressIndicator(
            progress = if (totalPositions > 0) currentPosition.toFloat() / totalPositions.toFloat() else 0f,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp),
            color = when (calibrationState) {
                is IMUCalibrationState.Success -> Color.Green
                is IMUCalibrationState.Failed -> Color.Red
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = Color.Gray.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun CalibrationContent(
    calibrationState: IMUCalibrationState,
    statusText: String,
    isConnected: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A38)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (calibrationState) {
                    is IMUCalibrationState.Idle -> IdleContent(isConnected)
                    is IMUCalibrationState.Initiating -> InitiatingContent()
                    is IMUCalibrationState.AwaitingUserInput -> PositionContent(calibrationState.position)
                    is IMUCalibrationState.ProcessingPosition -> ProcessingContent(calibrationState.position)
                    is IMUCalibrationState.Success -> SuccessContent(calibrationState.message)
                    is IMUCalibrationState.Failed -> FailedContent(calibrationState.errorMessage)
                    is IMUCalibrationState.Cancelled -> CancelledContent()
                }
            }
        }

        // Status text at bottom of card
        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun IdleContent(isConnected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Connection status indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) Color(0xFF1B5E20) else Color(0xFF5D4037)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "✓ Connected" else "⚠ Not Connected",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "IMU Calibration",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isConnected) {
                "Ready to calibrate"
            } else {
                "Please connect to drone first"
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Info card - Compact version
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A28)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "📋 6 Positions Required:",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                listOf(
                    "1. Level",
                    "2. Left side",
                    "3. Right side",
                    "4. Nose down",
                    "5. Nose up",
                    "6. On back"
                ).forEach { step ->
                    Text(
                        text = step,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InitiatingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Initiating Calibration...",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PositionContent(position: AccelCalibrationPosition) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Drone orientation icon/visual
        DroneOrientationIcon(position)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = position.name.replace("_", " "),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = position.instruction,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProcessingContent(position: AccelCalibrationPosition) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Processing ${position.name} position...",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SuccessContent(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.Green,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Success!",
            color = Color.Green,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FailedContent(errorMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Failed",
            color = Color.Red,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CancelledContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Cancelled",
            color = Color.Gray,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DroneOrientationIcon(position: AccelCalibrationPosition) {
    // Simple visual representation using a box and arrow
    Box(
        modifier = Modifier
            .size(120.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (position) {
                AccelCalibrationPosition.LEVEL -> Icons.Default.CheckCircle
                AccelCalibrationPosition.LEFT -> Icons.Default.KeyboardArrowLeft
                AccelCalibrationPosition.RIGHT -> Icons.Default.KeyboardArrowRight
                AccelCalibrationPosition.NOSEDOWN -> Icons.Default.KeyboardArrowDown
                AccelCalibrationPosition.NOSEUP -> Icons.Default.KeyboardArrowUp
                AccelCalibrationPosition.BACK -> Icons.Default.Refresh
            },
            contentDescription = position.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
    }
}

@Composable
private fun CalibrationActions(
    calibrationState: IMUCalibrationState,
    isConnected: Boolean,
    buttonText: String,
    onButtonClick: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (calibrationState) {
            is IMUCalibrationState.Idle -> {
                // Single button: "Start Calibration"
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontSize = 16.sp)
                }
            }

            is IMUCalibrationState.Initiating,
            is IMUCalibrationState.AwaitingUserInput,
            is IMUCalibrationState.ProcessingPosition -> {
                // During calibration: Cancel + Main button ("Click when Done")
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.weight(2f),
                    enabled = calibrationState is IMUCalibrationState.AwaitingUserInput,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontSize = 16.sp)
                }
            }

            is IMUCalibrationState.Success,
            is IMUCalibrationState.Failed,
            is IMUCalibrationState.Cancelled -> {
                // After calibration: Reset to start new
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Calibration", fontSize = 16.sp)
                }
            }
        }
    }
}
