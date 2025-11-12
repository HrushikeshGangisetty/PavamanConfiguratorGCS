package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * ViewModel for IMU (Accelerometer) Calibration - MissionPlanner Protocol
 *
 * Protocol Flow (replicating MissionPlanner):
 *
 * 1. User clicks "Start IMU Calibration"
 *    - Send MAV_CMD_PREFLIGHT_CALIBRATION with param5=1 (start accel calibration)
 *    - Set _inCalibration = true
 *    - Button text changes to "Click when Done"
 *    - Subscribe to STATUSTEXT and COMMAND_LONG messages
 *
 * 2. FC sends STATUSTEXT: "Place vehicle level and press any key"
 *    - Display message to user
 *    - OR FC sends COMMAND_LONG with MAV_CMD_ACCELCAL_VEHICLE_POS (param1 = position enum)
 *    - Extract position from param1 and display to user
 *
 * 3. User places drone and clicks "Click when Done"
 *    - Send MAV_CMD_ACCELCAL_VEHICLE_POS with param1 = current position value
 *    - This tells FC that position is ready
 *
 * 4. FC continues to next position, sends next STATUSTEXT or COMMAND_LONG
 *    - Repeat until all 6 positions complete
 *
 * 5. FC sends STATUSTEXT: "Calibration successful" or "Calibration failed"
 *    - Stop calibration, unsubscribe listeners
 */
class IMUCalibrationViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "IMUCalibrationVM"

        // MAV_CMD_ACCELCAL_VEHICLE_POS command ID
        private const val MAV_CMD_ACCELCAL_VEHICLE_POS: UInt = 42429u

        // MAV_CMD_PREFLIGHT_CALIBRATION command ID
        private const val MAV_CMD_PREFLIGHT_CALIBRATION: UInt = 241u
    }

    private val _uiState = MutableStateFlow(IMUCalibrationUiState())
    val uiState: StateFlow<IMUCalibrationUiState> = _uiState.asStateFlow()

    // Calibration state tracking
    private var _inCalibration = false
    private var count = 0
    private var currentPosition: AccelCalibrationPosition? = null

    private var statusTextCollectorJob: Job? = null

    // Connection state
    val isConnected = telemetryRepository.connectionState
        .map {
            it is TelemetryRepository.ConnectionState.Connected ||
            it is TelemetryRepository.ConnectionState.HeartbeatVerified
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Observe connection state
        viewModelScope.launch {
            isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }
    }

    /**
     * Main calibration button click handler
     * - If not in calibration: Start calibration
     * - If in calibration: User confirms current position is ready
     */
    fun onButtonClick() {
        if (!_inCalibration) {
            startCalibration()
        } else {
            onPositionReady()
        }
    }

    /**
     * Start the IMU calibration process
     */
    private fun startCalibration() {
        if (!_uiState.value.isConnected) {
            _uiState.update {
                it.copy(
                    calibrationState = IMUCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = IMUCalibrationState.Initiating,
                    statusText = "Starting IMU calibration...",
                    currentPositionIndex = 0,
                    buttonText = "Click when Done"
                )
            }

            count = 0
            _inCalibration = true
            currentPosition = null

            // Subscribe to STATUSTEXT messages
            startMessageListeners()

            try {
                Log.d(TAG, "Sending MAV_CMD_PREFLIGHT_CALIBRATION (param5=1)")

                // Send PREFLIGHT_CALIBRATION command with param5=1 for accelerometer
                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_PREFLIGHT_CALIBRATION,
                    param1 = 0f, // Gyro
                    param2 = 0f, // Magnetometer
                    param3 = 0f, // Ground pressure
                    param4 = 0f, // Radio
                    param5 = 1f, // Accelerometer - START
                    param6 = 0f, // Compass/Motor
                    param7 = 0f  // Airspeed
                )

                Log.d(TAG, "✓ Calibration command sent, waiting for FC response...")

                _uiState.update {
                    it.copy(
                        statusText = "Waiting for flight controller response...",
                        buttonText = "Click when Done"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start calibration", e)
                _uiState.update {
                    it.copy(
                        calibrationState = IMUCalibrationState.Failed("Error: ${e.message}"),
                        statusText = "Error: ${e.message}",
                        buttonText = "Start Calibration"
                    )
                }
                _inCalibration = false
                stopMessageListeners()
            }
        }
    }

    /**
     * User confirms current position is ready
     */
    private fun onPositionReady() {
        val position = currentPosition
        if (position == null) {
            Log.w(TAG, "onPositionReady called but no current position set")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = IMUCalibrationState.ProcessingPosition(position),
                    statusText = "Processing ${position.name}...",
                    buttonText = "Click when Done"
                )
            }

            try {
                Log.d(TAG, "User confirmed ${position.name}, sending MAV_CMD_ACCELCAL_VEHICLE_POS with param1=${position.paramValue}")

                // Send ACCELCAL_VEHICLE_POS command with param1 = position value
                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_ACCELCAL_VEHICLE_POS,
                    param1 = position.paramValue.toFloat(),
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                count++
                Log.d(TAG, "✓ Position command sent (count=$count/6), waiting for next instruction...")

                _uiState.update {
                    it.copy(
                        statusText = "Waiting for next position...",
                        currentPositionIndex = count,
                        buttonText = "Click when Done"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing position", e)
                _uiState.update {
                    it.copy(
                        calibrationState = IMUCalibrationState.Failed("Error: ${e.message}"),
                        statusText = "Error: ${e.message}",
                        buttonText = "Start Calibration"
                    )
                }
                _inCalibration = false
                stopMessageListeners()
            }
        }
    }

    /**
     * Start listening to STATUSTEXT messages
     */
    private fun startMessageListeners() {
        Log.d(TAG, "Starting message listeners for calibration")

        // Listen to STATUSTEXT messages
        statusTextCollectorJob = viewModelScope.launch {
            telemetryRepository.calibrationStatus.collect { statusText ->
                Log.d(TAG, "STATUSTEXT: $statusText")
                handleStatusText(statusText)
            }
        }
    }

    /**
     * Handle STATUSTEXT messages from FC
     */
    private fun handleStatusText(text: String) {
        val lower = text.lowercase()

        // Update status text in UI
        _uiState.update { state ->
            state.copy(statusText = text)
        }

        // Check for completion messages
        when {
            lower.contains("calibration successful") || lower.contains("calibration complete") -> {
                Log.d(TAG, "✓ Calibration successful!")

                _uiState.update {
                    it.copy(
                        calibrationState = IMUCalibrationState.Success("Calibration completed successfully!"),
                        statusText = "Success! Calibration completed.",
                        buttonText = "Start Calibration"
                    )
                }
                _inCalibration = false
                stopMessageListeners()
            }

            lower.contains("calibration failed") -> {
                Log.e(TAG, "Calibration failed")

                _uiState.update {
                    it.copy(
                        calibrationState = IMUCalibrationState.Failed("Calibration failed"),
                        statusText = text,
                        buttonText = "Start Calibration"
                    )
                }
                _inCalibration = false
                stopMessageListeners()
            }

            // Try to parse position from STATUSTEXT
            else -> {
                AccelCalibrationPosition.fromStatusText(text)?.let { position ->
                    Log.d(TAG, "Parsed position from STATUSTEXT: ${position.name}")
                    currentPosition = position

                    _uiState.update {
                        it.copy(
                            calibrationState = IMUCalibrationState.AwaitingUserInput(
                                position = position,
                                instruction = position.instruction
                            ),
                            statusText = text,
                            currentPositionIndex = AccelCalibrationPosition.entries.indexOf(position),
                            buttonText = "Click when Done"
                        )
                    }
                }
            }
        }
    }

    /**
     * Stop listening to messages
     */
    private fun stopMessageListeners() {
        statusTextCollectorJob?.cancel()
        statusTextCollectorJob = null
        Log.d(TAG, "Stopped message listeners")
    }

    /**
     * Cancel calibration
     */
    fun cancelCalibration() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = IMUCalibrationState.Cancelled,
                    statusText = "Calibration cancelled",
                    currentPositionIndex = 0,
                    showCancelDialog = false,
                    buttonText = "Start Calibration"
                )
            }
            _inCalibration = false
            count = 0
            currentPosition = null
            stopMessageListeners()
        }
    }

    fun resetCalibration() {
        stopMessageListeners()
        _inCalibration = false
        count = 0
        currentPosition = null
        _uiState.update {
            it.copy(
                calibrationState = IMUCalibrationState.Idle,
                statusText = "",
                currentPositionIndex = 0,
                showCancelDialog = false,
                buttonText = "Start Calibration"
            )
        }
    }

    fun showCancelDialog(show: Boolean) {
        _uiState.update { it.copy(showCancelDialog = show) }
    }

    override fun onCleared() {
        super.onCleared()
        stopMessageListeners()
    }
}

