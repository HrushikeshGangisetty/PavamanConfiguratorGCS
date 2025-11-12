package com.example.pavamanconfiguratorgcs.ui.configurations

/**
 * State of the RC calibration process.
 * Follows ArduPilot/MissionPlanner protocol for RC calibration.
 */
sealed class RCCalibrationState {
    object Idle : RCCalibrationState()
    object LoadingParameters : RCCalibrationState()
    data class Ready(val instruction: String = "Click 'Calibrate Radio' to begin") : RCCalibrationState()
    data class CapturingMinMax(val instruction: String = "Move all sticks and switches to their extreme positions") : RCCalibrationState()
    data class CapturingCenter(val instruction: String = "Center all sticks and set throttle to minimum") : RCCalibrationState()
    object Saving : RCCalibrationState()
    data class Success(val summary: String) : RCCalibrationState()
    data class Failed(val errorMessage: String) : RCCalibrationState()
}

/**
 * Represents current RC channel data and calibration values.
 * Supports up to 16 RC channels as per MAVLink RC_CHANNELS message.
 */
data class RCChannelData(
    val channelNumber: Int,
    val currentValue: Int = 1500,
    val minValue: Int = 1500,
    val maxValue: Int = 1500,
    val trimValue: Int = 1500,
    val isReversed: Boolean = false,
    val isAssignedToFunction: String? = null // e.g., "Roll", "Pitch", "Throttle", "Yaw"
)

/**
 * Complete UI state for RC Calibration screen.
 */
data class RCCalibrationUiState(
    val isConnected: Boolean = false,
    val calibrationState: RCCalibrationState = RCCalibrationState.Idle,
    val channels: List<RCChannelData> = (1..16).map { RCChannelData(it) },
    val rollChannel: Int = 1,
    val pitchChannel: Int = 2,
    val throttleChannel: Int = 3,
    val yawChannel: Int = 4,
    val statusText: String = "",
    val buttonText: String = "Calibrate Radio"
)

