package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavParamType
import com.divpundir.mavlink.definitions.common.RcChannels
import com.example.pavamanconfiguratorgcs.data.ParameterRepository
import com.example.pavamanconfiguratorgcs.data.models.FirmwareType
import com.example.pavamanconfiguratorgcs.data.models.FlightMode
import com.example.pavamanconfiguratorgcs.data.models.FlightModeConfiguration
import com.example.pavamanconfiguratorgcs.data.models.FlightModeProvider
import com.example.pavamanconfiguratorgcs.data.models.FlightModeSlot
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Flight Modes Configuration following MVVM architecture.
 * Manages flight mode slots, simple modes, and real-time switch monitoring.
 */
class FlightModesViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FLIGHTMODE"

        // Parameter names - ArduCopter/ArduPlane
        private const val PARAM_FLTMODE_PREFIX = "FLTMODE"
        private const val PARAM_FLTMODE_CH = "FLTMODE_CH"
        private const val PARAM_SIMPLE = "SIMPLE"
        private const val PARAM_SUPER_SIMPLE = "SUPER_SIMPLE"

        // Parameter names - ArduRover
        private const val PARAM_MODE_PREFIX = "MODE"
        private const val PARAM_MODE_CH = "MODE_CH"

        // Parameter names - PX4
        private const val PARAM_COM_FLTMODE_PREFIX = "COM_FLTMODE"

        // Update interval for real-time monitoring
        private const val UPDATE_INTERVAL_MS = 100L
    }

    // UI State
    private val _uiState = MutableStateFlow(FlightModesUiState())
    val uiState: StateFlow<FlightModesUiState> = _uiState.asStateFlow()

    // Connection state
    val isConnected = telemetryRepository.connectionState
        .map { it is TelemetryRepository.ConnectionState.Connected ||
               it is TelemetryRepository.ConnectionState.HeartbeatVerified }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var updateJob: Job? = null

    init {
        Log.d(TAG, "FlightModesViewModel initialized")
        detectFirmwareAndLoadParameters()
        startRealtimeUpdates()
    }

    /**
     * Detect firmware type and load appropriate parameters.
     */
    private fun detectFirmwareAndLoadParameters() {
        viewModelScope.launch {
            Log.d(TAG, "Detecting firmware type")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // For now, default to ArduCopter - can be enhanced with actual detection
                // In a real implementation, you'd read from heartbeat message
                val firmwareType = FirmwareType.ARDUPILOT_COPTER

                Log.d(TAG, "Detected firmware: $firmwareType")

                val availableModes = FlightModeProvider.getModesForFirmware(firmwareType)

                _uiState.update {
                    it.copy(
                        firmwareType = firmwareType,
                        availableModes = availableModes,
                        showSimpleModes = firmwareType == FirmwareType.ARDUPILOT_COPTER
                    )
                }

                loadParameters()
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting firmware", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to detect firmware: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load current flight mode parameters from autopilot.
     */
    private fun loadParameters() {
        viewModelScope.launch {
            Log.d(TAG, "Loading flight mode parameters")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val currentState = _uiState.value
                val paramPrefix = getParameterPrefix(currentState.firmwareType)
                val modeChannelParam = getModeChannelParam(currentState.firmwareType)

                // Load switch channel
                loadSwitchChannel(modeChannelParam)

                // Load flight mode slots
                val slots = mutableListOf<FlightModeSlot>()
                for (i in 1..6) {
                    val paramName = "$paramPrefix$i"
                    when (val result = parameterRepository.requestParameter(paramName)) {
                        is ParameterRepository.ParameterResult.Success -> {
                            val modeValue = result.parameter.value.toInt()
                            Log.d(TAG, "Loaded $paramName = $modeValue")

                            slots.add(FlightModeSlot(slot = i, mode = modeValue))
                        }
                        is ParameterRepository.ParameterResult.Error -> {
                            Log.e(TAG, "Error loading $paramName: ${result.message}")
                            slots.add(FlightModeSlot(slot = i, mode = 0))
                        }
                        is ParameterRepository.ParameterResult.Timeout -> {
                            Log.w(TAG, "Timeout loading $paramName")
                            slots.add(FlightModeSlot(slot = i, mode = 0))
                        }
                    }
                }

                // Load simple mode settings (ArduCopter only)
                if (currentState.firmwareType == FirmwareType.ARDUPILOT_COPTER) {
                    loadSimpleModeSettings(slots)
                }

                _uiState.update {
                    it.copy(
                        configuration = it.configuration.copy(slots = slots),
                        isLoading = false
                    )
                }

                Log.d(TAG, "Parameters loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading parameters", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load parameters: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Load switch channel parameter.
     */
    private suspend fun loadSwitchChannel(paramName: String) {
        when (val result = parameterRepository.requestParameter(paramName)) {
            is ParameterRepository.ParameterResult.Success -> {
                val channel = result.parameter.value.toInt()
                Log.d(TAG, "Switch channel: $channel")
                _uiState.update {
                    it.copy(
                        configuration = it.configuration.copy(switchChannel = channel)
                    )
                }
            }
            else -> {
                Log.w(TAG, "Could not load switch channel, using default")
            }
        }
    }

    /**
     * Load simple mode bitmask and update slots.
     */
    private suspend fun loadSimpleModeSettings(slots: MutableList<FlightModeSlot>) {
        // Load SIMPLE parameter
        when (val result = parameterRepository.requestParameter(PARAM_SIMPLE)) {
            is ParameterRepository.ParameterResult.Success -> {
                val simpleValue = result.parameter.value.toInt()
                Log.d(TAG, "SIMPLE bitmask: $simpleValue")

                // Extract bits for each mode
                for (i in 0..5) {
                    val isSimpleEnabled = ((simpleValue shr i) and 1) == 1
                    slots[i] = slots[i].copy(simpleEnabled = isSimpleEnabled)
                }
            }
            else -> Log.w(TAG, "Could not load SIMPLE parameter")
        }

        // Load SUPER_SIMPLE parameter
        when (val result = parameterRepository.requestParameter(PARAM_SUPER_SIMPLE)) {
            is ParameterRepository.ParameterResult.Success -> {
                val superSimpleValue = result.parameter.value.toInt()
                Log.d(TAG, "SUPER_SIMPLE bitmask: $superSimpleValue")

                // Extract bits for each mode
                for (i in 0..5) {
                    val isSuperSimpleEnabled = ((superSimpleValue shr i) and 1) == 1
                    slots[i] = slots[i].copy(superSimpleEnabled = isSuperSimpleEnabled)
                }
            }
            else -> Log.w(TAG, "Could not load SUPER_SIMPLE parameter")
        }
    }

    /**
     * Start real-time updates for switch PWM and current mode.
     */
    private fun startRealtimeUpdates() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            // Monitor RC_CHANNELS messages
            telemetryRepository.mavFrame
                .filter { it.message is RcChannels }
                .map { it.message as RcChannels }
                .collect { rcChannels ->
                    updateCurrentMode(rcChannels)
                }
        }
    }

    /**
     * Update current mode based on RC channel input.
     */
    private fun updateCurrentMode(rcChannels: RcChannels) {
        val currentState = _uiState.value
        val switchChannel = currentState.configuration.switchChannel

        // Get PWM value from appropriate channel
        val pwm = when (switchChannel) {
            5 -> rcChannels.chan5Raw.toInt()
            6 -> rcChannels.chan6Raw.toInt()
            7 -> rcChannels.chan7Raw.toInt()
            8 -> rcChannels.chan8Raw.toInt()
            9 -> rcChannels.chan9Raw.toInt()
            10 -> rcChannels.chan10Raw.toInt()
            11 -> rcChannels.chan11Raw.toInt()
            12 -> rcChannels.chan12Raw.toInt()
            13 -> rcChannels.chan13Raw.toInt()
            14 -> rcChannels.chan14Raw.toInt()
            15 -> rcChannels.chan15Raw.toInt()
            16 -> rcChannels.chan16Raw.toInt()
            else -> 1500
        }

        // Determine mode slot from PWM value
        val modeSlot = readSwitchPosition(pwm)

        _uiState.update {
            it.copy(
                configuration = it.configuration.copy(
                    switchPwm = pwm,
                    currentModeIndex = modeSlot
                )
            )
        }
    }

    /**
     * Convert PWM value to mode slot (0-5).
     * Based on ArduPilot switch mapping.
     */
    private fun readSwitchPosition(pwm: Int): Int {
        return when {
            pwm < 1230 -> 0
            pwm < 1360 -> 1
            pwm < 1490 -> 2
            pwm < 1620 -> 3
            pwm < 1749 -> 4
            else -> 5
        }
    }

    /**
     * Update flight mode for a specific slot.
     */
    fun updateFlightMode(slotIndex: Int, modeKey: Int) {
        Log.d(TAG, "Updating slot ${slotIndex + 1} to mode $modeKey")

        // Validate mode key is within valid range
        if (modeKey < 0 || modeKey > 63) {
            Log.w(TAG, "Invalid mode key: $modeKey. Must be 0-63.")
            _uiState.update {
                it.copy(errorMessage = "Invalid mode selection. Please try again.")
            }
            return
        }

        _uiState.update { state ->
            val updatedSlots = state.configuration.slots.toMutableList()
            updatedSlots[slotIndex] = updatedSlots[slotIndex].copy(mode = modeKey)

            state.copy(
                configuration = state.configuration.copy(slots = updatedSlots),
                hasUnsavedChanges = true,
                errorMessage = null  // Clear any previous errors
            )
        }
    }

    /**
     * Update simple mode for a specific slot.
     */
    fun updateSimpleMode(slotIndex: Int, enabled: Boolean) {
        Log.d(TAG, "Updating slot ${slotIndex + 1} simple mode to $enabled")

        _uiState.update { state ->
            val updatedSlots = state.configuration.slots.toMutableList()
            updatedSlots[slotIndex] = updatedSlots[slotIndex].copy(simpleEnabled = enabled)

            state.copy(
                configuration = state.configuration.copy(slots = updatedSlots),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Update super simple mode for a specific slot.
     */
    fun updateSuperSimpleMode(slotIndex: Int, enabled: Boolean) {
        Log.d(TAG, "Updating slot ${slotIndex + 1} super simple mode to $enabled")

        _uiState.update { state ->
            val updatedSlots = state.configuration.slots.toMutableList()
            updatedSlots[slotIndex] = updatedSlots[slotIndex].copy(superSimpleEnabled = enabled)

            state.copy(
                configuration = state.configuration.copy(slots = updatedSlots),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Save all flight mode configuration to autopilot.
     */
    fun saveFlightModes() {
        viewModelScope.launch {
            Log.d(TAG, "Saving flight mode configuration")
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            try {
                // 1. Validate connection
                if (telemetryRepository.connection == null) {
                    throw Exception("Aircraft not connected. Please establish connection first.")
                }

                if (!telemetryRepository.fcuDetected.value) {
                    throw Exception("Flight controller not detected. Check MAVLink connection.")
                }

                val currentState = _uiState.value
                val paramPrefix = getParameterPrefix(currentState.firmwareType)

                // 2. Validate mode values are in range
                for (slot in currentState.configuration.slots) {
                    if (slot.mode < 0 || slot.mode > 63) {
                        throw Exception("Invalid mode value ${slot.mode} for slot ${slot.slot}. Must be 0-63.")
                    }
                }

                Log.d(TAG, "Validation passed. Saving ${currentState.configuration.slots.size} flight modes...")

                // 3. Save each flight mode slot with detailed error tracking
                var allSuccess = true
                val failedParams = mutableListOf<String>()

                for (slot in currentState.configuration.slots) {
                    val paramName = "$paramPrefix${slot.slot}"

                    try {
                        Log.d(TAG, "Setting $paramName = ${slot.mode}")

                        val result = parameterRepository.setParameter(
                            paramName = paramName,
                            value = slot.mode.toFloat(),
                            paramType = MavParamType.INT8
                        )

                        when (result) {
                            is ParameterRepository.ParameterResult.Success -> {
                                Log.d(TAG, "✓ Saved $paramName = ${slot.mode}")
                            }
                            is ParameterRepository.ParameterResult.Error -> {
                                Log.e(TAG, "✗ Failed to save $paramName: ${result.message}")
                                failedParams.add("$paramName: ${result.message}")
                                allSuccess = false
                            }
                            is ParameterRepository.ParameterResult.Timeout -> {
                                Log.e(TAG, "✗ Timeout saving $paramName (no response from aircraft)")
                                failedParams.add("$paramName: Timeout")
                                allSuccess = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Exception saving $paramName", e)
                        failedParams.add("$paramName: ${e.message}")
                        allSuccess = false
                    }
                }

                // 4. Save simple modes (ArduCopter only)
                if (currentState.firmwareType == FirmwareType.ARDUPILOT_COPTER) {
                    try {
                        val simpleResult = saveSimpleModes(currentState.configuration.slots)
                        if (!simpleResult) {
                            failedParams.add("SIMPLE/SUPER_SIMPLE parameters")
                            allSuccess = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving simple modes", e)
                        failedParams.add("SIMPLE modes: ${e.message}")
                        allSuccess = false
                    }
                }

                // 5. Update UI based on results
                if (allSuccess) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            successMessage = "Flight modes saved successfully! Configuration will be active on next mode switch."
                        )
                    }
                    Log.d(TAG, "✓ All flight modes saved successfully")
                } else {
                    val errorDetails = failedParams.joinToString("\n• ")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Failed to save some parameters:\n• $errorDetails\n\nPlease check connection and retry."
                        )
                    }
                    Log.e(TAG, "✗ Some parameters failed to save: $errorDetails")
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "State error during save", e)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Configuration error: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error saving flight modes", e)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Error: ${e.message ?: "Unknown error occurred"}\n\nPlease check connection and try again."
                    )
                }
            } finally {
                // Always ensure saving state is cleared
                if (_uiState.value.isSaving) {
                    _uiState.update { it.copy(isSaving = false) }
                }
                Log.d(TAG, "Save operation completed")
            }
        }
    }

    /**
     * Save simple and super simple mode bitmasks.
     * Returns true if successful, false otherwise.
     */
    private suspend fun saveSimpleModes(slots: List<FlightModeSlot>): Boolean {
        // Calculate SIMPLE bitmask
        var simpleValue = 0
        var superSimpleValue = 0

        for (i in slots.indices) {
            if (slots[i].simpleEnabled) {
                simpleValue = simpleValue or (1 shl i)
            }
            if (slots[i].superSimpleEnabled) {
                superSimpleValue = superSimpleValue or (1 shl i)
            }
        }

        // Validate bitmask range (0-63 for 6 bits)
        if (simpleValue > 63 || superSimpleValue > 63) {
            Log.e(TAG, "Invalid bitmask values: SIMPLE=$simpleValue, SUPER_SIMPLE=$superSimpleValue")
            return false
        }

        Log.d(TAG, "Saving SIMPLE bitmask: $simpleValue (binary: ${simpleValue.toString(2).padStart(6, '0')})")
        val simpleResult = parameterRepository.setParameter(
            paramName = PARAM_SIMPLE,
            value = simpleValue.toFloat(),
            paramType = MavParamType.INT8
        )

        if (simpleResult !is ParameterRepository.ParameterResult.Success) {
            Log.e(TAG, "Failed to save SIMPLE parameter")
            return false
        }

        Log.d(TAG, "Saving SUPER_SIMPLE bitmask: $superSimpleValue (binary: ${superSimpleValue.toString(2).padStart(6, '0')})")
        val superSimpleResult = parameterRepository.setParameter(
            paramName = PARAM_SUPER_SIMPLE,
            value = superSimpleValue.toFloat(),
            paramType = MavParamType.INT8
        )

        if (superSimpleResult !is ParameterRepository.ParameterResult.Success) {
            Log.e(TAG, "Failed to save SUPER_SIMPLE parameter")
            return false
        }

        return true
    }

    /**
     * Clear success/error messages.
     */
    fun clearMessages() {
        _uiState.update {
            it.copy(successMessage = null, errorMessage = null)
        }
    }

    /**
     * Get parameter prefix based on firmware type.
     */
    private fun getParameterPrefix(firmwareType: FirmwareType): String {
        return when (firmwareType) {
            FirmwareType.ARDUPILOT_ROVER -> PARAM_MODE_PREFIX
            FirmwareType.PX4 -> PARAM_COM_FLTMODE_PREFIX
            else -> PARAM_FLTMODE_PREFIX
        }
    }

    /**
     * Get mode channel parameter name based on firmware type.
     */
    private fun getModeChannelParam(firmwareType: FirmwareType): String {
        return when (firmwareType) {
            FirmwareType.ARDUPILOT_ROVER -> PARAM_MODE_CH
            else -> PARAM_FLTMODE_CH
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateJob?.cancel()
        Log.d(TAG, "FlightModesViewModel cleared")
    }
}

/**
 * UI State for Flight Modes screen.
 */
data class FlightModesUiState(
    val firmwareType: FirmwareType = FirmwareType.ARDUPILOT_COPTER,
    val availableModes: List<FlightMode> = emptyList(),
    val configuration: FlightModeConfiguration = FlightModeConfiguration(),
    val showSimpleModes: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
