package com.example.pavamanconfiguratorgcs.ui.configurations

/**
 * Enum representing the different orientations for accelerometer calibration.
 * These correspond to ACCELCAL_VEHICLE_POS enum values from ArduPilot.
 *
 * ArduPilot ACCELCAL_VEHICLE_POS enum values:
 * - LEVEL = 1
 * - LEFT = 2
 * - RIGHT = 3
 * - NOSEDOWN = 4
 * - NOSEUP = 5
 * - BACK = 6
 */
enum class AccelCalibrationPosition(val paramValue: Int, val instruction: String) {
    LEVEL(1, "Place vehicle level and press Next"),
    LEFT(2, "Place vehicle on its LEFT side and press Next"),
    RIGHT(3, "Place vehicle on its RIGHT side and press Next"),
    NOSEDOWN(4, "Place vehicle nose DOWN and press Next"),
    NOSEUP(5, "Place vehicle nose UP and press Next"),
    BACK(6, "Place vehicle on its BACK and press Next");

    companion object {
        /**
         * Get position from param value (from COMMAND_LONG param1)
         */
        fun fromParamValue(value: Int): AccelCalibrationPosition? = entries.find { it.paramValue == value }

        fun fromIndex(index: Int): AccelCalibrationPosition? = entries.getOrNull(index)

        /**
         * Parse position from ArduPilot STATUSTEXT prompt
         */
        fun fromStatusText(text: String): AccelCalibrationPosition? {
            val lower = text.lowercase()
            return when {
                lower.contains("level") -> LEVEL
                lower.contains("left") -> LEFT
                lower.contains("right") -> RIGHT
                lower.contains("nose") && (lower.contains("down") || lower.contains("forward")) -> NOSEDOWN
                lower.contains("nose") && lower.contains("up") -> NOSEUP
                lower.contains("back") || lower.contains("upside down") -> BACK
                else -> null
            }
        }
    }
}

/**
 * State of the calibration process following MissionPlanner's protocol.
 */
sealed class IMUCalibrationState {
    object Idle : IMUCalibrationState()
    object Initiating : IMUCalibrationState()
    data class AwaitingUserInput(
        val position: AccelCalibrationPosition,
        val instruction: String
    ) : IMUCalibrationState()
    data class ProcessingPosition(val position: AccelCalibrationPosition) : IMUCalibrationState()
    data class Success(val message: String) : IMUCalibrationState()
    data class Failed(val errorMessage: String) : IMUCalibrationState()
    object Cancelled : IMUCalibrationState()
}

/**
 * UI state for the calibration screen
 */
data class IMUCalibrationUiState(
    val calibrationState: IMUCalibrationState = IMUCalibrationState.Idle,
    val statusText: String = "",
    val currentPositionIndex: Int = 0,
    val totalPositions: Int = 6,
    val isConnected: Boolean = false,
    val showCancelDialog: Boolean = false,
    val buttonText: String = "Start Calibration"
)

