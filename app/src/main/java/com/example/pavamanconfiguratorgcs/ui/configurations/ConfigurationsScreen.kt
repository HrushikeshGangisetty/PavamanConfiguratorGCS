package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun ConfigurationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToEscCalibration: () -> Unit = {},
    onNavigateToFrameType: () -> Unit = {},
    onNavigateToFlightModes: () -> Unit = {},
    onNavigateToServoOutput: () -> Unit = {},
    onNavigateToSerialPorts: () -> Unit = {},
    onNavigateToMotorTest: () -> Unit = {},
    onNavigateToFailsafe: () -> Unit = {},
    onNavigateToBatteryMonitor: () -> Unit = {},
    onNavigateToCompassCalibration: () -> Unit = {},
    onNavigateToRCCalibration: () -> Unit = {},
    onNavigateToIMUCalibration: () -> Unit = {},
    onNavigateToHWID: () -> Unit = {},
    onNavigateToBarometer: () -> Unit = {}
) {
    // Theme colors matching HomeScreen
    val backgroundColor = Color(0xFF0A0E21)
    val cardColor = Color(0xFF1C2541)
    val accentColor = Color(0xFF00D4AA)
    val waveColor = Color(0xFF1E3A5F)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Wave background decoration
        ConfigWaveBackground(
            waveColor = waveColor,
            modifier = Modifier.fillMaxSize()
        )

        // Star decorations
        ConfigStarDecorations()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Custom compact header
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
                        onClick = onNavigateBack,
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
                            "CONFIGURATIONS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Setup & Calibration",
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

            // Scrollable list of configuration options
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(getConfigurationItems()) { item ->
                    ConfigurationCard(
                        title = item.title,
                        cardColor = cardColor,
                        accentColor = accentColor,
                        onClick = {
                            when (item.route) {
                                "esc_calibration" -> onNavigateToEscCalibration()
                                "frame_type" -> onNavigateToFrameType()
                                "flight_modes" -> onNavigateToFlightModes()
                                "servo_output" -> onNavigateToServoOutput()
                                "serial_ports" -> onNavigateToSerialPorts()
                                "motor_test" -> onNavigateToMotorTest()
                                "failsafe" -> onNavigateToFailsafe()
                                "battery_monitor" -> onNavigateToBatteryMonitor()
                                "compass_calibration" -> onNavigateToCompassCalibration()
                                "rc_calibration" -> onNavigateToRCCalibration()
                                "imu_calibration" -> onNavigateToIMUCalibration()
                                "hwid" -> onNavigateToHWID()
                                "barometer_calibration" -> onNavigateToBarometer()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigurationCard(
    title: String,
    cardColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Wave background for configurations screen
 */
@Composable
fun ConfigWaveBackground(
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
 * Star decorations for configurations screen
 */
@Composable
fun ConfigStarDecorations() {
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

data class ConfigurationItem(
    val title: String,
    val route: String = ""
)

fun getConfigurationItems(): List<ConfigurationItem> {
    return listOf(
        ConfigurationItem("ESC Calibration", "esc_calibration"),
        ConfigurationItem("Frame Type", "frame_type"),
        ConfigurationItem("Flight Modes", "flight_modes"),
        ConfigurationItem("Servo Output", "servo_output"),
        ConfigurationItem("Serial Ports", "serial_ports"),
        ConfigurationItem("Motor Test", "motor_test"),
        ConfigurationItem("Failsafe", "failsafe"),
        ConfigurationItem("Battery Monitor", "battery_monitor"),
        ConfigurationItem("Compass Calibration", "compass_calibration"),
        ConfigurationItem("RC Calibration", "rc_calibration"),
        ConfigurationItem("IMU Calibration", "imu_calibration"),
        ConfigurationItem("HWID", "hwid"),
        ConfigurationItem("Barometer Calibration", "barometer_calibration")
    )
}
