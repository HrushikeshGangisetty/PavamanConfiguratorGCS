package com.example.pavamanconfiguratorgcs.telemetry.helpers

import android.util.Log
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.definitions.common.RcChannels
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Helper class for RC channel operations following MAVLink protocol.
 * Handles RC_CHANNELS message streaming and parameter operations.
 */
class RCChannelsHelper(
    private val telemetryRepository: TelemetryRepository
) {
    companion object {
        private const val TAG = "RCChannelsHelper"
        private const val RC_CHANNELS_MESSAGE_ID = 65u // MAV_MSG_ID_RC_CHANNELS
        private const val MAV_CMD_SET_MESSAGE_INTERVAL = 511u
    }

    /**
     * Request RC_CHANNELS messages from the autopilot at specified rate.
     * Message ID 65 for RC_CHANNELS.
     */
    suspend fun requestRCChannels(hz: Float = 10f) {
        Log.d(TAG, "========== REQUESTING RC_CHANNELS MESSAGE STREAMING ==========")
        Log.d(TAG, "Requesting RC_CHANNELS (65) at $hz Hz")
        Log.d(TAG, "Interval: ${if (hz > 0f) (1_000_000f / hz).toInt() else 0} microseconds")

        telemetryRepository.sendCommand(
            commandId = MAV_CMD_SET_MESSAGE_INTERVAL,
            param1 = RC_CHANNELS_MESSAGE_ID.toFloat(), // 65f - RC_CHANNELS message ID
            param2 = if (hz <= 0f) 0f else (1_000_000f / hz) // interval in microseconds
        )
        Log.d(TAG, "✓ RC_CHANNELS message interval command sent")
        Log.d(TAG, "==============================================================")
    }

    /**
     * Stop RC_CHANNELS message streaming.
     */
    suspend fun stopRCChannels() {
        Log.d(TAG, "========== STOPPING RC_CHANNELS MESSAGE STREAMING ==========")
        telemetryRepository.sendCommand(
            commandId = MAV_CMD_SET_MESSAGE_INTERVAL,
            param1 = RC_CHANNELS_MESSAGE_ID.toFloat(), // 65f - RC_CHANNELS message ID
            param2 = 0f // 0 = disable streaming
        )
        Log.d(TAG, "✓ RC_CHANNELS streaming disabled")
        Log.d(TAG, "=============================================================")
    }

    /**
     * Get flow of RC_CHANNELS messages from the detected FCU only.
     * Filters by FCU system ID to avoid processing messages from other systems.
     */
    fun getRCChannelsFlow(): Flow<RcChannels> {
        return telemetryRepository.mavFrame
            .filter {
                telemetryRepository.fcuDetected.value &&
                it.systemId == telemetryRepository.fcuSystemId
            }
            .map { it.message }
            .filterIsInstance<RcChannels>()
            .onEach { rcChannels ->
                Log.d(TAG, "📻 RC_CHANNELS received: ch1=${rcChannels.chan1Raw} ch2=${rcChannels.chan2Raw} ch3=${rcChannels.chan3Raw} ch4=${rcChannels.chan4Raw}")
            }
    }

    /**
     * Extract all 16 channel values from RC_CHANNELS message.
     */
    fun extractChannelValues(rcChannels: RcChannels): List<Int> {
        return listOf(
            rcChannels.chan1Raw.toInt(),
            rcChannels.chan2Raw.toInt(),
            rcChannels.chan3Raw.toInt(),
            rcChannels.chan4Raw.toInt(),
            rcChannels.chan5Raw.toInt(),
            rcChannels.chan6Raw.toInt(),
            rcChannels.chan7Raw.toInt(),
            rcChannels.chan8Raw.toInt(),
            rcChannels.chan9Raw.toInt(),
            rcChannels.chan10Raw.toInt(),
            rcChannels.chan11Raw.toInt(),
            rcChannels.chan12Raw.toInt(),
            rcChannels.chan13Raw.toInt(),
            rcChannels.chan14Raw.toInt(),
            rcChannels.chan15Raw.toInt(),
            rcChannels.chan16Raw.toInt()
        )
    }

    /**
     * Request RC channel mapping parameters (RCMAP_*).
     */
    suspend fun requestRCMappingParameters(
        parameterRepository: ParameterRepository
    ): Map<String, Int> {
        val params = listOf("RCMAP_ROLL", "RCMAP_PITCH", "RCMAP_THROTTLE", "RCMAP_YAW")
        val paramValues = mutableMapOf<String, Int>()

        params.forEach { paramName ->
            val result = parameterRepository.requestParameter(paramName)
            result.onSuccess { parameter ->
                paramValues[paramName] = parameter.value.toInt()
                Log.d(TAG, "✓ Received $paramName = ${parameter.value.toInt()}")
            }.onFailure { error ->
                Log.e(TAG, "Error loading $paramName: ${error.message}")
            }
            delay(100)
        }

        return paramValues
    }

    /**
     * Save RC channel calibration parameters (RC{n}_MIN, RC{n}_MAX, RC{n}_TRIM).
     */
    suspend fun saveChannelCalibration(
        channelNumber: Int,
        minValue: Int,
        maxValue: Int,
        trimValue: Int,
        parameterRepository: ParameterRepository
    ): Triple<Boolean, Boolean, Boolean> {
        var minSuccess = false
        var maxSuccess = false
        var trimSuccess = false

        try {
            // Get the parameter type from existing parameter
            val existingParam = parameterRepository.requestParameter("RC${channelNumber}_MIN")
            val paramType = existingParam.getOrNull()?.type
                ?: com.divpundir.mavlink.definitions.common.MavParamType.REAL32.wrap()

            // Save MIN
            parameterRepository.setParameter("RC${channelNumber}_MIN", minValue.toFloat(), paramType)
                .onSuccess {
                    minSuccess = true
                    Log.d(TAG, "✓ Saved RC${channelNumber}_MIN = $minValue")
                }
                .onFailure {
                    Log.e(TAG, "Failed to save RC${channelNumber}_MIN")
                }
            delay(50)

            // Save MAX
            parameterRepository.setParameter("RC${channelNumber}_MAX", maxValue.toFloat(), paramType)
                .onSuccess {
                    maxSuccess = true
                    Log.d(TAG, "✓ Saved RC${channelNumber}_MAX = $maxValue")
                }
                .onFailure {
                    Log.e(TAG, "Failed to save RC${channelNumber}_MAX")
                }
            delay(50)

            // Save TRIM
            parameterRepository.setParameter("RC${channelNumber}_TRIM", trimValue.toFloat(), paramType)
                .onSuccess {
                    trimSuccess = true
                    Log.d(TAG, "✓ Saved RC${channelNumber}_TRIM = $trimValue")
                }
                .onFailure {
                    Log.e(TAG, "Failed to save RC${channelNumber}_TRIM")
                }
            delay(50)

        } catch (e: Exception) {
            Log.e(TAG, "Exception saving parameters for CH$channelNumber", e)
        }

        return Triple(minSuccess, maxSuccess, trimSuccess)
    }
}
