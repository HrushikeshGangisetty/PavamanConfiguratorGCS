package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

// Theme colors matching other calibration screens
private val backgroundColor = Color(0xFF0A0E21)
private val accentColor = Color(0xFF00D4AA)
private val cardColor = Color(0xFF1C2541)
private val waveColor = Color(0xFF1E3A5F)
private val errorColor = Color(0xFFFF6B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassCalibrationScreen(
    viewModel: CompassCalibrationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Cancel confirmation dialog
    if (uiState.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog(false) },
            title = { Text("Cancel Calibration?", color = Color.White) },
            text = { Text("Are you sure you want to cancel the compass calibration? All progress will be lost.", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = cardColor,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelCalibration()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor)
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
        CompassWaveBackground(modifier = Modifier.fillMaxSize())

        // Star decorations
        CompassStarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                CompassCalibrationHeader(
                    onBackClick = {
                        if (uiState.calibrationState is CompassCalibrationState.Idle ||
                            uiState.calibrationState is CompassCalibrationState.Success ||
                            uiState.calibrationState is CompassCalibrationState.Failed ||
                            uiState.calibrationState is CompassCalibrationState.Cancelled
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
            CompassCalibrationProgress(
                overallProgress = uiState.overallProgress,
                calibrationState = uiState.calibrationState
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            CompassCalibrationContent(
                calibrationState = uiState.calibrationState,
                statusText = uiState.statusText,
                isConnected = uiState.isConnected,
                compassProgress = uiState.compassProgress,
                compassReports = uiState.compassReports,
                overallProgress = uiState.overallProgress,
                calibrationComplete = uiState.calibrationComplete
            )
        }

        // Action buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            CompassCalibrationActions(
                calibrationState = uiState.calibrationState,
                isConnected = uiState.isConnected,
                calibrationComplete = uiState.calibrationComplete,
                onStart = { viewModel.startCalibration() },
                onCancel = { viewModel.showCancelDialog(true) },
                onAccept = { viewModel.acceptCalibration() },
                onReset = { viewModel.resetCalibration() }
            )
        }
    }
}
}

@Composable
private fun CompassCalibrationHeader(onBackClick: () -> Unit) {
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
                text = "COMPASS",
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
private fun CompassCalibrationProgress(
    overallProgress: Int,
    calibrationState: CompassCalibrationState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (calibrationState is CompassCalibrationState.InProgress) {
            Text(
                text = "Progress: $overallProgress%",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LinearProgressIndicator(
            progress = { overallProgress.toFloat() / 100f },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp),
            color = when (calibrationState) {
                is CompassCalibrationState.Success -> accentColor
                is CompassCalibrationState.Failed -> errorColor
                is CompassCalibrationState.InProgress -> accentColor
                else -> Color.Gray
            },
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun CompassCalibrationContent(
    calibrationState: CompassCalibrationState,
    statusText: String,
    isConnected: Boolean,
    compassProgress: Map<Int, CompassProgress>,
    compassReports: Map<Int, CompassReport>,
    overallProgress: Int,
    calibrationComplete: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp),
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
                    is CompassCalibrationState.Idle -> IdleContent(isConnected)
                    is CompassCalibrationState.Starting -> StartingContent()
                    is CompassCalibrationState.InProgress -> InProgressContent(
                        instruction = calibrationState.currentInstruction,
                        compassProgress = compassProgress,
                        compassReports = compassReports,
                        overallProgress = overallProgress,
                        calibrationComplete = calibrationComplete
                    )
                    is CompassCalibrationState.Success -> SuccessContent(
                        calibrationState.message,
                        calibrationState.compassReports
                    )
                    is CompassCalibrationState.Failed -> FailedContent(calibrationState.errorMessage)
                    is CompassCalibrationState.Cancelled -> CancelledContent()
                }
            }
        }

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
            border = BorderStroke(1.dp, if (isConnected) accentColor.copy(alpha = 0.5f) else errorColor.copy(alpha = 0.5f))
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
                    tint = if (isConnected) accentColor else errorColor,
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
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = if (isConnected) accentColor else Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Compass Calibration",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isConnected) {
                "Ready to calibrate magnetometers"
            } else {
                "Connect to drone to start"
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                    text = "📋 Calibration Instructions:",
                    color = accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Hold vehicle in the air\n" +
                            "2. Rotate slowly - point each side down\n" +
                            "3. Follow on-screen rotation guidance\n" +
                            "4. Wait for all compasses to complete\n" +
                            "5. Review and accept calibration\n" +
                            "6. Reboot autopilot after success",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StartingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )

        Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier
                .size(80.dp)
                .rotate(angle)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Starting Calibration",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Initializing magnetometer calibration...",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InProgressContent(
    instruction: String,
    compassProgress: Map<Int, CompassProgress>,
    compassReports: Map<Int, CompassReport>,
    overallProgress: Int,
    calibrationComplete: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!calibrationComplete) {
            val infiniteTransition = rememberInfiniteTransition(label = "rotation")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "angle"
            )

            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(80.dp)
                    .rotate(angle)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Calibrating...",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Calibration Complete!",
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = instruction,
            color = accentColor,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (compassProgress.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1529)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Compass Progress:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    compassProgress.entries.sortedBy { it.key }.forEach { (compassId, progress) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Compass $compassId:",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.width(80.dp)
                            )

                            LinearProgressIndicator(
                                progress = { progress.completionPct.toFloat() / 100f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = accentColor,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            Text(
                                text = "${progress.completionPct}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.width(50.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        if (compassId != compassProgress.keys.max()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        if (compassReports.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1529)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Calibration Results:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    compassReports.entries.sortedBy { it.key }.forEach { (compassId, report) ->
                        CompassReportCard(compassId, report)

                        if (compassId != compassReports.keys.max()) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassReportCard(compassId: Int, report: CompassReport) {
    val isSuccess = report.calStatus.contains("SUCCESS", ignoreCase = true)
    val fitnessColor = when {
        report.fitness < 50f -> Color(0xFF4CAF50)
        report.fitness < 100f -> Color(0xFFFFA726)
        else -> Color.Red
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) Color(0xFF1B5E20).copy(alpha = 0.3f)
            else Color(0xFF5D4037).copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Compass $compassId",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = report.calStatus,
                        color = if (isSuccess) Color(0xFF4CAF50) else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Fitness:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Text(
                    text = String.format("%.2f", report.fitness) +
                            if (report.fitness < 100f) " (Good)" else " (Review)",
                    color = fitnessColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Offsets:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Text(
                    text = String.format("X:%.1f Y:%.1f Z:%.1f", report.ofsX, report.ofsY, report.ofsZ),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(message: String, compassReports: List<CompassReport>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Success!",
            color = Color(0xFF4CAF50),
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

        if (compassReports.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1529)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Final Calibration Results:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    compassReports.sortedBy { it.compassId.toInt() }.forEach { report ->
                        CompassReportCard(report.compassId.toInt(), report)
                        if (report != compassReports.last()) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4D3E)),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Please reboot the autopilot",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FailedContent(errorMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Failed",
            color = Color.Red,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5D4037).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Troubleshooting Tips:",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Ensure vehicle is away from metal objects\n" +
                            "• Rotate slowly and smoothly\n" +
                            "• Complete all 6 orientations fully\n" +
                            "• Check for magnetic interference\n" +
                            "• Try calibration again",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun CancelledContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Cancelled",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The calibration process was cancelled.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompassCalibrationActions(
    calibrationState: CompassCalibrationState,
    isConnected: Boolean,
    calibrationComplete: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (calibrationState) {
            is CompassCalibrationState.Idle -> {
                Button(
                    onClick = onStart,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isConnected) backgroundColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontSize = 16.sp, color = if (isConnected) backgroundColor else Color.White.copy(alpha = 0.5f))
                }

                Button(
                    onClick = onAccept,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                }

                Button(
                    onClick = onCancel,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = errorColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
            is CompassCalibrationState.Starting,
            is CompassCalibrationState.InProgress -> {
                Button(
                    onClick = onStart,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                }

                Button(
                    onClick = onAccept,
                    enabled = calibrationComplete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (calibrationComplete) backgroundColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept", fontSize = 16.sp, color = if (calibrationComplete) backgroundColor else Color.White.copy(alpha = 0.5f))
                }

                Button(
                    onClick = onCancel,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = errorColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", fontSize = 16.sp, color = Color.White)
                }
            }
            is CompassCalibrationState.Success,
            is CompassCalibrationState.Failed,
            is CompassCalibrationState.Cancelled -> {
                Button(
                    onClick = onReset,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = backgroundColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start", fontSize = 16.sp, color = backgroundColor)
                }

                Button(
                    onClick = onAccept,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept", fontSize = 16.sp)
                }

                Button(
                    onClick = onCancel,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = errorColor,
                        disabledContainerColor = Color(0xFF2D3A5C)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Wave background decoration for Compass screen
 */
@Composable
private fun CompassWaveBackground(modifier: Modifier = Modifier) {
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
 * Star decorations for Compass screen
 */
@Composable
private fun CompassStarDecorations() {
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
            drawCompassStar(
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompassStar(
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
