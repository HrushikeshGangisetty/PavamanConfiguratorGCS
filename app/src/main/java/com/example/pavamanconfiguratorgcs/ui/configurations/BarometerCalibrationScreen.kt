package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarometerCalibrationScreen(
    viewModel: BarometerCalibrationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Theme colors matching HomeScreen
    val backgroundColor = Color(0xFF0A0E21)
    val accentColor = Color(0xFF00D4AA)
    val cardColor = Color(0xFF1C2541)
    val waveColor = Color(0xFF1E3A5F)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        BarometerWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        BarometerStarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom Top Bar matching theme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
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
                        text = "BAROMETER",
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

            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3A5C))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Instructions",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Text(
                                text = "Place the drone on a flat surface. Ensure no wind or movement. Press Start Calibration.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Warnings Card
                    if (!uiState.isFlatSurface || !uiState.isWindGood) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B1B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (!uiState.isFlatSurface) {
                                    Text(
                                        text = "⚠ Place the drone on a flat surface.",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                if (!uiState.isWindGood) {
                                    Text(
                                        text = "⚠ Wind condition is not good. It is better to stop flying and calibrating the drone.",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Calibration Progress/Button Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3A5C))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (uiState.isCalibrating) {
                                Text(
                                    text = "Calibrating...",
                                    color = accentColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                // Progress indicator
                                LinearProgressIndicator(
                                    progress = { uiState.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = accentColor,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Text(
                                    text = "${uiState.progress}%",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                                )

                                Button(
                                    onClick = { viewModel.stopCalibration() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF6B6B)
                                    )
                                ) {
                                    Text(
                                        text = "Stop Calibration",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Ready to Calibrate",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Button(
                                    onClick = { viewModel.startCalibration() },
                                    enabled = uiState.isConnected && uiState.isFlatSurface &&
                                              uiState.isWindGood,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isConnected && uiState.isFlatSurface && uiState.isWindGood) {
                                            accentColor
                                        } else {
                                            Color(0xFF2D3A5C)
                                        },
                                        disabledContainerColor = Color(0xFF2D3A5C)
                                    )
                                ) {
                                    Text(
                                        text = "Start Calibration",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isConnected && uiState.isFlatSurface && uiState.isWindGood) {
                                            Color(0xFF0A0E21)
                                        } else {
                                            Color.White.copy(alpha = 0.5f)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Status Card
                    if (uiState.statusText.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3A5C))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Status",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = uiState.statusText,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Info about barometer calibration
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "About Barometer Calibration",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Barometer calibration ensures accurate altitude readings by measuring atmospheric pressure. This is essential for stable altitude hold and landing operations.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wave background decoration for Barometer screen
 */
@Composable
private fun BarometerWaveBackground(
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
 * Star decorations for Barometer screen
 */
@Composable
private fun BarometerStarDecorations() {
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
            drawBarometerStar(
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarometerStar(
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
