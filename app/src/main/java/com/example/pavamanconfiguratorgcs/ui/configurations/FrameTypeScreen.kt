package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameTypeScreen(
    viewModel: FrameTypeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

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
            Text("Select Frame", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FrameTypeCard(title = "Quad", isSelected = state.selectedFrame == FrameTypeViewModel.FrameKind.QUAD, modifier = Modifier.weight(1f)) {
                    viewModel.selectFrame(FrameTypeViewModel.FrameKind.QUAD)
                }
                FrameTypeCard(title = "Hexa", isSelected = state.selectedFrame == FrameTypeViewModel.FrameKind.HEXA, modifier = Modifier.weight(1f)) {
                    viewModel.selectFrame(FrameTypeViewModel.FrameKind.HEXA)
                }
                FrameTypeCard(title = "Octa", isSelected = state.selectedFrame == FrameTypeViewModel.FrameKind.OCTA, modifier = Modifier.weight(1f)) {
                    viewModel.selectFrame(FrameTypeViewModel.FrameKind.OCTA)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show the selection summary and applying status instead of listing parameters
            val selected = state.selectedFrame
            if (selected != null) {
                Text("Selected: ${selected.displayName}  (Type: X)", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.isApplying) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Applying parameters to firmware...", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Applied: ${state.appliedCount}", color = Color.LightGray)
            } else if (state.isSuccess) {
                Text("Parameters applied successfully.", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Applied count: ${state.appliedCount}", color = Color.LightGray)
            } else {
                // Not applying and not success -> show possible error message or instruction
                state.errorMessage?.let {
                    Text("Error: $it", color = Color.Red)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selected == null) {
                    Text("Tap a frame to apply its parameters to the firmware.", color = Color.LightGray)
                } else {
                    // selection exists but not applying and not success (likely error)
                    Text("Tap the frame again to retry applying parameters.", color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Optional: action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { viewModel.clearSelection() }) {
                    Text("Clear", color = Color.White)
                }
                TextButton(onClick = { selected?.let { viewModel.selectFrame(it) } }) {
                    Text("Retry", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FrameTypeCard(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF616161) else Color.Black)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
