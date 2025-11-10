package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple Battery Monitor screen placeholder. Extend with live telemetry / parameter writes later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryMonitorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery Monitor", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Battery Monitor",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "This screen will display live battery telemetry (voltage, current, consumed mAh) and related parameters.",
                color = Color(0xFFCCCCCC)
            )
            // TODO: Hook into telemetry repository and show real-time values.
            PlaceholderBatteryStats()
        }
    }
}

@Composable
private fun PlaceholderBatteryStats() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live Battery Stats", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            StatRow(label = "Voltage", value = "--.- V")
            StatRow(label = "Current", value = "--.- A")
            StatRow(label = "Consumed", value = "---- mAh")
            StatRow(label = "Remaining", value = "-- %")
            Text("(Values pending telemetry integration)", fontSize = 12.sp, color = Color(0xFFBBBBBB))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 16.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

