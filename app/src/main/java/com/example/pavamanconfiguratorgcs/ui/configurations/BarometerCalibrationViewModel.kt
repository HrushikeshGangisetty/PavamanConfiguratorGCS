package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for Barometer Calibration
 *
 * Protocol Flow:
 * 1. Send MAV_CMD_PREFLIGHT_CALIBRATION with param3=1 (barometer calibration)
 * 2. Listen to STATUSTEXT messages for calibration progress
 * 3. Wait for success/failure indication
 */
class BarometerCalibrationViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BaroCalVM"
        private const val MAV_CMD_PREFLIGHT_CALIBRATION: UInt = 241u
        private const val ACK_TIMEOUT_MS = 4000L
        private const val MAX_RETRIES = 1
        private const val FINAL_OUTCOME_TIMEOUT_MS = 10000L
    }

    private val _uiState = MutableStateFlow(BarometerCalibrationUiState())
    val uiState: StateFlow<BarometerCalibrationUiState> = _uiState.asStateFlow()

    private var statusJob: Job? = null

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
     * Check pre-flight conditions
     */
    fun checkConditions(flatSurface: Boolean, windGood: Boolean) {
        _uiState.update { it.copy(isFlatSurface = flatSurface, isWindGood = windGood) }
    }

    /**
     * Start barometer calibration
     */
    fun startCalibration() {
        val state = _uiState.value

        // Validate environment conditions with clear messaging
        if (!state.isFlatSurface && !state.isWindGood) {
            val message = "Place the drone on a flat surface. Wind condition is not good. It is better to stop flying and calibrating the drone."
            _uiState.update { it.copy(statusText = message) }
            return
        } else if (!state.isFlatSurface) {
            val message = "Place the drone on a flat surface."
            _uiState.update { it.copy(statusText = message) }
            return
        } else if (!state.isWindGood) {
            val message = "Wind condition is not good. It is better to stop flying and calibrating the drone."
            _uiState.update { it.copy(statusText = message) }
            return
        }

        if (!_uiState.value.isConnected) {
            _uiState.update { it.copy(statusText = "Please connect to the drone first") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusText = "Starting barometer calibration...",
                    isCalibrating = true,
                    progress = 0,
                    isStopped = false
                )
            }

            // Start listening to STATUSTEXT messages relevant to barometer calibration
            startStatusListener()

            try {
                var started = false
                var lastAckText: String? = null
                var lastAckResult: UInt? = null

                repeat(MAX_RETRIES + 1) { attempt ->
                    // Send MAV_CMD_PREFLIGHT_CALIBRATION with barometer flag (param3 = 1)
                    telemetryRepository.sendCommand(
                        commandId = MAV_CMD_PREFLIGHT_CALIBRATION,
                        param1 = 0f, // gyro
                        param2 = 0f, // mag
                        param3 = 1f, // baro
                        param4 = 0f, // radio
                        param5 = 0f, // accel
                        param6 = 0f, // esc
                        param7 = 0f
                    )

                    Log.d(TAG, "Sent PREFLIGHT_CALIBRATION (baro) attempt ${attempt + 1}")

                    val ack = telemetryRepository.awaitCommandAck(MAV_CMD_PREFLIGHT_CALIBRATION, ACK_TIMEOUT_MS)
                    val result = ack?.result?.value
                    lastAckResult = result
                    lastAckText = ack?.result?.entry?.name ?: result?.toString()
                    val ok = (result == 0u /* ACCEPTED */) || (result == 5u /* IN_PROGRESS */)
                    if (ok) {
                        started = true
                        return@repeat
                    } else if (ack != null) {
                        // Received a non-accepted ACK (e.g. temporarily rejected / denied)
                        // Do not immediately stop listening to STATUSTEXT; allow autopilot to still report final outcome.
                        started = false
                        return@repeat
                    }
                    // ack == null -> retry
                }

                if (!started) {
                    if (lastAckResult != null) {
                        // We received an ACK but it was not accepted; wait for STATUSTEXT outcome before failing.
                        _uiState.update {
                            it.copy(
                                statusText = "Start returned: ${lastAckText ?: "ACK"}. Waiting for status updates...",
                                isCalibrating = true,
                                progress = 5
                            )
                        }

                        val success = awaitFinalOutcome(FINAL_OUTCOME_TIMEOUT_MS)
                        if (success == true) {
                            _uiState.update {
                                it.copy(
                                    statusText = "Barometer calibration successful",
                                    isCalibrating = false,
                                    progress = 100
                                )
                            }
                        } else if (success == false) {
                            _uiState.update {
                                it.copy(
                                    statusText = "Barometer calibration failed",
                                    isCalibrating = false
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    statusText = "No explicit success received after ACK (${lastAckText ?: "unknown"}). Assuming completion if STATUSTEXT not received.",
                                    isCalibrating = false,
                                    progress = 100
                                )
                            }
                        }

                        stopStatusListener()
                        return@launch
                    } else {
                        // No ACK at all -> fail
                        _uiState.update {
                            it.copy(
                                isCalibrating = false,
                                statusText = "Failed to start barometer calibration: No ACK"
                            )
                        }
                        stopStatusListener()
                        return@launch
                    }
                }

                _uiState.update { it.copy(statusText = "Calibrating barometer...", progress = 10) }

                // Await final outcome from STATUSTEXT
                val success = awaitFinalOutcome(FINAL_OUTCOME_TIMEOUT_MS)
                if (success == true) {
                    _uiState.update {
                        it.copy(
                            statusText = "Barometer calibration successful",
                            isCalibrating = false,
                            progress = 100
                        )
                    }
                } else if (success == false) {
                    _uiState.update {
                        it.copy(
                            statusText = "Barometer calibration failed",
                            isCalibrating = false
                        )
                    }
                } else {
                    // Timeout: assume completion but inform user no explicit success was received
                    _uiState.update {
                        it.copy(
                            statusText = "Assuming barometer calibration completed (no explicit success received)",
                            isCalibrating = false,
                            progress = 100
                        )
                    }
                }

                stopStatusListener()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusText = "Error: ${e.message}",
                        isCalibrating = false
                    )
                }
                stopStatusListener()
            }
        }
    }

    /**
     * Stop calibration
     */
    fun stopCalibration() {
        viewModelScope.launch {
            // No dedicated cancel for baro; send neutral PREFLIGHT_CALIBRATION
            try {
                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_PREFLIGHT_CALIBRATION,
                    param1 = 0f, param2 = 0f, param3 = 0f, param4 = 0f,
                    param5 = 0f, param6 = 0f, param7 = 0f
                )
            } catch (_: Exception) { /* ignore */ }

            _uiState.update {
                it.copy(
                    statusText = "Calibration stopped.",
                    isStopped = true,
                    isCalibrating = false
                )
            }
            stopStatusListener()
        }
    }

    /**
     * Start listening to STATUSTEXT messages
     */
    private fun startStatusListener() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            telemetryRepository.calibrationStatus.collect { text ->
                if (text.isBlank()) return@collect

                val lower = text.lowercase()
                val relevant = listOf("baro", "barometer", "pressure", "calib").any { lower.contains(it) }
                if (!relevant) return@collect

                _uiState.update { it.copy(statusText = text) }

                // Treat any clear success indicator as completion
                if (lower.contains("fail")) {
                    _uiState.update { it.copy(isCalibrating = false) }
                } else if (lower.contains("success") || lower.contains("complete") ||
                           lower.contains("completed") || lower.contains("done")) {
                    _uiState.update { it.copy(progress = 100, isCalibrating = false) }
                }
            }
        }
    }

    /**
     * Stop listening to STATUSTEXT messages
     */
    private fun stopStatusListener() {
        statusJob?.cancel()
        statusJob = null
    }

    /**
     * Await final outcome from STATUSTEXT
     */
    private suspend fun awaitFinalOutcome(timeoutMs: Long): Boolean? = withTimeoutOrNull(timeoutMs) {
        telemetryRepository.calibrationStatus
            .mapNotNull { it }
            .first { t ->
                val l = t.lowercase()
                // Prefer barometer mentions but accept generic calibration outcome keywords
                (l.contains("baro") || l.contains("barometer") || l.contains("pressure") || l.contains("calib")) &&
                        (l.contains("success") || l.contains("fail") || l.contains("complete") ||
                         l.contains("completed") || l.contains("done"))
            }
            .let { finalTxt ->
                val l = finalTxt.lowercase()
                (l.contains("success") || l.contains("complete") || l.contains("completed") || l.contains("done"))
            }
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusListener()
    }
}

