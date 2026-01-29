package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HWIDScreen(
    viewModel: HWIDViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    // Provide a safe initial value when collecting the StateFlow
    val devices by viewModel.devices.collectAsState(initial = emptyList())

    // Collect loading progress from repository via ViewModel
    val loading by viewModel.loadingProgress.collectAsState(
        initial = com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository.LoadingProgress(0, 0)
    )

    // Theme colors matching HomeScreen
    val backgroundColor = Color(0xFF0A0E21)
    val accentColor = Color(0xFF00D4AA)
    val cardColor = Color(0xFF1C2541)
    val waveColor = Color(0xFF1E3A5F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        HWIDWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        HWIDStarDecorations()

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
                if (onNavigateBack != null) {
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
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HARDWARE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "IDs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshParameters() },
                    modifier = Modifier
                        .background(
                            color = cardColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = accentColor
                    )
                }
            }

            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = BorderStroke(1.dp, Color(0xFF2D3A5C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            "Parameter",
                            modifier = Modifier.weight(0.4f),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Dev ID",
                            modifier = Modifier.weight(0.2f),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Bus Type",
                            modifier = Modifier.weight(0.2f),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Address",
                            modifier = Modifier.weight(0.2f),
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Loading Status Card
                if (loading.errorMessage != null || loading.total > 0 || !loading.isComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (loading.errorMessage != null) {
                                Text(
                                    "Error: ${loading.errorMessage}",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp
                                )
                            }

                            if (loading.total > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${loading.current}/${loading.total}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(0.25f)
                                    )
                                    val progress = (loading.current.toFloat() / loading.total.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .weight(0.75f)
                                            .height(6.dp),
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                }
                            } else if (!loading.isComplete) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Loading...",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(0.3f)
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .height(6.dp),
                                        color = accentColor,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                }
                            }

                            if (loading.isComplete && loading.total > 0) {
                                Text(
                                    "✓ Load complete (${loading.current}/${loading.total})",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Scrollable list of devices
                if (devices.isEmpty()) {
                    // Empty state card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, Color(0xFF2D3A5C).copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hardware IDs found.\nTap refresh to retry.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(items = devices) { device ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                border = BorderStroke(1.dp, Color(0xFF2D3A5C)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        device.paramName,
                                        modifier = Modifier.weight(0.4f),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )

                                    // Display device ID as decimal (base 10)
                                    val displayId = try {
                                        val s = device.deviceId.trim()
                                        if (s.startsWith("0x", true)) {
                                            s.substring(2).toULong(16).toString()
                                        } else s
                                    } catch (_: Exception) { device.deviceId }

                                    Text(
                                        displayId,
                                        modifier = Modifier.weight(0.2f),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )

                                    Text(
                                        device.busType,
                                        modifier = Modifier.weight(0.2f),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )

                                    Text(
                                        device.busAddress,
                                        modifier = Modifier.weight(0.2f),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wave background decoration for HWID screen
 */
@Composable
private fun HWIDWaveBackground(
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
 * Star decorations for HWID screen
 */
@Composable
private fun HWIDStarDecorations() {
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
            drawHWIDStar(
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHWIDStar(
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