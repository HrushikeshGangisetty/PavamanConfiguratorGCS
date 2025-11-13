package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pavamanconfiguratorgcs.data.models.ServoChannel
import com.example.pavamanconfiguratorgcs.data.models.ServoFunction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServoOutputScreen(
    viewModel: ServoOutputViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val servoChannels by viewModel.servoChannels.collectAsStateWithLifecycle()
    val vehicleState by viewModel.vehicleState.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val testModeEnabled by viewModel.testModeEnabled.collectAsStateWithLifecycle()

    // Show message snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Servo Output",
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
                .padding(16.dp)
        ) {
            // Warning banner if armed
            if (vehicleState.isArmed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(
                        text = "⚠ VEHICLE ARMED - Servo testing disabled for safety",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Position",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(100.dp)
                )
                Text(
                    text = "Rev",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Function",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = "Min",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(70.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Trim",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(70.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Max",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(70.dp),
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            // Servo channels list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(servoChannels) { index, channel ->
                    ServoChannelRow(
                        channel = channel,
                        onFunctionChange = { function ->
                            viewModel.updateChannelFunction(channel.channelIndex, function)
                        },
                        onReverseChange = { reversed ->
                            viewModel.updateChannelReverse(channel.channelIndex, reversed)
                        },
                        onMinChange = { min ->
                            viewModel.updateChannelMin(channel.channelIndex, min)
                        },
                        onTrimChange = { trim ->
                            viewModel.updateChannelTrim(channel.channelIndex, trim)
                        },
                        onMaxChange = { max ->
                            viewModel.updateChannelMax(channel.channelIndex, max)
                        },
                        testModeEnabled = testModeEnabled && !vehicleState.isArmed,
                        onTestServo = { pwm ->
                            viewModel.testServo(channel.channelIndex, pwm)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServoChannelRow(
    channel: ServoChannel,
    onFunctionChange: (ServoFunction) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onMinChange: (Int) -> Unit,
    onTrimChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
    testModeEnabled: Boolean,
    onTestServo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedFunction by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel number + PWM slider
        Column(
            modifier = Modifier.width(100.dp)
        ) {
            Text(
                text = channel.channelIndex.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // PWM Display Slider (read-only display with gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        color = Color(0xFF2A2A28),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(4.dp))
            ) {
                // Green gradient fill based on PWM value
                val fillFraction = if (channel.pwm > 0) {
                    ((channel.pwm - 1000).coerceIn(0, 1000) / 1000f).coerceIn(0f, 1f)
                } else 0f

                if (fillFraction > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }

                // PWM value text
                Text(
                    text = if (channel.pwm > 0) channel.pwm.toString() else "0",
                    color = if (channel.pwm > 0) Color.White else Color(0xFF888888),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Reverse checkbox
        Checkbox(
            checked = channel.reverse,
            onCheckedChange = onReverseChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50),
                uncheckedColor = Color.Gray
            ),
            modifier = Modifier.width(40.dp)
        )

        // Function dropdown
        Box(modifier = Modifier.width(140.dp)) {
            OutlinedButton(
                onClick = { expandedFunction = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF2A2A28),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = channel.function.displayName,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            DropdownMenu(
                expanded = expandedFunction,
                onDismissRequest = { expandedFunction = false },
                modifier = Modifier.background(Color(0xFF2A2A28))
            ) {
                ServoFunction.getAllFunctions().forEach { function ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = function.displayName,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        },
                        onClick = {
                            onFunctionChange(function)
                            expandedFunction = false
                        }
                    )
                }
            }
        }

        // Min PWM spinner
        NumberSpinner(
            value = channel.minPwm,
            onValueChange = onMinChange,
            modifier = Modifier.width(70.dp)
        )

        // Trim PWM spinner
        NumberSpinner(
            value = channel.trimPwm,
            onValueChange = onTrimChange,
            modifier = Modifier.width(70.dp)
        )

        // Max PWM spinner
        NumberSpinner(
            value = channel.maxPwm,
            onValueChange = onMaxChange,
            modifier = Modifier.width(70.dp)
        )
    }
}

@Composable
fun NumberSpinner(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 800..2200,
    step: Int = 50
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Up button
        Button(
            onClick = {
                val newValue = (value + step).coerceIn(range)
                if (newValue != value) onValueChange(newValue)
            },
            modifier = Modifier
                .width(50.dp)
                .height(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF444444)
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("▲", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // Value display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF2A2A28), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Down button
        Button(
            onClick = {
                val newValue = (value - step).coerceIn(range)
                if (newValue != value) onValueChange(newValue)
            },
            modifier = Modifier
                .width(50.dp)
                .height(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF444444)
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("▼", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
