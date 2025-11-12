package com.example.pavamanconfiguratorgcs.ui.configurations

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToEscCalibration: () -> Unit = {},
    onNavigateToFrameType: () -> Unit = {},
    onNavigateToFlightModes: () -> Unit = {},
    onNavigateToServoOutput: () -> Unit = {},
    onNavigateToSerialPorts: () -> Unit = {},
    onNavigateToMotorTest: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Configurations",
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Configurations",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable list of configuration options
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(getConfigurationItems()) { item ->
                    ConfigurationCard(
                        title = item.title,
                        onClick = {
                            when (item.route) {
                                "esc_calibration" -> onNavigateToEscCalibration()
                                "frame_type" -> onNavigateToFrameType()
                                "flight_modes" -> onNavigateToFlightModes()
                                "servo_output" -> onNavigateToServoOutput()
                                "serial_ports" -> onNavigateToSerialPorts()
                                "motor_test" -> onNavigateToMotorTest()
                                // Handle other routes here
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "• $title",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                tint = Color.White
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
        ConfigurationItem("Motor Test", "motor_test")
        // Add more items here as needed
    )
}
