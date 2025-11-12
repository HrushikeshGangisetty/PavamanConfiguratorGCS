package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

/**
 * ViewModel for ArduPilot Compass (Magnetometer) Calibration.
 *
 * Implements the MissionPlanner/ArduPilot compass calibration protocol:
 *
 * STEP 1: Start Calibration
 *   - Send MAV_CMD_DO_START_MAG_CAL (42424) with parameters (0, 1, 1, 0, 0, 0, 0)
 *   - Wait for COMMAND_ACK (MAV_RESULT_ACCEPTED or MAV_RESULT_IN_PROGRESS)
 *   - Subscribe to MAG_CAL_PROGRESS and MAG_CAL_REPORT messages
 *
 * STEP 2: Monitor Progress
 *   - Receive MAG_CAL_PROGRESS messages continuously
 *   - Update UI with progress bars for each compass
 *
 * STEP 3: Receive Final Report
 *   - Receive MAG_CAL_REPORT messages when calibration completes
 *   - Check fitness metrics and calibration status
 *
 * STEP 4: Accept or Cancel
 *   - If user accepts: Send MAV_CMD_DO_ACCEPT_MAG_CAL (42425)
 *   - If user cancels: Send MAV_CMD_DO_CANCEL_MAG_CAL (42426)
 *
 * STEP 5: Reboot
 *   - Prompt user to reboot autopilot to apply new offsets
 */
class CompassCalibrationViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CompassCalVM"

        // MAVLink command IDs (as per ArduPilot specification)
        private const val MAV_CMD_DO_START_MAG_CAL = 42424u
        private const val MAV_CMD_DO_ACCEPT_MAG_CAL = 42425u
        private const val MAV_CMD_DO_CANCEL_MAG_CAL = 42426u

        // Timeout for COMMAND_ACK
        private const val ACK_TIMEOUT_MS = 5000L
        private const val PROGRESS_TIMEOUT_MS = 7000L
    }

    private val _uiState = MutableStateFlow(CompassCalibrationUiState())
    val uiState: StateFlow<CompassCalibrationUiState> = _uiState.asStateFlow()

    private var progressListenerJob: Job? = null
    private var reportListenerJob: Job? = null
    private var statusTextListenerJob: Job? = null

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
            }
        }
    }

    /**
     * STEP 1: Start compass calibration using MAV_CMD_DO_START_MAG_CAL.
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            Log.e(TAG, "❌ Cannot start calibration - Not connected to drone")
            _uiState.update {
                it.copy(
                    calibrationState = CompassCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "═══════════════════════════════════════════════════════")
            Log.d(TAG, "         COMPASS CALIBRATION STARTING")
            Log.d(TAG, "═══════════════════════════════════════════════════════")

            _uiState.update {
                it.copy(
                    calibrationState = CompassCalibrationState.Starting,
                    statusText = "Starting compass calibration...",
                    compassProgress = emptyMap(),
                    compassReports = emptyMap(),
                    overallProgress = 0,
                    calibrationComplete = false
                )
            }

            // Request MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from autopilot
            try {
                Log.d(TAG, "→ Requesting message streaming from autopilot...")
                telemetryRepository.requestMagCalMessages(hz = 10f)
                Log.d(TAG, "✓ Message streaming requested successfully")
                delay(200)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to request mag cal messages: ${e.message}", e)
            }

            // Subscribe to MAG_CAL_PROGRESS and MAG_CAL_REPORT before sending command
            Log.d(TAG, "→ Starting message listeners...")
            startProgressListener()
            startReportListener()
            startStatusTextListener()
            Log.d(TAG, "✓ All message listeners started")

            // Track if we receive progress
            var progressReceived = false
            val progressJob = launch {
                telemetryRepository.magCalProgress.take(1).collect {
                    progressReceived = true
                    Log.d(TAG, "✓ First MAG_CAL_PROGRESS message received!")
                }
            }
            val statusTextJob = launch {
                telemetryRepository.calibrationStatus.take(1).collect {
                    progressReceived = true
                    Log.d(TAG, "✓ First calibration STATUSTEXT received!")
                }
            }

            try {
                Log.d(TAG, "→ Sending MAV_CMD_DO_START_MAG_CAL (42424)")
                Log.d(TAG, "   Parameters: (0, 1, 1, 0, 0, 0, 0)")

                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_DO_START_MAG_CAL,
                    param1 = 0f,  // Bitmask (0 = all compasses)
                    param2 = 1f,  // Retry on failure
                    param3 = 1f,  // Autosave results
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )
                Log.d(TAG, "✓ Command sent, waiting for COMMAND_ACK...")

                val ack = telemetryRepository.awaitCommandAck(MAV_CMD_DO_START_MAG_CAL, ACK_TIMEOUT_MS)
                val ackResult = ack?.result?.value
                val ackName = ack?.result?.entry?.name ?: "NO_ACK"

                Log.d(TAG, "← Received COMMAND_ACK: $ackName (value=$ackResult)")

                if (ackResult == 0u || ackResult == 5u) {
                    Log.d(TAG, "✓ COMMAND ACCEPTED - Calibration started successfully!")

                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.InProgress(
                                currentInstruction = "Rotate vehicle slowly - point each side down towards earth"
                            ),
                            statusText = "Calibrating... Rotate vehicle on all axes"
                        )
                    }

                    // Wait for progress or timeout
                    delay(PROGRESS_TIMEOUT_MS)
                    if (!progressReceived) {
                        Log.e(TAG, "❌ TIMEOUT: No progress messages received")
                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Failed("No progress received from autopilot!"),
                                statusText = "No progress received.\n\nCheck connection and try again."
                            )
                        }
                        stopAllListeners()
                        telemetryRepository.stopMagCalMessages()
                    }
                } else {
                    Log.e(TAG, "❌ COMMAND REJECTED: $ackName")
                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.Failed("Calibration rejected: $ackName"),
                            statusText = "Failed to start calibration: $ackName"
                        )
                    }
                    stopAllListeners()
                    telemetryRepository.stopMagCalMessages()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during calibration start", e)
                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Failed("Error: ${e.message}"),
                        statusText = "Error starting calibration: ${e.message}"
                    )
                }
                stopAllListeners()
                telemetryRepository.stopMagCalMessages()
            } finally {
                progressJob.cancel()
                statusTextJob.cancel()
            }
        }
    }

    /**
     * STEP 4a: Accept calibration results using MAV_CMD_DO_ACCEPT_MAG_CAL.
     */
    fun acceptCalibration() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusText = "Accepting calibration...",
                    showAcceptDialog = false
                )
            }

            try {
                Log.d(TAG, "Sending MAV_CMD_DO_ACCEPT_MAG_CAL (42425)")

                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_DO_ACCEPT_MAG_CAL,
                    param1 = 0f,
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                delay(500)

                _uiState.update {
                    it.copy(
                        statusText = "Calibration accepted! Please reboot the autopilot.",
                        calibrationState = CompassCalibrationState.Success(
                            message = "Calibration completed and saved successfully!",
                            compassReports = it.compassReports.values.toList()
                        )
                    )
                }

                stopAllListeners()
                telemetryRepository.stopMagCalMessages()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept compass calibration", e)
                _uiState.update {
                    it.copy(statusText = "Error accepting calibration: ${e.message}")
                }
            }
        }
    }

    /**
     * STEP 4b: Cancel compass calibration using MAV_CMD_DO_CANCEL_MAG_CAL.
     */
    fun cancelCalibration() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusText = "Cancelling calibration...",
                    showCancelDialog = false
                )
            }

            try {
                Log.d(TAG, "Sending MAV_CMD_DO_CANCEL_MAG_CAL (42426)")

                telemetryRepository.sendCommand(
                    commandId = MAV_CMD_DO_CANCEL_MAG_CAL,
                    param1 = 0f,
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                delay(500)

                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Cancelled,
                        statusText = "Calibration cancelled",
                        compassProgress = emptyMap(),
                        compassReports = emptyMap(),
                        overallProgress = 0,
                        calibrationComplete = false
                    )
                }

                stopAllListeners()
                telemetryRepository.stopMagCalMessages()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel compass calibration", e)
                _uiState.update {
                    it.copy(statusText = "Error cancelling: ${e.message}")
                }
            }
        }
    }

    fun resetCalibration() {
        stopAllListeners()
        _uiState.update {
            it.copy(
                calibrationState = CompassCalibrationState.Idle,
                statusText = "",
                compassProgress = emptyMap(),
                compassReports = emptyMap(),
                overallProgress = 0,
                showCancelDialog = false,
                showAcceptDialog = false,
                calibrationComplete = false
            )
        }
    }

    fun showCancelDialog(show: Boolean) {
        _uiState.update { it.copy(showCancelDialog = show) }
    }

    fun showAcceptDialog(show: Boolean) {
        _uiState.update { it.copy(showAcceptDialog = show) }
    }

    /**
     * STEP 2: Listen to MAG_CAL_PROGRESS messages for ongoing progress updates.
     */
    private fun startProgressListener() {
        progressListenerJob?.cancel()
        progressListenerJob = viewModelScope.launch {
            telemetryRepository.magCalProgress.collect { mavProgress ->
                val compassId = mavProgress.compassId.toInt()
                val completionPct = mavProgress.completionPct.toInt()
                val calStatus = mavProgress.calStatus.entry?.name ?: "UNKNOWN"

                Log.d(TAG, "MAG_CAL_PROGRESS: compass=$compassId status=$calStatus pct=$completionPct")

                val progress = CompassProgress(
                    compassId = mavProgress.compassId,
                    calMask = mavProgress.calMask,
                    calStatus = calStatus,
                    attempt = mavProgress.attempt,
                    completionPct = mavProgress.completionPct,
                    completionMask = mavProgress.completionMask,
                    directionX = mavProgress.directionX,
                    directionY = mavProgress.directionY,
                    directionZ = mavProgress.directionZ
                )

                val updatedProgress = _uiState.value.compassProgress.toMutableMap()
                updatedProgress[compassId] = progress

                val overallPct = if (updatedProgress.isNotEmpty()) {
                    updatedProgress.values.map { it.completionPct.toInt() }.average().toInt()
                } else {
                    0
                }

                val instruction = buildRotationInstruction(
                    mavProgress.directionX,
                    mavProgress.directionY,
                    mavProgress.directionZ
                )

                _uiState.update {
                    it.copy(
                        compassProgress = updatedProgress,
                        overallProgress = overallPct,
                        statusText = "Calibrating... $overallPct%",
                        calibrationState = CompassCalibrationState.InProgress(
                            currentInstruction = instruction
                        )
                    )
                }
            }
        }
    }

    /**
     * Listen to STATUSTEXT messages for calibration status updates.
     */
    private fun startStatusTextListener() {
        statusTextListenerJob?.cancel()
        statusTextListenerJob = viewModelScope.launch {
            telemetryRepository.calibrationStatus.collect { statusText ->
                val text = statusText
                val lower = text.lowercase()

                if (!lower.contains("mag") && !lower.contains("compass") && !lower.contains("calib")) {
                    return@collect
                }

                Log.d(TAG, "STATUSTEXT: $text")

                _uiState.update { state ->
                    state.copy(statusText = text)
                }

                // Parse progress percentage
                val progressRegex = """(\d+)%""".toRegex()
                val progressMatch = progressRegex.find(lower)
                if (progressMatch != null) {
                    val progress = progressMatch.groupValues[1].toIntOrNull() ?: 0
                    _uiState.update {
                        it.copy(
                            overallProgress = progress,
                            statusText = "Calibrating... $progress%",
                            calibrationState = CompassCalibrationState.InProgress(
                                currentInstruction = "Rotate vehicle slowly - point each side down"
                            )
                        )
                    }
                }

                // Check for success
                if (lower.contains("calibration successful") ||
                    lower.contains("mag calibration successful") ||
                    lower.contains("compass calibration successful")) {

                    Log.d(TAG, "✓ Calibration SUCCESS detected via STATUSTEXT")

                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.Success(
                                message = "Compass calibration completed successfully!",
                                compassReports = it.compassReports.values.toList()
                            ),
                            statusText = "Success! Please reboot the autopilot.",
                            overallProgress = 100,
                            calibrationComplete = true
                        )
                    }
                    stopAllListeners()
                    telemetryRepository.stopMagCalMessages()
                }

                // Check for failure
                if (lower.contains("calibration failed") ||
                    lower.contains("mag cal failed") ||
                    lower.contains("compass cal failed")) {

                    Log.d(TAG, "✗ Calibration FAILED detected via STATUSTEXT")

                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.Failed(text),
                            statusText = "Calibration failed - please retry"
                        )
                    }
                    stopAllListeners()
                    telemetryRepository.stopMagCalMessages()
                }
            }
        }
    }

    /**
     * STEP 3: Listen to MAG_CAL_REPORT messages for final calibration results.
     */
    private fun startReportListener() {
        reportListenerJob?.cancel()
        reportListenerJob = viewModelScope.launch {
            telemetryRepository.magCalReport.collect { mavReport ->
                val compassId = mavReport.compassId.toInt()
                val calStatus = mavReport.calStatus.entry?.name ?: "UNKNOWN"

                Log.d(TAG, "MAG_CAL_REPORT: compass=$compassId status=$calStatus fitness=${mavReport.fitness}")

                val report = CompassReport(
                    compassId = mavReport.compassId,
                    calStatus = calStatus,
                    autosaved = mavReport.autosaved,
                    fitness = mavReport.fitness,
                    ofsX = mavReport.ofsX,
                    ofsY = mavReport.ofsY,
                    ofsZ = mavReport.ofsZ,
                    diagX = mavReport.diagX,
                    diagY = mavReport.diagY,
                    diagZ = mavReport.diagZ,
                    offdiagX = mavReport.offdiagX,
                    offdiagY = mavReport.offdiagY,
                    offdiagZ = mavReport.offdiagZ,
                    orientationConfidence = mavReport.orientationConfidence,
                    oldOrientation = mavReport.oldOrientation.entry?.name ?: "UNKNOWN",
                    newOrientation = mavReport.newOrientation.entry?.name ?: "UNKNOWN",
                    scaleFactor = mavReport.scaleFactor
                )

                val updatedReports = _uiState.value.compassReports.toMutableMap()
                updatedReports[compassId] = report

                val allReported = checkIfAllCompassesReported(updatedReports)
                val allSuccess = updatedReports.values.all {
                    it.calStatus.contains("SUCCESS", ignoreCase = true)
                }
                val anyFailed = updatedReports.values.any {
                    it.calStatus.contains("FAIL", ignoreCase = true)
                }

                when {
                    allReported && allSuccess -> {
                        _uiState.update {
                            it.copy(
                                compassReports = updatedReports,
                                calibrationComplete = true,
                                overallProgress = 100,
                                statusText = "Calibration complete - Review results and Accept",
                                showAcceptDialog = true,
                                calibrationState = CompassCalibrationState.InProgress(
                                    currentInstruction = "Calibration complete! Review results below."
                                )
                            )
                        }
                        Log.d(TAG, "✓ All compasses calibrated successfully")
                    }
                    allReported && anyFailed -> {
                        val failedCompasses = updatedReports.filter {
                            it.value.calStatus.contains("FAIL", ignoreCase = true)
                        }.keys.joinToString(", ")

                        _uiState.update {
                            it.copy(
                                compassReports = updatedReports,
                                calibrationState = CompassCalibrationState.Failed(
                                    "Calibration failed for compass(es): $failedCompasses"
                                ),
                                statusText = "Calibration failed - please retry",
                                calibrationComplete = true
                            )
                        }
                        stopAllListeners()
                        telemetryRepository.stopMagCalMessages()
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                compassReports = updatedReports,
                                statusText = "Received calibration report for compass $compassId..."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkIfAllCompassesReported(reports: Map<Int, CompassReport>): Boolean {
        val progressCompasses = _uiState.value.compassProgress.keys
        if (progressCompasses.isEmpty()) {
            return reports.isNotEmpty()
        }
        return progressCompasses.all { it in reports.keys }
    }

    private fun buildRotationInstruction(dirX: Float, dirY: Float, dirZ: Float): String {
        val absX = kotlin.math.abs(dirX)
        val absY = kotlin.math.abs(dirY)
        val absZ = kotlin.math.abs(dirZ)

        return when {
            absZ > absX && absZ > absY -> {
                if (dirZ > 0) "Point TOP towards the ground"
                else "Point BOTTOM towards the ground"
            }
            absX > absY && absX > absZ -> {
                if (dirX > 0) "Point RIGHT side towards the ground"
                else "Point LEFT side towards the ground"
            }
            absY > absX && absY > absZ -> {
                if (dirY > 0) "Point FRONT towards the ground"
                else "Point BACK towards the ground"
            }
            else -> "Rotate vehicle slowly - point each side down towards earth"
        }
    }

    private fun stopAllListeners() {
        progressListenerJob?.cancel()
        progressListenerJob = null
        reportListenerJob?.cancel()
        reportListenerJob = null
        statusTextListenerJob?.cancel()
        statusTextListenerJob = null

        Log.d(TAG, "All message listeners stopped")
    }

    override fun onCleared() {
        super.onCleared()
        stopAllListeners()
        viewModelScope.launch {
            telemetryRepository.stopMagCalMessages()
        }
    }
}
