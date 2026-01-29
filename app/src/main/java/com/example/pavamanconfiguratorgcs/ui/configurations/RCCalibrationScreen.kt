package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// Theme colors
private val backgroundColor = Color(0xFF0A0E21)
private val cardColor = Color(0xFF1C2541)
private val accentColor = Color(0xFF00D4AA)
private val waveColor = Color(0xFF1E3A5F)
private val successColor = Color(0xFF4CAF50)
private val errorColor = Color(0xFFD32F2F)
private val warningColor = Color(0xFFFF9800)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RCCalibrationScreen(
    viewModel: RCCalibrationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSafetyDialog by remember { mutableStateOf(false) }
    var safetyWarningRead by remember { mutableStateOf(false) }

    // Announce safety warning when dialog is shown
    LaunchedEffect(showSafetyDialog) {
        if (showSafetyDialog) {
            safetyWarningRead = false
            // Enable button after a delay (simulating reading time)
            kotlinx.coroutines.delay(8000)
            safetyWarningRead = true
        }
    }

    // Safety Warning Dialog
    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Safety Warning",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "⚠️ Before proceeding, ensure:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Your transmitter is ON and receiver is powered and connected",
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Your motor does NOT have power/NO PROPS attached!!!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSafetyDialog = false
                        safetyWarningRead = false
                        viewModel.startCalibration()
                    },
                    enabled = safetyWarningRead,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        if (safetyWarningRead) "I Understand" else "Please wait...",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) {
                    Text("Cancel")
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
        RCCalibrationWaveBackground(modifier = Modifier.fillMaxSize())

        // Star decorations
        RCCalibrationStarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom header
            Surface(
                color = cardColor,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            when (uiState.calibrationState) {
                                is RCCalibrationState.Idle,
                                is RCCalibrationState.Ready,
                                is RCCalibrationState.Success,
                                is RCCalibrationState.Failed -> onNavigateBack()
                                else -> {
                                    // Could show confirmation dialog if calibration in progress
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "RC CALIBRATION",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Remote Controller Setup",
                            fontSize = 10.sp,
                            color = accentColor
                        )
                    }
                }
            }

            // Accent line separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accentColor)
            )

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Connection Status
                ConnectionStatusCard(isConnected = uiState.isConnected)

                Spacer(modifier = Modifier.height(16.dp))

                // Main RC Channels Display (Roll, Pitch, Throttle, Yaw)
                MainControlsCard(
                    channels = uiState.channels,
                    rollChannel = uiState.rollChannel,
                    pitchChannel = uiState.pitchChannel,
                    throttleChannel = uiState.throttleChannel,
                    yawChannel = uiState.yawChannel,
                    calibrationState = uiState.calibrationState,
                    onCalibrate = {
                        if (uiState.calibrationState is RCCalibrationState.Ready) {
                            showSafetyDialog = true
                        } else {
                            viewModel.onButtonClick()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // All Channels Display
                AllChannelsCard(
                    channels = uiState.channels,
                    calibrationState = uiState.calibrationState
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Button
                CalibrationButton(
                    buttonText = uiState.buttonText,
                    enabled = uiState.isConnected &&
                        (uiState.calibrationState is RCCalibrationState.Ready ||
                         uiState.calibrationState is RCCalibrationState.CapturingMinMax ||
                         uiState.calibrationState is RCCalibrationState.CapturingCenter),
                    isLoading = uiState.calibrationState is RCCalibrationState.Saving ||
                               uiState.calibrationState is RCCalibrationState.LoadingParameters,
                    onClick = {
                        if (uiState.calibrationState is RCCalibrationState.Ready) {
                            showSafetyDialog = true
                        } else {
                            viewModel.onButtonClick()
                        }
                    }
                )

                // Success/Failure Result
                when (val state = uiState.calibrationState) {
                    is RCCalibrationState.Success -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        ResultCard(
                            message = state.summary,
                            isSuccess = true
                        )
                    }
                    is RCCalibrationState.Failed -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        ResultCard(
                            message = state.errorMessage,
                            isSuccess = false
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF1B5E20) else Color(0xFF5D4037)
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isConnected) "✓ Connected to Vehicle" else "⚠ Not Connected",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MainControlsCard(
    channels: List<RCChannelData>,
    rollChannel: Int,
    pitchChannel: Int,
    throttleChannel: Int,
    yawChannel: Int,
    calibrationState: RCCalibrationState,
    onCalibrate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Main Flight Controls",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Small Calibrate button on the right
                Button(
                    onClick = onCalibrate,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Calibrate", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            RCChannelBar(
                label = "Roll (CH$rollChannel)",
                channel = channels.getOrNull(rollChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(10.dp))

            RCChannelBar(
                label = "Pitch (CH$pitchChannel)",
                channel = channels.getOrNull(pitchChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(10.dp))

            RCChannelBar(
                label = "Throttle (CH$throttleChannel)",
                channel = channels.getOrNull(throttleChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(10.dp))

            RCChannelBar(
                label = "Yaw (CH$yawChannel)",
                channel = channels.getOrNull(yawChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )
        }
    }
}

@Composable
private fun AllChannelsCard(
    channels: List<RCChannelData>,
    calibrationState: RCCalibrationState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Channel Monitor",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val left = channels.take(8)
            val right = channels.drop(8)

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    left.forEach { channel ->
                        RCChannelBar(
                            label = "Channel ${channel.channelNumber}",
                            channel = channel,
                            isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(modifier = Modifier.weight(1f)) {
                    right.forEach { channel ->
                        RCChannelBar(
                            label = "Channel ${channel.channelNumber}",
                            channel = channel,
                            isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RCChannelBar(
    label: String,
    channel: RCChannelData?,
    isCapturing: Boolean
) {
    if (channel == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.width(110.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Progress bar
        val progress = ((channel.currentValue - 800f) / (2200f - 800f)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .height(16.dp)
                .weight(1f)
                .background(Color(0xFF0D1426), RoundedCornerShape(6.dp))
                .padding(2.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val fillColor = if (isCapturing) errorColor else accentColor
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(progress)
                            .background(fillColor, RoundedCornerShape(4.dp))
                    )
                }
                val rem = (1f - progress).coerceAtLeast(0f)
                if (rem > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(rem)
                            .background(Color(0xFF2D3A5C), RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = channel.currentValue.toString(),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.width(56.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Checkbox(checked = false, onCheckedChange = null, enabled = false)
    }
}

@Composable
private fun CalibrationButton(
    buttonText: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            disabledContainerColor = Color(0xFF2D3A5C)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = buttonText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.LightGray
        )
    }
}

@Composable
private fun ResultCard(
    message: String,
    isSuccess: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isSuccess) "Success!" else "Failed",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

/**
 * Wave background for RC Calibration screen
 */
@Composable
private fun RCCalibrationWaveBackground(modifier: Modifier = Modifier) {
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
        val waveHeight = height * 0.1f
        val baseY = height * 0.85f

        for (i in 0..4) {
            val path = Path()
            val offsetY = i * 10f
            val alpha = (0.25f - i * 0.04f).coerceAtLeast(0.05f)

            path.moveTo(0f, baseY + offsetY)

            var x = 0f
            while (x <= width) {
                val y = baseY + offsetY + sin((x / width * 3 + animatedOffset / 60f + i * 0.5f).toDouble()).toFloat() * waveHeight * 0.3f
                path.lineTo(x, y)
                x += 5f
            }

            drawPath(
                path = path,
                color = waveColor.copy(alpha = alpha),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

/**
 * Star decorations for RC Calibration screen
 */
@Composable
private fun RCCalibrationStarDecorations() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starAlpha"
    )

    val starPositions = listOf(
        Offset(0.92f, 0.12f),
        Offset(0.08f, 0.3f),
        Offset(0.95f, 0.5f),
        Offset(0.05f, 0.7f),
        Offset(0.9f, 0.85f)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val starColor = Color.White

        starPositions.forEachIndexed { index, pos ->
            val x = size.width * pos.x
            val y = size.height * pos.y
            val alpha = if (index % 2 == 0) starAlpha else 1f - starAlpha

            val starSize = 5f
            val path = Path().apply {
                moveTo(x, y - starSize)
                lineTo(x, y + starSize)
                moveTo(x - starSize, y)
                lineTo(x + starSize, y)
            }

            drawPath(
                path = path,
                color = starColor.copy(alpha = alpha.coerceIn(0.1f, 0.6f)),
                style = Stroke(width = 1f)
            )
        }
    }
}

