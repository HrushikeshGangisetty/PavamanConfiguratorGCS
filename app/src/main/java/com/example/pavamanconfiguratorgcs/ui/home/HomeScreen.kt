package com.example.pavamanconfiguratorgcs.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToConfigurations: () -> Unit,
    onNavigateToFullParams: () -> Unit,
    onNavigateToConnection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val droneHeartbeatReceived by viewModel.droneHeartbeatReceived.collectAsStateWithLifecycle()

    // Colors
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
        WaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        StarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Title
                Column {
                    Text(
                        text = "PAVAMAN",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "CONFIGURATOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                }

                // Connection Status Badge
                ConnectionStatusBadge(
                    isConnected = droneHeartbeatReceived,
                    onReconnectClick = onNavigateToConnection,
                    accentColor = accentColor
                )
            }

            // Main Content - Cards centered
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Section header
                    Text(
                        text = "SELECT AN OPTION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Cards Row with descriptions below
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Column - Configurations
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(200.dp)
                        ) {
                            MenuCard(
                                title = "Configurations",
                                subtitle = "Setup & Calibrate",
                                icon = Icons.Default.Settings,
                                onClick = onNavigateToConfigurations,
                                cardColor = cardColor,
                                borderColor = Color(0xFF2D3A5C),
                                modifier = Modifier.size(width = 200.dp, height = 160.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Description text for Configurations
                            Text(
                                text = "Frame Type • Flight Modes\nCalibration • Failsafe • Motors",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.35f),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }

                        // Right Column - Full Params
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(200.dp)
                        ) {
                            MenuCard(
                                title = "Full Params",
                                subtitle = "View & Edit All",
                                icon = Icons.AutoMirrored.Filled.List,
                                onClick = onNavigateToFullParams,
                                cardColor = cardColor,
                                borderColor = Color(0xFF2D3A5C),
                                modifier = Modifier.size(width = 200.dp, height = 160.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Description text for Full Params
                            Text(
                                text = "Search • Filter • Edit\nSave to Vehicle • Export",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.35f),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Footer text
                    Text(
                        text = "ArduPilot Compatible  •  MAVLink 2.0",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.25f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Wave background decoration
 */
@Composable
fun WaveBackground(
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
 * Star decorations scattered around the screen
 */
@Composable
fun StarDecorations() {
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
            drawStar(
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
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

@Composable
fun ConnectionStatusBadge(
    isConnected: Boolean,
    onReconnectClick: () -> Unit,
    accentColor: Color
) {
    val statusColor = if (isConnected) accentColor else Color(0xFFFF6B6B)
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .then(
                if (!isConnected) Modifier.clickable { onReconnectClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C2541)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Animated status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = statusColor.copy(alpha = if (isConnected) 1f else pulseAlpha),
                        shape = CircleShape
                    )
            )

            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )

            if (!isConnected) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Reconnect",
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Simple menu card matching the reference design
 */
@Composable
fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    cardColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon container
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
