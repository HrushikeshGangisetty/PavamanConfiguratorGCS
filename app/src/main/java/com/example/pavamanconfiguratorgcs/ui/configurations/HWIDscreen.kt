package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HWIDScreen(
    viewModel: com.example.pavamanconfiguratorgcs.ui.configurations.HWIDViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Provide a safe initial value when collecting the StateFlow
    val devices by viewModel.devices.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware IDs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF535350),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header row
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Parameter Name", modifier = Modifier.weight(0.4f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Dev ID", modifier = Modifier.weight(0.2f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Bus Type", modifier = Modifier.weight(0.2f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Bus Address", modifier = Modifier.weight(0.2f), color = Color.White, fontWeight = FontWeight.Bold)
                }

                Divider(color = Color.LightGray)

                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items = devices) { device: com.example.pavamanconfiguratorgcs.ui.configurations.DeviceInfoDetailed ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A4A48)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(device.paramName, modifier = Modifier.weight(0.4f), color = Color.White)

                                // Device ID formatted as hex for readability
                                val devHex = "0x" + device.deviceId.toLong().toString(16).uppercase()
                                Text(devHex, modifier = Modifier.weight(0.2f), color = Color.White)

                                Text(device.busType, modifier = Modifier.weight(0.2f), color = Color.White)
                                Text(device.busAddress, modifier = Modifier.weight(0.2f), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
