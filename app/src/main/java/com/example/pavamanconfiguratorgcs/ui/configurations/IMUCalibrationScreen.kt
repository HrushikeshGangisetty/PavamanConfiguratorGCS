package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// Theme colors matching HomeScreen
private val backgroundColor = Color(0xFF0A0E21)
private val accentColor = Color(0xFF00D4AA)
private val cardColor = Color(0xFF1C2541)
private val waveColor = Color(0xFF1E3A5F)

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
            title = { Text("Cancel Calibration?", color = Color.White) },
            text = { Text("Are you sure you want to cancel the calibration process?", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = cardColor,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelCalibration()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog(false) }) {
                    Text("Continue Calibration", color = accentColor)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        IMUWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        IMUStarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header - Fixed at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
}

@Composable
private fun CalibrationHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "IMU",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = "CALIBRATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                letterSpacing = 2.sp
            )
        }
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
            progress = { if (totalPositions > 0) currentPosition.toFloat() / totalPositions.toFloat() else 0f },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp),
            color = when (calibrationState) {
                is IMUCalibrationState.Success -> accentColor
                is IMUCalibrationState.Failed -> Color(0xFFFF6B6B)
                else -> accentColor
            },
            trackColor = Color.White.copy(alpha = 0.1f)
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
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2D3A5C))
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
                containerColor = if (isConnected) Color(0xFF1B4D3E) else Color(0xFF4D2B1B)
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (isConnected) accentColor.copy(alpha = 0.5f) else Color(0xFFFF6B6B).copy(alpha = 0.5f))
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
                    tint = if (isConnected) accentColor else Color(0xFFFF6B6B),
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
            tint = if (isConnected) accentColor else Color.Gray,
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1529)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "📋 6 Positions Required:",
                    color = accentColor,
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
            tint = accentColor,
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
            color = accentColor,
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
            color = accentColor
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
            tint = accentColor,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Success!",
            color = accentColor,
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
            tint = Color(0xFFFF6B6B),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Failed",
            color = Color(0xFFFF6B6B),
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
            .background(accentColor.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (position) {
                AccelCalibrationPosition.LEVEL -> Icons.Default.CheckCircle
                AccelCalibrationPosition.LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                AccelCalibrationPosition.RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                AccelCalibrationPosition.NOSEDOWN -> Icons.Default.KeyboardArrowDown
                AccelCalibrationPosition.NOSEUP -> Icons.Default.KeyboardArrowUp
                AccelCalibrationPosition.BACK -> Icons.Default.Refresh
            },
            contentDescription = position.name,
            tint = accentColor,
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
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isConnected) backgroundColor else Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontSize = 16.sp, color = if (isConnected) backgroundColor else Color.White.copy(alpha = 0.5f))
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
                        containerColor = Color(0xFF2D3A5C)
                    )
                ) {
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.weight(2f),
                    enabled = calibrationState is IMUCalibrationState.AwaitingUserInput,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    )
                ) {
                    val enabled = calibrationState is IMUCalibrationState.AwaitingUserInput
                    Icon(Icons.Default.Check, contentDescription = null, tint = if (enabled) backgroundColor else Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontSize = 16.sp, color = if (enabled) backgroundColor else Color.White.copy(alpha = 0.5f))
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
                        containerColor = accentColor
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = backgroundColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start New Calibration", fontSize = 16.sp, color = backgroundColor)
                }
            }
        }
    }
}

/**
 * Wave background decoration for IMU screen
 */
@Composable
private fun IMUWaveBackground(
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
        val baseY = height * 0.7f

        // Draw multiple wave lines
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
 * Star decorations for IMU screen
 */
@Composable
private fun IMUStarDecorations() {
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

    // Star positions (relative to screen)
    val starPositions = listOf(
        Offset(0.15f, 0.35f),
        Offset(0.45f, 0.22f),
        Offset(0.85f, 0.28f),
        Offset(0.25f, 0.75f),
        Offset(0.55f, 0.68f),
        Offset(0.78f, 0.55f)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val starColor = Color.White

        starPositions.forEachIndexed { index, pos ->
            val x = size.width * pos.x
            val y = size.height * pos.y
            val alpha = if (index % 2 == 0) starAlpha else 1.3f - starAlpha

            // Draw 4-point star
            drawIMUStar(
                center = Offset(x, y),
                size = 8f,
                color = starColor.copy(alpha = alpha.coerceIn(0.2f, 1f))
            )
        }
    }
}

/**
 * Draw a 4-point star
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIMUStar(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        // Vertical line
        moveTo(center.x, center.y - size)
        lineTo(center.x, center.y + size)

        // Horizontal line
        moveTo(center.x - size, center.y)
        lineTo(center.x + size, center.y)

        // Diagonal lines (smaller)
        val smallSize = size * 0.5f
        moveTo(center.x - smallSize, center.y - smallSize)
        lineTo(center.x + smallSize, center.y + smallSize)

        moveTo(center.x + smallSize, center.y - smallSize)
        lineTo(center.x - smallSize, center.y + smallSize)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.5f)
    )
}
