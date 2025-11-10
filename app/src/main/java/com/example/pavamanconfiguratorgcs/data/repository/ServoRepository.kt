package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.definitions.minimal.Heartbeat
import com.example.pavamanconfiguratorgcs.data.models.ServoChannel
import com.example.pavamanconfiguratorgcs.data.models.ServoFunction
import com.example.pavamanconfiguratorgcs.data.models.ServoSnapshot
import com.example.pavamanconfiguratorgcs.data.models.VehicleState
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository for handling servo output telemetry and commands
 */
class ServoRepository(
    private val telemetryRepository: TelemetryRepository
) {
    companion object {
        private const val TAG = "ServoRepository"
        private const val MAX_CHANNELS = 16

        // MAV_CMD_DO_SET_SERVO command ID
        private const val MAV_CMD_DO_SET_SERVO = 183u
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    // Current servo channel states
    private val _servoChannels = MutableStateFlow<List<ServoChannel>>(
        List(MAX_CHANNELS) { index ->
            ServoChannel(
                channelIndex = index + 1,
                pwm = 0,
                function = if (index < 4) ServoFunction.entries[index + 1] else ServoFunction.DISABLED
            )
        }
    )
    val servoChannels: StateFlow<List<ServoChannel>> = _servoChannels.asStateFlow()

    // Latest servo snapshot
    private val _latestSnapshot = MutableStateFlow<ServoSnapshot?>(null)
    val latestSnapshot: StateFlow<ServoSnapshot?> = _latestSnapshot.asStateFlow()

    // Vehicle state for safety checks
    private val _vehicleState = MutableStateFlow(VehicleState())
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    init {
        startListeningToMavlink()
    }

    private fun startListeningToMavlink() {
        scope.launch {
            telemetryRepository.connectionState.collect { state ->
                if (state is TelemetryRepository.ConnectionState.HeartbeatVerified) {
                    // Start collecting MAVLink messages
                    collectMavlinkMessages()
                }
            }
        }
    }

    private suspend fun collectMavlinkMessages() {
        telemetryRepository.connection?.let { connection ->
            try {
                connection.mavFrame.collect { frame ->
                    handleMavlinkMessage(frame)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting MAVLink messages", e)
            }
        }
    }

    private fun handleMavlinkMessage(frame: MavFrame<out MavMessage<*>>) {
        when (val message = frame.message) {
            is ServoOutputRaw -> handleServoOutputRaw(message)
            is Heartbeat -> handleHeartbeat(message)
            is ParamValue -> handleParamValue(message)
            else -> { /* Ignore other messages */ }
        }
    }

    /**
     * Handle SERVO_OUTPUT_RAW message (msg id 36)
     */
    private fun handleServoOutputRaw(message: ServoOutputRaw) {
        val timestamp = message.timeUsec.toLong()
        val port = message.port.toInt()

        // Extract PWM values for all 16 channels (servo1_raw to servo16_raw)
        val pwmValues = listOf(
            message.servo1Raw.toInt(),
            message.servo2Raw.toInt(),
            message.servo3Raw.toInt(),
            message.servo4Raw.toInt(),
            message.servo5Raw.toInt(),
            message.servo6Raw.toInt(),
            message.servo7Raw.toInt(),
            message.servo8Raw.toInt(),
            message.servo9Raw.toInt(),
            message.servo10Raw.toInt(),
            message.servo11Raw.toInt(),
            message.servo12Raw.toInt(),
            message.servo13Raw.toInt(),
            message.servo14Raw.toInt(),
            message.servo15Raw.toInt(),
            message.servo16Raw.toInt()
        )

        // Update servo channels with new PWM values
        val updatedChannels = _servoChannels.value.mapIndexed { index, channel ->
            channel.copy(
                pwm = pwmValues.getOrNull(index) ?: 0,
                timestampUsec = timestamp
            )
        }

        _servoChannels.value = updatedChannels

        // Create snapshot
        _latestSnapshot.value = ServoSnapshot(
            port = port,
            channels = updatedChannels,
            timestampUsec = timestamp
        )

        Log.d(TAG, "Servo output updated: ${pwmValues.take(8)}")
    }

    /**
     * Handle HEARTBEAT message for vehicle state
     */
    private fun handleHeartbeat(message: Heartbeat) {
        // Check if SAFETY_ARMED flag (bit 7, value 128) is set in baseMode
        // MAV_MODE_FLAG_SAFETY_ARMED = 128 (0x80)
        val baseModeValue = message.baseMode.value
        val isArmed = (baseModeValue and 128u) != 0u

        _vehicleState.value = _vehicleState.value.copy(
            isArmed = isArmed,
            systemStatus = message.systemStatus.value.toInt()
        )
    }

    /**
     * Handle PARAM_VALUE for servo function assignments
     */
    private fun handleParamValue(message: ParamValue) {
        val paramId = message.paramId.trim('\u0000')

        // Parse SERVOx_FUNCTION parameters
        if (paramId.startsWith("SERVO") && paramId.endsWith("_FUNCTION")) {
            val channelMatch = Regex("SERVO(\\d+)_FUNCTION").find(paramId)
            channelMatch?.let {
                val channelIndex = it.groupValues[1].toIntOrNull() ?: return
                val functionValue = message.paramValue.toInt()
                val servoFunction = ServoFunction.fromIndex(functionValue)

                updateChannelFunction(channelIndex, servoFunction)
            }
        }

        // Parse SERVOx_REVERSED parameters
        if (paramId.startsWith("SERVO") && paramId.endsWith("_REVERSED")) {
            val channelMatch = Regex("SERVO(\\d+)_REVERSED").find(paramId)
            channelMatch?.let {
                val channelIndex = it.groupValues[1].toIntOrNull() ?: return
                val reversed = message.paramValue.toInt() == 1

                updateChannelReverse(channelIndex, reversed)
            }
        }

        // Parse MIN/TRIM/MAX parameters
        when {
            paramId.startsWith("SERVO") && paramId.endsWith("_MIN") -> {
                val channelMatch = Regex("SERVO(\\d+)_MIN").find(paramId)
                channelMatch?.let {
                    val channelIndex = it.groupValues[1].toIntOrNull() ?: return
                    updateChannelMin(channelIndex, message.paramValue.toInt())
                }
            }
            paramId.startsWith("SERVO") && paramId.endsWith("_TRIM") -> {
                val channelMatch = Regex("SERVO(\\d+)_TRIM").find(paramId)
                channelMatch?.let {
                    val channelIndex = it.groupValues[1].toIntOrNull() ?: return
                    updateChannelTrim(channelIndex, message.paramValue.toInt())
                }
            }
            paramId.startsWith("SERVO") && paramId.endsWith("_MAX") -> {
                val channelMatch = Regex("SERVO(\\d+)_MAX").find(paramId)
                channelMatch?.let {
                    val channelIndex = it.groupValues[1].toIntOrNull() ?: return
                    updateChannelMax(channelIndex, message.paramValue.toInt())
                }
            }
        }
    }

    private fun updateChannelFunction(channelIndex: Int, function: ServoFunction) {
        if (channelIndex !in 1..MAX_CHANNELS) return

        _servoChannels.value = _servoChannels.value.mapIndexed { index, channel ->
            if (index == channelIndex - 1) {
                channel.copy(function = function)
            } else channel
        }
    }

    private fun updateChannelReverse(channelIndex: Int, reverse: Boolean) {
        if (channelIndex !in 1..MAX_CHANNELS) return

        _servoChannels.value = _servoChannels.value.mapIndexed { index, channel ->
            if (index == channelIndex - 1) {
                channel.copy(reverse = reverse)
            } else channel
        }
    }

    private fun updateChannelMin(channelIndex: Int, min: Int) {
        if (channelIndex !in 1..MAX_CHANNELS) return

        _servoChannels.value = _servoChannels.value.mapIndexed { index, channel ->
            if (index == channelIndex - 1) {
                channel.copy(minPwm = min)
            } else channel
        }
    }

    private fun updateChannelTrim(channelIndex: Int, trim: Int) {
        if (channelIndex !in 1..MAX_CHANNELS) return

        _servoChannels.value = _servoChannels.value.mapIndexed { index, channel ->
            if (index == channelIndex - 1) {
                channel.copy(trimPwm = trim)
            } else channel
        }
    }

    private fun updateChannelMax(channelIndex: Int, max: Int) {
        if (channelIndex !in 1..MAX_CHANNELS) return

        _servoChannels.value = _servoChannels.value.mapIndexed { index, channel ->
            if (index == channelIndex - 1) {
                channel.copy(maxPwm = max)
            } else channel
        }
    }

    /**
     * Send MAV_CMD_DO_SET_SERVO command to set a servo PWM value
     */
    suspend fun setServoPwm(
        servoNumber: Int,
        pwmUs: Int
    ): Result<Unit> {
        if (servoNumber !in 1..MAX_CHANNELS) {
            return Result.failure(IllegalArgumentException("Invalid servo number: $servoNumber"))
        }

        if (pwmUs !in 800..2200) {
            return Result.failure(IllegalArgumentException("PWM value out of range: $pwmUs"))
        }

        return try {
            val connection = telemetryRepository.connection
                ?: return Result.failure(IllegalStateException("No MAVLink connection"))

            // Use reflection to create MavEnumValue since the enum constant doesn't exist in the library
            // This is a workaround for missing enum values
            val commandValue = try {
                // Try to find if MAV_CMD_DO_SET_SERVO exists in the enum
                MavCmd.entries.firstOrNull { it.name == "MAV_CMD_DO_SET_SERVO" }?.let { MavEnumValue.of(it) }
            } catch (e: Exception) {
                null
            } ?: run {
                // If not found, we need to use a different approach
                // For now, log a warning and return failure
                Log.w(TAG, "MAV_CMD_DO_SET_SERVO not available in this MAVLink library version")
                return Result.failure(IllegalStateException("MAV_CMD_DO_SET_SERVO command not supported by library"))
            }

            val command = CommandLong(
                targetSystem = telemetryRepository.fcuSystemId,
                targetComponent = telemetryRepository.fcuComponentId,
                command = commandValue,
                confirmation = 0u,
                param1 = servoNumber.toFloat(),
                param2 = pwmUs.toFloat(),
                param3 = 0f,
                param4 = 0f,
                param5 = 0f,
                param6 = 0f,
                param7 = 0f
            )

            connection.sendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = command
            )

            Log.d(TAG, "Sent DO_SET_SERVO: channel=$servoNumber, pwm=$pwmUs")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send servo command", e)
            Result.failure(e)
        }
    }

    /**
     * Request servo parameters from the vehicle
     */
    suspend fun requestServoParameters() {
        try {
            val connection = telemetryRepository.connection ?: return

            for (i in 1..MAX_CHANNELS) {
                // Request SERVOx_FUNCTION
                requestParameter(connection, "SERVO${i}_FUNCTION")
                // Request SERVOx_REVERSED
                requestParameter(connection, "SERVO${i}_REVERSED")
                // Request MIN/TRIM/MAX
                requestParameter(connection, "SERVO${i}_MIN")
                requestParameter(connection, "SERVO${i}_TRIM")
                requestParameter(connection, "SERVO${i}_MAX")
            }

            Log.d(TAG, "Requested servo parameters")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request servo parameters", e)
        }
    }

    private suspend fun requestParameter(connection: com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection, paramId: String) {
        val request = ParamRequestRead(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId,
            paramId = paramId,
            paramIndex = -1
        )

        connection.sendUnsignedV2(
            systemId = telemetryRepository.gcsSystemId,
            componentId = telemetryRepository.gcsComponentId,
            payload = request
        )
    }

    /**
     * Check if it's safe to send servo commands
     */
    fun canSendServoCommands(): Boolean {
        return !_vehicleState.value.isArmed
    }
}
