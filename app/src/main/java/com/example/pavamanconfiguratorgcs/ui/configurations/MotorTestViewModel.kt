package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.api.wrap
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for Motor Test following MVVM architecture.
 * Manages motor testing state, frame detection, and MAVLink MOTOR_TEST commands.
 */
class MotorTestViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MotorTest"

        // Parameter names
        private const val PARAM_FRAME_CLASS = "FRAME_CLASS"
        private const val PARAM_FRAME_TYPE = "FRAME_TYPE"
        private const val PARAM_Q_FRAME_CLASS = "Q_FRAME_CLASS"
        private const val PARAM_Q_FRAME_TYPE = "Q_FRAME_TYPE"
        private const val PARAM_MOT_SPIN_ARM = "MOT_SPIN_ARM"
        private const val PARAM_MOT_SPIN_MIN = "MOT_SPIN_MIN"

        // Default values
        private const val DEFAULT_THROTTLE_PERCENT = 5f
        private const val DEFAULT_DURATION = 2f
        private const val MAX_THROTTLE_FOR_SPIN = 20f

        // MAVLink command timeout - increased to 5 seconds for motor test
        private const val COMMAND_TIMEOUT_MS = 5000L
    }

    // UI State
    private val _uiState = MutableStateFlow(MotorTestUiState())
    val uiState: StateFlow<MotorTestUiState> = _uiState.asStateFlow()

    // Connection state
    val isConnected = telemetryRepository.connectionState
        .map {
            it is TelemetryRepository.ConnectionState.Connected ||
            it is TelemetryRepository.ConnectionState.HeartbeatVerified
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        Log.d(TAG, "MotorTestViewModel initialized")
        loadFrameInfo()
    }

    /**
     * Load frame class and type information from autopilot.
     */
    private fun loadFrameInfo() {
        viewModelScope.launch {
            Log.d(TAG, "Loading frame information")
            _uiState.update { it.copy(isLoading = true) }

            try {
                var frameClass = 0
                var frameType = 0

                // Try FRAME_CLASS first
                when (val result = parameterRepository.requestParameter(PARAM_FRAME_CLASS)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        frameClass = result.parameter.value.toInt()
                        Log.d(TAG, "Loaded FRAME_CLASS = $frameClass")
                    }
                    else -> {
                        // Try Q_FRAME_CLASS for QuadPlane
                        when (val qResult = parameterRepository.requestParameter(PARAM_Q_FRAME_CLASS)) {
                            is ParameterRepository.ParameterResult.Success -> {
                                frameClass = qResult.parameter.value.toInt()
                                Log.d(TAG, "Loaded Q_FRAME_CLASS = $frameClass")
                            }
                            else -> Log.w(TAG, "Could not load frame class")
                        }
                    }
                }

                // Try FRAME_TYPE
                when (val result = parameterRepository.requestParameter(PARAM_FRAME_TYPE)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        frameType = result.parameter.value.toInt()
                        Log.d(TAG, "Loaded FRAME_TYPE = $frameType")
                    }
                    else -> {
                        // Try Q_FRAME_TYPE for QuadPlane
                        when (val qResult = parameterRepository.requestParameter(PARAM_Q_FRAME_TYPE)) {
                            is ParameterRepository.ParameterResult.Success -> {
                                frameType = qResult.parameter.value.toInt()
                                Log.d(TAG, "Loaded Q_FRAME_TYPE = $frameType")
                            }
                            else -> Log.w(TAG, "Could not load frame type")
                        }
                    }
                }

                // Detect motor count based on frame class or MAV_TYPE
                val motorCount = detectMotorCount(frameClass)
                val motors = createMotorList(motorCount, frameClass, frameType)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        frameClass = frameClass,
                        frameType = frameType,
                        frameClassName = getFrameClassName(frameClass),
                        frameTypeName = getFrameTypeName(frameType),
                        motorCount = motorCount,
                        motors = motors
                    )
                }

                Log.d(TAG, "Frame info loaded: Class=$frameClass, Type=$frameType, Motors=$motorCount")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading frame info", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load frame info: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Detect motor count based on frame class or MAV_TYPE.
     */
    private fun detectMotorCount(frameClass: Int): Int {
        // Frame class mapping (ArduPilot convention)
        return when (frameClass) {
            1 -> 4  // QUAD
            2 -> 6  // HEXA
            3 -> 8  // OCTO
            4 -> 8  // OCTA_QUAD
            5 -> 4  // Y6 (uses 6 motors but 3 positions)
            7 -> 4  // TRI
            10 -> 12 // DECA
            11 -> 12 // DODECA_HEXA
            else -> {
                Log.w(TAG, "Unknown frame class: $frameClass, defaulting to 4 motors")
                4 // Default to quad
            }
        }
    }

    /**
     * Create motor list with metadata.
     */
    private fun createMotorList(motorCount: Int, frameClass: Int, frameType: Int): List<MotorInfo> {
        val motors = mutableListOf<MotorInfo>()

        for (i in 1..motorCount) {
            val letter = ('A' + (i - 1)).toString()
            val rotation = getMotorRotation(i, frameClass, frameType)

            motors.add(
                MotorInfo(
                    number = i,
                    label = "Motor $letter",
                    rotation = rotation,
                    testOrder = i
                )
            )
        }

        Log.d(TAG, "Created motor list with $motorCount motors")
        return motors
    }

    /**
     * Get motor rotation direction (simplified - in production, load from JSON).
     */
    private fun getMotorRotation(motorNumber: Int, frameClass: Int, frameType: Int): String {
        // Simplified rotation pattern for QUAD X configuration
        // In production, this should be loaded from APMotorLayout.json
        return when {
            frameClass == 1 && frameType == 1 -> { // QUAD X
                when (motorNumber) {
                    1, 4 -> "CCW"
                    2, 3 -> "CW"
                    else -> "Unknown"
                }
            }
            else -> if (motorNumber % 2 == 0) "CW" else "CCW"
        }
    }

    /**
     * Get frame class name.
     */
    private fun getFrameClassName(frameClass: Int): String {
        return when (frameClass) {
            1 -> "QUAD"
            2 -> "HEXA"
            3 -> "OCTO"
            4 -> "OCTA_QUAD"
            5 -> "Y6"
            7 -> "TRI"
            10 -> "DECA"
            11 -> "DODECA_HEXA"
            else -> "UNKNOWN ($frameClass)"
        }
    }

    /**
     * Get frame type name.
     */
    private fun getFrameTypeName(frameType: Int): String {
        return when (frameType) {
            0 -> "PLUS"
            1 -> "X"
            2 -> "V"
            3 -> "H"
            else -> "TYPE_$frameType"
        }
    }

    /**
     * Update throttle percentage.
     */
    fun updateThrottlePercent(value: Float) {
        Log.d(TAG, "Updating throttle to $value%")
        _uiState.update { it.copy(throttlePercent = value.coerceIn(-100f, 100f)) }
    }

    /**
     * Update test duration.
     */
    fun updateDuration(value: Float) {
        Log.d(TAG, "Updating duration to $value seconds")
        _uiState.update { it.copy(duration = value.coerceIn(0f, 999f)) }
    }

    /**
     * Test a single motor.
     */
    fun testMotor(motorNumber: Int) {
        val state = _uiState.value
        Log.d(TAG, "Testing motor $motorNumber at ${state.throttlePercent}% for ${state.duration}s")

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingMotor = motorNumber, errorMessage = null) }

            val result = sendMotorTestCommand(
                motorNumber = motorNumber,
                throttlePercent = state.throttlePercent,
                duration = state.duration,
                motorCount = 0
            )

            // Wait for the duration plus a small buffer
            delay((state.duration * 1000).toLong() + 200)

            _uiState.update {
                it.copy(
                    isTestingMotor = null,
                    errorMessage = if (!result) "Motor test command failed" else null
                )
            }
        }
    }

    /**
     * Test all motors sequentially (one at a time).
     */
    fun testAllMotors() {
        val state = _uiState.value
        Log.d(TAG, "Testing all motors sequentially")

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAll = true, errorMessage = null) }

            for (motor in state.motors) {
                _uiState.update { it.copy(isTestingMotor = motor.number) }

                val result = sendMotorTestCommand(
                    motorNumber = motor.number,
                    throttlePercent = state.throttlePercent,
                    duration = state.duration,
                    motorCount = 0
                )

                if (!result) {
                    Log.e(TAG, "Failed to test motor ${motor.number}")
                    _uiState.update {
                        it.copy(
                            isTestingAll = false,
                            isTestingMotor = null,
                            errorMessage = "Failed to test motor ${motor.number}"
                        )
                    }
                    return@launch
                }

                // Wait for test duration before next motor
                delay((state.duration * 1000).toLong() + 300)
            }

            _uiState.update {
                it.copy(isTestingAll = false, isTestingMotor = null)
            }

            Log.d(TAG, "All motors tested successfully")
        }
    }

    /**
     * Test all motors in sequence (autopilot cycles through them).
     */
    fun testAllInSequence() {
        val state = _uiState.value
        Log.d(TAG, "Testing all motors in sequence (autopilot controlled)")

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAll = true, errorMessage = null) }

            val result = sendMotorTestCommand(
                motorNumber = 1,  // Start with motor 1
                throttlePercent = state.throttlePercent,
                duration = state.duration,
                motorCount = state.motorCount  // Tell autopilot to cycle through all
            )

            if (!result) {
                _uiState.update {
                    it.copy(
                        isTestingAll = false,
                        errorMessage = "Failed to start sequence test"
                    )
                }
                return@launch
            }

            // Wait for full sequence (duration per motor * motor count)
            delay((state.duration * state.motorCount * 1000).toLong() + 500)

            _uiState.update { it.copy(isTestingAll = false) }
            Log.d(TAG, "Sequence test completed")
        }
    }

    /**
     * Stop all motors immediately.
     */
    fun stopAllMotors() {
        val state = _uiState.value
        Log.d(TAG, "Stopping all motors")

        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }

            for (motor in state.motors) {
                sendMotorTestCommand(
                    motorNumber = motor.number,
                    throttlePercent = 0f,
                    duration = 0f,
                    motorCount = 0
                )
                delay(50) // Small delay between stop commands
            }

            _uiState.update {
                it.copy(isTestingMotor = null, isTestingAll = false)
            }

            Log.d(TAG, "All motors stopped")
        }
    }

    /**
     * Set MOT_SPIN_ARM parameter (arm throttle).
     */
    fun setSpinArm() {
        val throttle = _uiState.value.throttlePercent

        if (throttle >= MAX_THROTTLE_FOR_SPIN) {
            Log.w(TAG, "Throttle percent $throttle% is too high (must be < $MAX_THROTTLE_FOR_SPIN%)")
            _uiState.update {
                it.copy(errorMessage = "Throttle percent above ${MAX_THROTTLE_FOR_SPIN.toInt()}%, too high")
            }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Setting MOT_SPIN_ARM to ${throttle / 100f}")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = parameterRepository.setParameter(
                paramName = PARAM_MOT_SPIN_ARM,
                value = throttle / 100f,
                paramType = MavParamType.REAL32
            )

            when (result) {
                is ParameterRepository.ParameterResult.Success -> {
                    Log.d(TAG, "MOT_SPIN_ARM set successfully")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "MOT_SPIN_ARM set to ${throttle / 100f}"
                        )
                    }
                }
                is ParameterRepository.ParameterResult.Error -> {
                    Log.e(TAG, "Failed to set MOT_SPIN_ARM: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to set MOT_SPIN_ARM: ${result.message}"
                        )
                    }
                }
                is ParameterRepository.ParameterResult.Timeout -> {
                    Log.e(TAG, "Timeout setting MOT_SPIN_ARM")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Timeout setting MOT_SPIN_ARM"
                        )
                    }
                }
            }
        }
    }

    /**
     * Set MOT_SPIN_MIN parameter (minimum throttle).
     */
    fun setSpinMin() {
        val throttle = _uiState.value.throttlePercent

        if (throttle >= MAX_THROTTLE_FOR_SPIN) {
            Log.w(TAG, "Throttle percent $throttle% is too high (must be < $MAX_THROTTLE_FOR_SPIN%)")
            _uiState.update {
                it.copy(errorMessage = "Throttle percent above ${MAX_THROTTLE_FOR_SPIN.toInt()}%, too high")
            }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Setting MOT_SPIN_MIN to ${throttle / 100f}")
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = parameterRepository.setParameter(
                paramName = PARAM_MOT_SPIN_MIN,
                value = throttle / 100f,
                paramType = MavParamType.REAL32
            )

            when (result) {
                is ParameterRepository.ParameterResult.Success -> {
                    Log.d(TAG, "MOT_SPIN_MIN set successfully")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "MOT_SPIN_MIN set to ${throttle / 100f}"
                        )
                    }
                }
                is ParameterRepository.ParameterResult.Error -> {
                    Log.e(TAG, "Failed to set MOT_SPIN_MIN: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to set MOT_SPIN_MIN: ${result.message}"
                        )
                    }
                }
                is ParameterRepository.ParameterResult.Timeout -> {
                    Log.e(TAG, "Timeout setting MOT_SPIN_MIN")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Timeout setting MOT_SPIN_MIN"
                        )
                    }
                }
            }
        }
    }

    /**
     * Send MAVLink MOTOR_TEST command to autopilot.
     * Returns true if command was acknowledged successfully.
     */
    private suspend fun sendMotorTestCommand(
        motorNumber: Int,
        throttlePercent: Float,
        duration: Float,
        motorCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.e(TAG, "No active connection")
            return@withContext false
        }

        try {
            Log.d(TAG, "Sending MOTOR_TEST command: motor=$motorNumber, throttle=$throttlePercent%, duration=${duration}s, count=$motorCount")

            // MOTOR_TEST_THROTTLE_PERCENT = 1 (as per MAVLink spec and Mission Planner)
            val throttleType = 1f

            Log.d(TAG, "Command params: targetSys=${telemetryRepository.fcuSystemId}, targetComp=${telemetryRepository.fcuComponentId}, " +
                    "p1=$motorNumber, p2=$throttleType, p3=$throttlePercent, p4=$duration, p5=$motorCount")

            val command = CommandLong(
                targetSystem = telemetryRepository.fcuSystemId,
                targetComponent = telemetryRepository.fcuComponentId,
                command = MavCmd.DO_MOTOR_TEST.wrap(),
                confirmation = 0u,
                param1 = motorNumber.toFloat(),  // Motor number (1-based)
                param2 = throttleType,           // Throttle type (1 = MOTOR_TEST_THROTTLE_PERCENT)
                param3 = throttlePercent,        // Throttle value (percentage, e.g., 5 = 5%)
                param4 = duration,               // Duration (seconds)
                param5 = motorCount.toFloat(),   // Motor count (0 = single motor, 2+ = sequential test)
                param6 = 0f,
                param7 = 0f
            )

            // Send command
            val sendResult = connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = command
            )

            Log.d(TAG, "Command sent, result=$sendResult")

            // Wait for ACK
            val ackReceived = waitForCommandAck(MavCmd.DO_MOTOR_TEST)

            if (ackReceived) {
                Log.d(TAG, "MOTOR_TEST command acknowledged")
                return@withContext true
            } else {
                Log.w(TAG, "MOTOR_TEST command not acknowledged (timeout)")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending MOTOR_TEST command", e)
            return@withContext false
        }
    }

    /**
     * Wait for COMMAND_ACK from autopilot.
     */
    private suspend fun waitForCommandAck(command: MavCmd): Boolean {
        Log.d(TAG, "Waiting for COMMAND_ACK for ${command.name} (${command.value})...")

        return withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
            telemetryRepository.mavFrame
                .filter { frame ->
                    val isCommandAck = frame.message is CommandAck
                    val isFromFcu = frame.systemId == telemetryRepository.fcuSystemId
                    val isFromCorrectComponent = frame.componentId == telemetryRepository.fcuComponentId

                    if (isCommandAck) {
                        val ack = frame.message as CommandAck
                        Log.d(TAG, "Received ACK: command=${ack.command.value}, result=${ack.result.entry?.name ?: ack.result.value}, fromFcu=$isFromFcu, fromComponent=$isFromCorrectComponent")
                    }

                    isCommandAck && isFromFcu && isFromCorrectComponent
                }
                .map { it.message as CommandAck }
                .filter { ack ->
                    val matches = ack.command.value == command.value
                    Log.d(TAG, "ACK command match: ${ack.command.value} == ${command.value} = $matches")
                    matches
                }
                .map { ack ->
                    val result = ack.result
                    Log.d(TAG, "Processing COMMAND_ACK for ${command.name}: result=${result.entry?.name ?: result.value} (${result.value})")

                    when (result.entry) {
                        MavResult.ACCEPTED -> {
                            Log.d(TAG, "Motor test command ACCEPTED")
                            true
                        }
                        MavResult.TEMPORARILY_REJECTED -> {
                            // Temporarily rejected is often used for pre-arm warnings but motor test still runs
                            Log.w(TAG, "Motor test command TEMPORARILY_REJECTED (may still execute)")
                            _uiState.update {
                                it.copy(errorMessage = "Warning: Command temporarily rejected but may execute")
                            }
                            true // Treat as success - motor test often runs anyway
                        }
                        MavResult.DENIED -> {
                            Log.e(TAG, "Motor test command DENIED")
                            _uiState.update {
                                it.copy(errorMessage = "Command was denied by the autopilot")
                            }
                            false
                        }
                        MavResult.UNSUPPORTED -> {
                            Log.e(TAG, "Motor test command UNSUPPORTED")
                            _uiState.update {
                                it.copy(errorMessage = "Motor test not supported by autopilot")
                            }
                            false
                        }
                        MavResult.FAILED -> {
                            Log.e(TAG, "Motor test command FAILED")
                            _uiState.update {
                                it.copy(errorMessage = "Motor test command failed")
                            }
                            false
                        }
                        else -> {
                            Log.w(TAG, "Motor test command returned: ${result.entry?.name ?: "Unknown"} (${result.value})")
                            _uiState.update {
                                it.copy(errorMessage = "Command result: ${result.entry?.name ?: "Unknown (${result.value})"}")
                            }
                            // For unknown results, try to proceed anyway
                            true
                        }
                    }
                }
                .first()
        } ?: run {
            Log.e(TAG, "Timeout waiting for COMMAND_ACK for ${command.name} after ${COMMAND_TIMEOUT_MS}ms")
            _uiState.update {
                it.copy(errorMessage = "Timeout waiting for command acknowledgment")
            }
            false
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clear success message.
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

/**
 * Motor information data class.
 */
data class MotorInfo(
    val number: Int,
    val label: String,
    val rotation: String,
    val testOrder: Int
)

/**
 * UI state for Motor Test screen.
 */
data class MotorTestUiState(
    val isLoading: Boolean = false,
    val frameClass: Int = 0,
    val frameType: Int = 0,
    val frameClassName: String = "",
    val frameTypeName: String = "",
    val motorCount: Int = 4,
    val motors: List<MotorInfo> = emptyList(),
    val throttlePercent: Float = 5f,
    val duration: Float = 2f,
    val isTestingMotor: Int? = null,  // Motor number being tested, null if none
    val isTestingAll: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
