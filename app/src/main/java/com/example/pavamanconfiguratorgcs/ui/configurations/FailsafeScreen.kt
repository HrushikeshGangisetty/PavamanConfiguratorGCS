package com.example.pavamanconfiguratorgcs.ui.configurations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/**
 * Failsafe configuration screen bound to [FailsafeViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailsafeScreen(
    viewModel: FailsafeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val status by viewModel.status.collectAsState()
    val imminent by viewModel.isThrottleFailsafeImminent.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Failsafe", fontWeight = FontWeight.Bold) },
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
                ),
                actions = {
                    TextButton(onClick = { viewModel.loadFailsafeParameters() }) {
                        Text("Reload", color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF3A3A38))
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiveStatusPanel(status = status, imminent = imminent, viewModel = viewModel)
            RcServoBars(status = status)
            BatteryFailsafeConfig(config = config, onChange = viewModel::updateLocalConfig, onWriteAll = { viewModel.setAllBatteryFailsafe() }, onWriteSingle = viewModel::writeParameterChange)
            ThrottleFailsafeConfig(config = config, onChange = viewModel::updateLocalConfig, onWrite = viewModel::writeParameterChange)
            GcsFailsafeConfig(config = config, onChange = viewModel::updateLocalConfig, onWrite = viewModel::writeParameterChange)
        }
    }
}

// Alias composable to match requested naming in specification.
@Composable
fun FailsafeConfigScreen(
    viewModel: FailsafeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) = FailsafeScreen(viewModel = viewModel, onNavigateBack = onNavigateBack, modifier = modifier)

@Composable
private fun LiveStatusPanel(status: FailsafeViewModel.VehicleStatusState, imminent: Boolean, viewModel: FailsafeViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Live Status", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatusItem(label = "Mode", value = status.currentFlightMode, valueColor = if (imminent) Color.Red else Color.White)
                StatusItem(label = "Arm", value = viewModel.formatArmingStatus(status.isArmed))
                StatusItem(label = "GPS", value = viewModel.formatGpsStatus(status.gpsFixType))
            }
            if (imminent) {
                Spacer(Modifier.height(6.dp))
                Text("Throttle failsafe imminent (CH3 < FS_THR_VALUE)", color = Color.Red, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String, valueColor: Color = Color.White) {
    Column { Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp); Text(value, color = valueColor, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun RcServoBars(status: FailsafeViewModel.VehicleStatusState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Radio / Servo Activity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Radio Inputs", color = Color(0xFFCCCCCC), fontSize = 14.sp)
            status.rcInputs.entries.sortedBy { it.key }.forEach { (ch, pwm) ->
                PwmBar(label = "Radio $ch IN", pwm = pwm)
            }
            Spacer(Modifier.height(8.dp))
            Text("Servo Outputs", color = Color(0xFFCCCCCC), fontSize = 14.sp)
            status.servoOutputs.entries.sortedBy { it.key }.forEach { (ch, pwm) ->
                PwmBar(label = "Servo $ch OUT", pwm = pwm)
            }
        }
    }
}

@Composable
private fun PwmBar(label: String, pwm: Int) {
    val fraction = ((pwm - 1000).coerceIn(0, 1000)) / 1000f
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text("$pwm", color = Color.White, fontSize = 13.sp)
        }
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth().height(6.dp), color = Color(0xFF4CAF50))
    }
}

@Composable
private fun BatteryFailsafeConfig(
    config: FailsafeViewModel.FailsafeConfigState,
    onChange: (String, Any?) -> Unit,
    onWriteAll: () -> Unit,
    onWriteSingle: (String, Any) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Battery Failsafe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            FailsafeDropdown(
                label = "Action (BATT_FS_LOW_ACT)",
                current = config.battFsLowAct,
                options = listOf(
                    0 to "Disabled",
                    1 to "Land",
                    2 to "RTL",
                    3 to "SmartRTL",
                    4 to "Terminate"
                ),
                onSelect = { onChange("BATT_FS_LOW_ACT", it) }
            )
            NumericField(label = "Low Volt (LOW_VOLT)", value = config.lowVolt?.toString() ?: "", suffix = "V") { txt ->
                onChange("LOW_VOLT", txt)
            }
            NumericField(label = "Capacity (FS_BATT_MAH)", value = config.fsBattMah?.toString() ?: "", suffix = "mAh") { txt ->
                onChange("FS_BATT_MAH", txt.toIntOrNull())
            }
            NumericField(label = "Low Timer (BATT_LOW_TIMER)", value = config.battLowTimer?.toString() ?: "", suffix = "s") { txt ->
                onChange("BATT_LOW_TIMER", txt.toIntOrNull())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onWriteAll() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                    Text("SET ALL BATTERY", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = {
                    // Write individually for convenience
                    config.battFsLowAct?.let { onWriteSingle("BATT_FS_LOW_ACT", it) }
                    config.lowVolt?.let { onWriteSingle("LOW_VOLT", it) }
                    config.fsBattMah?.let { onWriteSingle("FS_BATT_MAH", it) }
                    config.battLowTimer?.let { onWriteSingle("BATT_LOW_TIMER", it) }
                }) { Text("Set Individually") }
            }
        }
    }
}

@Composable
private fun ThrottleFailsafeConfig(
    config: FailsafeViewModel.FailsafeConfigState,
    onChange: (String, Any?) -> Unit,
    onWrite: (String, Any) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Throttle Failsafe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            FailsafeDropdown(
                label = "Enable (FS_THR_ENABLE)",
                current = config.fsThrEnable,
                options = listOf(
                    0 to "Disabled",
                    1 to "Enabled",
                    2 to "Auto Only"
                ),
                onSelect = { onChange("FS_THR_ENABLE", it) }
            )
            NumericField(label = "PWM Threshold (FS_THR_VALUE)", value = config.fsThrValue?.toString() ?: "", suffix = "") { txt ->
                onChange("FS_THR_VALUE", txt.toIntOrNull())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { config.fsThrEnable?.let { onWrite("FS_THR_ENABLE", it) } }) { Text("Set Enable") }
                OutlinedButton(onClick = { config.fsThrValue?.let { onWrite("FS_THR_VALUE", it) } }) { Text("Set Value") }
            }
        }
    }
}

@Composable
private fun GcsFailsafeConfig(
    config: FailsafeViewModel.FailsafeConfigState,
    onChange: (String, Any?) -> Unit,
    onWrite: (String, Any) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2C))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("GCS Failsafe", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            FailsafeDropdown(
                label = "Enable (FS_GCS_ENABLE)",
                current = config.fsGcsEnable,
                options = listOf(0 to "Disabled", 1 to "Enabled"),
                onSelect = { onChange("FS_GCS_ENABLE", it) }
            )
            OutlinedButton(onClick = { config.fsGcsEnable?.let { onWrite("FS_GCS_ENABLE", it) } }) { Text("Set GCS") }
        }
    }
}

@Composable
private fun NumericField(label: String, value: String, suffix: String, onValueChange: (String) -> Unit) {
    Column { Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color(0xFF777777),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            trailingIcon = { if (suffix.isNotBlank()) Text(suffix, color = Color(0xFFBBBBBB)) }
        ) }
}

@Composable
private fun FailsafeDropdown(
    label: String,
    current: Int?,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column { Text(label, color = Color(0xFFBBBBBB), fontSize = 12.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text(options.firstOrNull { it.first == current }?.second ?: "Select", color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color(0xFF444442)) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(value)
                })
            }
        }
    }
}
