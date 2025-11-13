package com.example.pavamanconfiguratorgcs.ui.configurations

/**
 * UI state for the barometer calibration screen
 * Keeps simple fields to minimize UI changes while adding robust backend logic
 */
data class BarometerCalibrationUiState(
    val isConnected: Boolean = false,
    val statusText: String = "",
    val isCalibrating: Boolean = false,
    val progress: Int = 0,
    val isStopped: Boolean = false,
    val isFlatSurface: Boolean = true,
    val isWindGood: Boolean = true
)

