package com.example.pavamanconfiguratorgcs.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToConfigurations: () -> Unit,
    onNavigateToFullParams: () -> Unit,
    onNavigateToConnection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val droneHeartbeatReceived by viewModel.droneHeartbeatReceived.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Pavaman Configurator",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Connection Status Indicator
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (droneHeartbeatReceived) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (droneHeartbeatReceived) "Connected" else "Drone Disconnected",
                                color = if (droneHeartbeatReceived) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        // Show Reconnect button when disconnected
                        if (!droneHeartbeatReceived) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onNavigateToConnection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                ),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Reconnect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A5F),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F), // Dark navy blue
                            Color(0xFF2B4A73), // Medium navy blue
                            Color(0xFF1E3A5F)  // Dark navy blue
                        )
                    )
                )
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Configurations Card
                MenuCard(
                    title = "Configurations",
                    onClick = onNavigateToConfigurations,
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4A6A8A), // Light blue-gray
                            Color(0xFF5A7A9A)  // Lighter blue-gray
                        )
                    )
                )

                Spacer(modifier = Modifier.width(32.dp))

                // Full Params Card
                MenuCard(
                    title = "Full Params",
                    onClick = onNavigateToFullParams,
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    gradient = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4A6A8A), // Light blue-gray
                            Color(0xFF5A7A9A)  // Lighter blue-gray
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun MenuCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 16.dp,
            pressedElevation = 20.dp,
            hoveredElevation = 18.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 24.sp
            )
        }
    }
}
