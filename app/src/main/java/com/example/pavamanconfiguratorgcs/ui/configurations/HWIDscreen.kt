package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    // Provide a safe initial value when collecting the StateFlow
    val devices by viewModel.devices.collectAsState(initial = emptyList())

    // Collect loading progress from repository via ViewModel
    val loading by viewModel.loadingProgress.collectAsState(
        initial = com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository.LoadingProgress(0, 0)
    )

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
                actions = {
                    IconButton(onClick = { viewModel.refreshParameters() }) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
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

                HorizontalDivider(color = Color.LightGray)

                // Loading status
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (loading.errorMessage != null) {
                        Text("Error: ${loading.errorMessage}", color = Color(0xFFFFB4AB))
                    }

                    if (loading.total > 0) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${loading.current}/${loading.total}", color = Color.LightGray, modifier = Modifier.weight(0.2f))
                            val progress = (loading.current.toFloat() / loading.total.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = progress, modifier = Modifier.weight(0.8f).height(6.dp))
                        }
                    } else if (!loading.isComplete) {
                        // Indeterminate progress when total isn't known yet
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Loading parameters...", color = Color.LightGray, modifier = Modifier.weight(0.3f))
                            LinearProgressIndicator(modifier = Modifier.weight(0.7f).height(6.dp))
                        }
                    }

                    // Show completion state
                    if (loading.isComplete) {
                        Text("Parameter load complete (${loading.current}/${loading.total})", color = Color(0xFFB7E4C7))
                    }
                }

                // Space between status and list
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable list of devices
                if (devices.isEmpty()) {
                    // Friendly empty state while parameters load
                    Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
                        Text("No hardware IDs found. Tap refresh to retry.", color = Color.LightGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(items = devices) { device ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A4A48)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(device.paramName, modifier = Modifier.weight(0.4f), color = Color.White)

                                    // Display device ID as decimal (base 10). If a hex string slipped through, convert it here.
                                    val displayId = try {
                                        val s = device.deviceId.trim()
                                        if (s.startsWith("0x", true)) {
                                            s.substring(2).toULong(16).toString()
                                        } else s
                                    } catch (_: Exception) { device.deviceId }

                                    Text(displayId, modifier = Modifier.weight(0.2f), color = Color.White)

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
}