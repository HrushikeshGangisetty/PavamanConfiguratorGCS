package com.example.pavamanconfiguratorgcs.ui.configurations

/**
 * State of the compass calibration process.
 * Follows ArduPilot/MissionPlanner protocol using MAVLink commands.
 */
sealed class CompassCalibrationState {
    object Idle : CompassCalibrationState()
    object Starting : CompassCalibrationState()
    data class InProgress(
        val currentInstruction: String = "Rotate vehicle slowly - point each side down towards earth"
    ) : CompassCalibrationState()
    data class Success(
        val message: String,
        val compassReports: List<CompassReport> = emptyList()
    ) : CompassCalibrationState()
    data class Failed(val errorMessage: String) : CompassCalibrationState()
    object Cancelled : CompassCalibrationState()
}

/**
 * Represents calibration progress for a single compass.
 * Mirrors MAG_CAL_PROGRESS MAVLink message data.
 */
data class CompassProgress(
    val compassId: UByte,
    val calMask: UByte,
    val calStatus: String,
    val attempt: UByte,
    val completionPct: UByte,
    val completionMask: List<UByte>,
    val directionX: Float,
    val directionY: Float,
    val directionZ: Float
)

/**
 * Represents calibration report for a single compass.
 * Mirrors MAG_CAL_REPORT MAVLink message data.
 */
data class CompassReport(
    val compassId: UByte,
    val calStatus: String,
    val autosaved: UByte,
    val fitness: Float,
    val ofsX: Float,
    val ofsY: Float,
    val ofsZ: Float,
    val diagX: Float,
    val diagY: Float,
    val diagZ: Float,
    val offdiagX: Float,
    val offdiagY: Float,
    val offdiagZ: Float,
    val orientationConfidence: Float,
    val oldOrientation: String,
    val newOrientation: String,
    val scaleFactor: Float
)

/**
 * UI state for the compass calibration screen.
 */
data class CompassCalibrationUiState(
    val calibrationState: CompassCalibrationState = CompassCalibrationState.Idle,
    val statusText: String = "",
    val compassProgress: Map<Int, CompassProgress> = emptyMap(),
    val compassReports: Map<Int, CompassReport> = emptyMap(),
    val isConnected: Boolean = false,
    val showCancelDialog: Boolean = false,
    val showAcceptDialog: Boolean = false,
    val overallProgress: Int = 0,
    val calibrationComplete: Boolean = false
)

