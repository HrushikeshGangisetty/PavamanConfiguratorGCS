package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import com.example.pavamanconfiguratorgcs.telemetry.helpers.RCChannelsHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel for ArduPilot RC (Radio Control) Calibration following MVVM architecture.
 *
 * Implements the MissionPlanner RC calibration protocol:
 * 1. Load RC mapping parameters (RCMAP_ROLL, RCMAP_PITCH, RCMAP_THROTTLE, RCMAP_YAW)
 * 2. Start RC_CHANNELS streaming at 10Hz
 * 3. Capture min/max values while user moves sticks to extremes
 * 4. Capture center/trim values
 * 5. Save all calibration parameters to vehicle
 */
class RCCalibrationViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "RCCalVM"
    }

    private val rcChannelsHelper = RCChannelsHelper(telemetryRepository)

    private val _uiState = MutableStateFlow(RCCalibrationUiState())
    val uiState: StateFlow<RCCalibrationUiState> = _uiState.asStateFlow()

    private var rcChannelsListenerJob: Job? = null

    // Calibration tracking
    private val capturedMin = IntArray(16) { 1500 }
    private val capturedMax = IntArray(16) { 1500 }
    private val capturedTrim = IntArray(16) { 1500 }

    // Connection state
    val isConnected = telemetryRepository.connectionState
        .map { it is TelemetryRepository.ConnectionState.Connected ||
               it is TelemetryRepository.ConnectionState.HeartbeatVerified }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Observe connection state
        viewModelScope.launch {
            isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected && _uiState.value.calibrationState is RCCalibrationState.Idle) {
                    loadParameters()
                }
            }
        }
    }

    /**
     * STEP 1: Load RC mapping parameters and start RC_CHANNELS streaming.
     */
    private fun loadParameters() {
        viewModelScope.launch {
            Log.d(TAG, "========== LOADING RC PARAMETERS ==========")
            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.LoadingParameters,
                    statusText = "Loading RC channel mapping..."
                )
            }

            try {
                // Request RC channel mapping parameters
                val paramValues = rcChannelsHelper.requestRCMappingParameters(parameterRepository)

                // Extract channel mappings (default to standard if not received)
                val rollCh = paramValues["RCMAP_ROLL"] ?: 1
                val pitchCh = paramValues["RCMAP_PITCH"] ?: 2
                val throttleCh = paramValues["RCMAP_THROTTLE"] ?: 3
                val yawCh = paramValues["RCMAP_YAW"] ?: 4

                Log.d(TAG, "✓ RC Mapping: Roll=$rollCh, Pitch=$pitchCh, Throttle=$throttleCh, Yaw=$yawCh")

                // Update channel function assignments
                val updatedChannels = _uiState.value.channels.mapIndexed { index, ch ->
                    val channelNum = index + 1
                    val function = when (channelNum) {
                        rollCh -> "Roll"
                        pitchCh -> "Pitch"
                        throttleCh -> "Throttle"
                        yawCh -> "Yaw"
                        else -> null
                    }
                    ch.copy(isAssignedToFunction = function)
                }

                _uiState.update {
                    it.copy(
                        channels = updatedChannels,
                        rollChannel = rollCh,
                        pitchChannel = pitchCh,
                        throttleChannel = throttleCh,
                        yawChannel = yawCh,
                        calibrationState = RCCalibrationState.Ready(),
                        statusText = "Ready to calibrate",
                        buttonText = "Calibrate Radio"
                    )
                }

                // Start RC_CHANNELS streaming at 10Hz
                rcChannelsHelper.requestRCChannels(10f)
                startRCChannelsListener()

                Log.d(TAG, "========== RC PARAMETERS LOADED ==========")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load RC parameters", e)
                _uiState.update {
                    it.copy(
                        calibrationState = RCCalibrationState.Failed("Failed to load RC parameters: ${e.message}"),
                        statusText = "Error loading parameters"
                    )
                }
            }
        }
    }

    /**
     * STEP 2: Start listening to RC_CHANNELS messages and update UI in real-time.
     */
    private fun startRCChannelsListener() {
        rcChannelsListenerJob?.cancel()

        var firstMessageReceived = false

        rcChannelsListenerJob = viewModelScope.launch {
            rcChannelsHelper.getRCChannelsFlow().collect { rcChannels ->
                // Log first RC_CHANNELS message received
                if (!firstMessageReceived) {
                    firstMessageReceived = true
                    Log.d(TAG, "✓ Remote controller connected - receiving RC_CHANNELS")
                }

                // Extract all 16 channels from RC_CHANNELS message
                val channelValues = rcChannelsHelper.extractChannelValues(rcChannels)

                // Update current values and track min/max during calibration
                val state = _uiState.value.calibrationState
                val updatedChannels = _uiState.value.channels.mapIndexed { index, ch ->
                    val currentValue = channelValues[index]

                    // If capturing min/max, update captured values
                    if (state is RCCalibrationState.CapturingMinMax) {
                        capturedMin[index] = min(capturedMin[index], currentValue)
                        capturedMax[index] = max(capturedMax[index], currentValue)

                        ch.copy(
                            currentValue = currentValue,
                            minValue = capturedMin[index],
                            maxValue = capturedMax[index]
                        )
                    } else {
                        ch.copy(currentValue = currentValue)
                    }
                }

                _uiState.update { it.copy(channels = updatedChannels) }
            }
        }
    }

    /**
     * STEP 3: Start calibration - begin capturing min/max values.
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            Log.e(TAG, "❌ Cannot start calibration - Not connected to drone")
            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "========== STARTING RC CALIBRATION ==========")

            // Initialize min/max tracking
            for (i in 0..15) {
                capturedMin[i] = 2200  // Start high so any real value will be lower
                capturedMax[i] = 800   // Start low so any real value will be higher
            }

            val instruction = "Move all RC sticks and switches to their extreme positions"
            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.CapturingMinMax(instruction),
                    statusText = "Move sticks to extremes...",
                    buttonText = "Click when Done"
                )
            }

            Log.d(TAG, "✓ Capturing min/max values - move all controls to extremes")
        }
    }

    /**
     * STEP 4: Move to center capture phase.
     */
    fun captureCenter() {
        viewModelScope.launch {
            Log.d(TAG, "========== CAPTURING CENTER VALUES ==========")

            // Validate that we captured reasonable min/max values
            var validChannels = 0
            for (i in 0..15) {
                if (capturedMin[i] < 2000 && capturedMax[i] > 1000 && capturedMin[i] < capturedMax[i]) {
                    validChannels++
                }
            }

            if (validChannels < 4) {
                Log.e(TAG, "❌ Not enough valid channels captured (only $validChannels)")

                _uiState.update {
                    it.copy(
                        calibrationState = RCCalibrationState.Failed(
                            "Bad channel data. Please ensure transmitter is on and move all sticks."
                        ),
                        statusText = "Calibration failed",
                        buttonText = "Calibrate Radio"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.CapturingCenter(
                        "Center all sticks and set throttle to minimum, then click 'Save Calibration'"
                    ),
                    statusText = "Center sticks and set throttle down...",
                    buttonText = "Save Calibration"
                )
            }

            Log.d(TAG, "✓ Ready to capture center values")
        }
    }

    /**
     * STEP 5: Capture trim values and save all calibration to vehicle.
     */
    fun saveCalibration() {
        viewModelScope.launch {
            Log.d(TAG, "========== SAVING RC CALIBRATION ==========")

            // Capture current positions as trim/center
            val currentChannels = _uiState.value.channels
            for (i in 0..15) {
                capturedTrim[i] = currentChannels[i].currentValue
            }

            // Validate trim values are within min/max range
            for (i in 0..15) {
                if (capturedMin[i] < capturedMax[i] && capturedMin[i] != 0 && capturedMax[i] != 0) {
                    // Constrain trim to be within min/max
                    if (capturedTrim[i] < capturedMin[i]) capturedTrim[i] = capturedMin[i]
                    if (capturedTrim[i] > capturedMax[i]) capturedTrim[i] = capturedMax[i]
                }
            }

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Saving,
                    statusText = "Saving calibration to vehicle...",
                    buttonText = "Saving..."
                )
            }

            // Save parameters to vehicle
            var successCount = 0
            var failCount = 0

            for (i in 0..15) {
                val channelNum = i + 1

                // Only save channels that have valid data
                if (capturedMin[i] < capturedMax[i] &&
                    capturedMin[i] > 800 && capturedMax[i] < 2200 &&
                    capturedTrim[i] >= capturedMin[i] && capturedTrim[i] <= capturedMax[i]) {

                    Log.d(TAG, "Saving CH$channelNum: MIN=${capturedMin[i]} MAX=${capturedMax[i]} TRIM=${capturedTrim[i]}")

                    val (minSuccess, maxSuccess, trimSuccess) = rcChannelsHelper.saveChannelCalibration(
                        channelNum,
                        capturedMin[i],
                        capturedMax[i],
                        capturedTrim[i],
                        parameterRepository
                    )

                    if (minSuccess) successCount++ else failCount++
                    if (maxSuccess) successCount++ else failCount++
                    if (trimSuccess) successCount++ else failCount++

                } else {
                    Log.d(TAG, "Skipping CH$channelNum - invalid or disconnected (${capturedMin[i]} | ${capturedMax[i]})")
                }
            }

            // Stop RC_CHANNELS streaming
            rcChannelsHelper.stopRCChannels()
            rcChannelsListenerJob?.cancel()

            // Generate summary report
            val summary = buildString {
                appendLine("RC Calibration Complete!")
                appendLine()
                appendLine("Detected radio channel values:")
                appendLine("NOTE: Channels showing 1500±2 are likely not connected")
                appendLine("Normal values are around 1100 | 1900")
                appendLine()
                appendLine("Channel : Min  | Max")
                appendLine("─────────────────────────")

                for (i in 0..15) {
                    val channelNum = i + 1
                    val function = _uiState.value.channels[i].isAssignedToFunction
                    val label = if (function != null) "CH$channelNum ($function)" else "CH$channelNum"

                    if (capturedMin[i] < capturedMax[i] && capturedMin[i] > 800) {
                        appendLine("$label : ${capturedMin[i]} | ${capturedMax[i]}")
                    }
                }

                appendLine()
                appendLine("Saved $successCount parameters successfully")
                if (failCount > 0) {
                    appendLine("Failed to save $failCount parameters")
                }
            }

            Log.d(TAG, summary)

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Success(summary),
                    statusText = "Calibration saved successfully",
                    buttonText = "Done"
                )
            }

            Log.d(TAG, "========== RC CALIBRATION COMPLETE ==========")
        }
    }

    /**
     * Handle button click based on current state.
     */
    fun onButtonClick() {
        when (_uiState.value.calibrationState) {
            is RCCalibrationState.Ready -> startCalibration()
            is RCCalibrationState.CapturingMinMax -> captureCenter()
            is RCCalibrationState.CapturingCenter -> saveCalibration()
            else -> {
                // Do nothing for other states
            }
        }
    }

    /**
     * Reset calibration to initial state.
     */
    fun resetCalibration() {
        viewModelScope.launch {
            rcChannelsListenerJob?.cancel()

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Idle,
                    statusText = "",
                    buttonText = "Calibrate Radio"
                )
            }

            // Reload parameters if connected
            if (_uiState.value.isConnected) {
                loadParameters()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rcChannelsListenerJob?.cancel()

        // Stop RC_CHANNELS streaming when leaving screen
        viewModelScope.launch {
            rcChannelsHelper.stopRCChannels()
        }
    }
}
