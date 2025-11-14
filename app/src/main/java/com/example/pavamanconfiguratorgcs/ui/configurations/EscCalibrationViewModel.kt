package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavParamType
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for ESC Calibration following MVVM architecture.
 * Manages ESC calibration state, parameters, and calibration flow.
 */
class EscCalibrationViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "EscCalibrationViewModel"

        // Parameter names
        private const val PARAM_MOT_PWM_TYPE = "MOT_PWM_TYPE"
        private const val PARAM_MOT_PWM_MIN = "MOT_PWM_MIN"
        private const val PARAM_MOT_PWM_MAX = "MOT_PWM_MAX"
        private const val PARAM_MOT_SPIN_ARM = "MOT_SPIN_ARM"
        private const val PARAM_MOT_SPIN_MIN = "MOT_SPIN_MIN"
        private const val PARAM_MOT_SPIN_MAX = "MOT_SPIN_MAX"
        private const val PARAM_ESC_CALIBRATION = "ESC_CALIBRATION"

        // ESC Calibration values
        private const val ESC_CAL_DISABLED = 0f
        private const val ESC_CAL_THROTTLE_LOW = 1f
        private const val ESC_CAL_THROTTLE_HIGH = 2f
        private const val ESC_CAL_FULL = 3f
    }

    // UI State
    private val _uiState = MutableStateFlow(EscCalibrationUiState())
    val uiState: StateFlow<EscCalibrationUiState> = _uiState.asStateFlow()

    // Connection state
    val isConnected = telemetryRepository.connectionState
        .map { it is TelemetryRepository.ConnectionState.Connected ||
               it is TelemetryRepository.ConnectionState.HeartbeatVerified }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        Log.d(TAG, "EscCalibrationViewModel initialized")
        loadParameters()
    }

    /**
     * Load current ESC parameters from autopilot.
     */
    fun loadParameters() {
        viewModelScope.launch {
            Log.d(TAG, "Loading ESC parameters")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Request all ESC-related parameters
                val params = listOf(
                    PARAM_MOT_PWM_TYPE,
                    PARAM_MOT_PWM_MIN,
                    PARAM_MOT_PWM_MAX,
                    PARAM_MOT_SPIN_ARM,
                    PARAM_MOT_SPIN_MIN,
                    PARAM_MOT_SPIN_MAX
                )

                params.forEach { paramName ->
                    when (val result = parameterRepository.requestParameter(paramName)) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.d(TAG, "Loaded $paramName = ${result.parameter.value}")
                            updateParameterInState(paramName, result.parameter.value)
                        }
                        is ParameterRepository.ParameterResult.Error -> {
                            Log.e(TAG, "Error loading $paramName: ${result.message}")
                        }
                        is ParameterRepository.ParameterResult.Timeout -> {
                            Log.w(TAG, "Timeout loading $paramName")
                        }
                    }
                }

                _uiState.update { it.copy(isLoading = false) }
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
     * Update a specific parameter value in the UI state.
     */
    private fun updateParameterInState(paramName: String, value: Float) {
        _uiState.update { state ->
            when (paramName) {
                PARAM_MOT_PWM_TYPE -> state.copy(motPwmType = value.toInt())
                PARAM_MOT_PWM_MIN -> state.copy(motPwmMin = value.toInt())
                PARAM_MOT_PWM_MAX -> state.copy(motPwmMax = value.toInt())
                PARAM_MOT_SPIN_ARM -> state.copy(motSpinArm = value)
                PARAM_MOT_SPIN_MIN -> state.copy(motSpinMin = value)
                PARAM_MOT_SPIN_MAX -> state.copy(motSpinMax = value)
                else -> state
            }
        }
    }

    /**
     * Update MOT_PWM_MIN value.
     */
    fun updateMotPwmMin(value: Int) {
        Log.d(TAG, "Updating MOT_PWM_MIN to $value")
        _uiState.update { it.copy(motPwmMin = value) }
    }

    /**
     * Update MOT_PWM_MAX value.
     */
    fun updateMotPwmMax(value: Int) {
        Log.d(TAG, "Updating MOT_PWM_MAX to $value")
        _uiState.update { it.copy(motPwmMax = value) }
    }

    /**
     * Update MOT_SPIN_ARM value.
     */
    fun updateMotSpinArm(value: Float) {
        Log.d(TAG, "Updating MOT_SPIN_ARM to $value")
        _uiState.update { it.copy(motSpinArm = value) }
    }

    /**
     * Update MOT_SPIN_MIN value.
     */
    fun updateMotSpinMin(value: Float) {
        Log.d(TAG, "Updating MOT_SPIN_MIN to $value")
        _uiState.update { it.copy(motSpinMin = value) }
    }

    /**
     * Update MOT_SPIN_MAX value.
     */
    fun updateMotSpinMax(value: Float) {
        Log.d(TAG, "Updating MOT_SPIN_MAX to $value")
        _uiState.update { it.copy(motSpinMax = value) }
    }

    /**
     * Update MOT_PWM_TYPE value.
     */
    fun updateMotPwmType(value: Int) {
        Log.d(TAG, "Updating MOT_PWM_TYPE to $value")
        _uiState.update { it.copy(motPwmType = value) }
    }

    /**
     * Save all modified parameters to the autopilot.
     */
    fun saveParameters() {
        viewModelScope.launch {
            Log.d(TAG, "Saving ESC parameters")
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            try {
                val state = _uiState.value
                var allSuccess = true

                // Save each parameter
                val paramsToSave = listOf(
                    PARAM_MOT_PWM_TYPE to state.motPwmType.toFloat(),
                    PARAM_MOT_PWM_MIN to state.motPwmMin.toFloat(),
                    PARAM_MOT_PWM_MAX to state.motPwmMax.toFloat(),
                    PARAM_MOT_SPIN_ARM to state.motSpinArm,
                    PARAM_MOT_SPIN_MIN to state.motSpinMin,
                    PARAM_MOT_SPIN_MAX to state.motSpinMax
                )

                paramsToSave.forEach { (paramName, value) ->
                    when (val result = parameterRepository.setParameter(paramName, value)) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.d(TAG, "Saved $paramName = $value")
                        }
                        is ParameterRepository.ParameterResult.Error -> {
                            Log.e(TAG, "Error saving $paramName: ${result.message}")
                            allSuccess = false
                        }
                        is ParameterRepository.ParameterResult.Timeout -> {
                            Log.e(TAG, "Timeout saving $paramName")
                            allSuccess = false
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = if (allSuccess) null else "Some parameters failed to save"
                    )
                }

                if (allSuccess) {
                    Log.d(TAG, "All parameters saved successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving parameters", e)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Failed to save parameters: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Start ESC calibration process.
     * This sends ESC_CALIBRATION=3 to the autopilot.
     */
    fun startCalibration() {
        viewModelScope.launch {
            Log.d(TAG, "Starting ESC calibration")
            _uiState.update {
                it.copy(
                    isCalibrating = true,
                    calibrationStep = CalibrationStep.STARTING,
                    errorMessage = null
                )
            }

            try {
                // Send ESC_CALIBRATION = 3 (Full calibration)
                when (val result = parameterRepository.setParameter(
                    PARAM_ESC_CALIBRATION,
                    ESC_CAL_FULL,
                    MavParamType.REAL32,
                    force = true // Always send, even if cached value matches
                )) {
                    is ParameterRepository.ParameterResult.Success -> {
                        Log.d(TAG, "ESC calibration started successfully")
                        _uiState.update {
                            it.copy(
                                calibrationStep = CalibrationStep.IN_PROGRESS,
                                calibrationMessage = "Calibration in progress. Follow the prompts on your autopilot."
                            )
                        }
                    }
                    is ParameterRepository.ParameterResult.Error -> {
                        Log.e(TAG, "Failed to start calibration: ${result.message}")
                        _uiState.update {
                            it.copy(
                                isCalibrating = false,
                                calibrationStep = CalibrationStep.IDLE,
                                errorMessage = "Failed to start calibration: ${result.message}. " +
                                              "Ensure your firmware is AC3.3+ compatible."
                            )
                        }
                    }
                    is ParameterRepository.ParameterResult.Timeout -> {
                        Log.e(TAG, "Calibration start timeout")
                        _uiState.update {
                            it.copy(
                                isCalibrating = false,
                                calibrationStep = CalibrationStep.IDLE,
                                errorMessage = "Calibration timeout. Check connection and firmware version (AC3.3+)."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting calibration", e)
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        calibrationStep = CalibrationStep.IDLE,
                        errorMessage = "Calibration error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Stop/reset calibration.
     */
    fun stopCalibration() {
        viewModelScope.launch {
            Log.d(TAG, "Stopping ESC calibration")

            try {
                // Send ESC_CALIBRATION = 0 (Disabled)
                parameterRepository.setParameter(
                    PARAM_ESC_CALIBRATION,
                    ESC_CAL_DISABLED,
                    MavParamType.REAL32,
                    force = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping calibration", e)
            }

            _uiState.update {
                it.copy(
                    isCalibrating = false,
                    calibrationStep = CalibrationStep.IDLE,
                    calibrationMessage = null
                )
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "EscCalibrationViewModel cleared")
    }
}

/**
 * UI State for ESC Calibration screen.
 */
data class EscCalibrationUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationStep: CalibrationStep = CalibrationStep.IDLE,
    val calibrationMessage: String? = null,
    val errorMessage: String? = null,

    // Motor parameters
    val motPwmType: Int = 0,          // MOT_PWM_TYPE (0-4)
    val motPwmMin: Int = 1000,        // MOT_PWM_MIN (1000-1500 µs)
    val motPwmMax: Int = 2000,        // MOT_PWM_MAX (1500-2200 µs)
    val motSpinArm: Float = 0.10f,    // MOT_SPIN_ARM (0.0-1.0)
    val motSpinMin: Float = 0.15f,    // MOT_SPIN_MIN (0.0-1.0)
    val motSpinMax: Float = 0.95f     // MOT_SPIN_MAX (0.0-1.0)
)

/**
 * Calibration process steps.
 */
enum class CalibrationStep {
    IDLE,
    STARTING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * PWM types for MOT_PWM_TYPE parameter.
 */
enum class PwmType(val value: Int, val displayName: String) {
    NORMAL(0, "Normal PWM"),
    ONESHOT(1, "OneShot"),
    ONESHOT125(2, "OneShot125"),
    BRUSHED(3, "Brushed"),
    DSHOT150(4, "DShot150"),
    DSHOT300(5, "DShot300"),
    DSHOT600(6, "DShot600"),
    DSHOT1200(7, "DShot1200");

    companion object {
        fun fromValue(value: Int): PwmType {
            return entries.find { it.value == value } ?: NORMAL
        }

        fun getDisplayNames(): List<String> {
            return entries.map { it.displayName }
        }
    }
}
