package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavamanconfiguratorgcs.data.models.FrameType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameTypeScreen(
    viewModel: FrameTypeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect states from the new ViewModel API
    val frameConfig by viewModel.frameConfig.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val motorLayout by viewModel.motorLayout.collectAsState()

    // Detect frame parameters when screen opens
    LaunchedEffect(Unit) {
        viewModel.detectFrameParameters()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frame Type", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(16.dp)
        ) {
            // Current Frame Status
            Text("Select Frame", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Current: ${frameConfig.getStatusDescription()}",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Frame Type Selection Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FrameTypeCard(
                    title = "Quad-X",
                    isSelected = frameConfig.currentFrameType == FrameType.QUAD_X,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    viewModel.changeFrameType(FrameType.QUAD_X)
                }
                FrameTypeCard(
                    title = "Hexa-X",
                    isSelected = frameConfig.currentFrameType == FrameType.HEXA_X,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    viewModel.changeFrameType(FrameType.HEXA_X)
                }
                FrameTypeCard(
                    title = "Octa-X",
                    isSelected = frameConfig.currentFrameType == FrameType.OCTA_X,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    viewModel.changeFrameType(FrameType.OCTA_X)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reboot Warning
            if (frameConfig.rebootRequired) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B35)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ Reboot Required",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Frame type change will take effect after vehicle reboot.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.acknowledgeReboot() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Rebooted", color = Color(0xFFFF6B35))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Motor Layout Display
            motorLayout?.let { layout ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Motor Layout - ${layout.frameType.displayName}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        layout.motors.forEach { motor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = motor.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = motor.description,
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Status Messages
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Loading...", color = Color.White)
                }
            }

            // Error Message
            error?.let { errorMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❌ Error: $errorMsg",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }

            // UI Message
            uiMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.contains("success", ignoreCase = true))
                            Color(0xFF4CAF50)
                        else
                            Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info Text
            if (!frameConfig.isDetected) {
                Text(
                    text = "Frame parameters not detected. Ensure parameters are loaded.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Parameter scheme: ${frameConfig.paramScheme.name}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FrameTypeCard(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFF616161)
                !enabled -> Color(0xFF1C1C1A)
                else -> Color.Black
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (enabled) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
