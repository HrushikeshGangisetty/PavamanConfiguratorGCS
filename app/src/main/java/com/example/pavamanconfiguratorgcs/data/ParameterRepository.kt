package com.example.pavamanconfiguratorgcs.data

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.definitions.common.*
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repository for handling MAVLink parameter operations.
 * Implements parameter reading, writing, and monitoring following ArduPilot protocol.
 */
class ParameterRepository(
    private val telemetryRepository: TelemetryRepository
) {
    companion object {
        private const val TAG = "ParameterRepository"
        private const val PARAM_TIMEOUT_MS = 700L
        private const val MAX_RETRIES = 3
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Store parameters locally
    private val _parameters = MutableStateFlow<Map<String, ParameterValue>>(emptyMap())

    data class ParameterValue(
        val name: String,
        val value: Float,
        val type: MavParamType,
        val index: Int = -1
    )

    sealed class ParameterResult {
        data class Success(val parameter: ParameterValue) : ParameterResult()
        data class Error(val message: String) : ParameterResult()
        object Timeout : ParameterResult()
    }

    /**
     * Set a parameter on the autopilot with retry logic.
     * Follows the MAVLink PARAM_SET -> PARAM_VALUE echo pattern.
     */
    suspend fun setParameter(
        paramName: String,
        value: Float,
        paramType: MavParamType = MavParamType.REAL32,
        force: Boolean = false
    ): ParameterResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "setParameter: $paramName = $value (type: $paramType)")

        // Check if already set (optimization)
        val currentParam = _parameters.value[paramName]
        if (!force && currentParam != null && currentParam.value == value) {
            Log.d(TAG, "Parameter $paramName already set to $value, skipping")
            return@withContext ParameterResult.Success(currentParam)
        }

        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.e(TAG, "No active connection")
            return@withContext ParameterResult.Error("No active connection")
        }

        // Prepare parameter ID (max 16 characters)
        val paramId = paramName.take(16)

        val paramSet = ParamSet(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId,
            paramId = paramId,
            paramValue = value,
            paramType = paramType.wrap()
        )

        var retries = MAX_RETRIES
        var result: ParameterResult = ParameterResult.Timeout

        while (retries > 0) {
            try {
                Log.d(TAG, "Sending PARAM_SET for $paramName (attempt ${MAX_RETRIES - retries + 1}/$MAX_RETRIES)")

                // Send PARAM_SET message
                connection.trySendUnsignedV2(
                    systemId = telemetryRepository.gcsSystemId,
                    componentId = telemetryRepository.gcsComponentId,
                    payload = paramSet
                )

                // Wait for PARAM_VALUE echo with timeout
                result = waitForParameterEcho(paramName, PARAM_TIMEOUT_MS)

                if (result is ParameterResult.Success) {
                    Log.d(TAG, "Parameter $paramName set successfully to $value")
                    return@withContext result
                }

                Log.w(TAG, "Retry $paramName (${MAX_RETRIES - retries + 1}/$MAX_RETRIES): $result")
                retries--
                if (retries > 0) {
                    delay(100) // Small delay between retries
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting parameter $paramName", e)
                result = ParameterResult.Error(e.message ?: "Unknown error")
                retries--
            }
        }

        Log.e(TAG, "Failed to set parameter $paramName after $MAX_RETRIES attempts")
        result
    }

    /**
     * Wait for PARAM_VALUE message that echoes our parameter set request.
     */
    private suspend fun waitForParameterEcho(
        paramName: String,
        timeoutMs: Long
    ): ParameterResult = withTimeoutOrNull(timeoutMs) {
        telemetryRepository.mavFrame
            .filter { frame ->
                frame.message is ParamValue &&
                frame.systemId == telemetryRepository.fcuSystemId &&
                frame.componentId == telemetryRepository.fcuComponentId
            }
            .map { it.message as ParamValue }
            .filter { paramValue ->
                paramValue.paramId == paramName
            }
            .first()
            .let { paramValue ->
                // Extract the type value from the MavEnumValue
                val typeValue = paramValue.paramType.value.toInt()
                val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue }
                    ?: MavParamType.REAL32

                val parameter = ParameterValue(
                    name = paramValue.paramId,
                    value = paramValue.paramValue,
                    type = paramTypeEnum,
                    index = paramValue.paramIndex.toInt()
                )

                // Update local cache
                _parameters.update { it + (paramValue.paramId to parameter) }

                Log.d(TAG, "Received PARAM_VALUE: ${paramValue.paramId} = ${paramValue.paramValue}")
                ParameterResult.Success(parameter)
            }
    } ?: ParameterResult.Timeout

    /**
     * Request a specific parameter from the autopilot.
     */
    suspend fun requestParameter(paramName: String): ParameterResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Requesting parameter: $paramName")

        val connection = telemetryRepository.connection
        if (connection == null) {
            return@withContext ParameterResult.Error("No active connection")
        }

        val paramRequest = ParamRequestRead(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId,
            paramId = paramName.take(16),
            paramIndex = -1 // -1 means use param_id instead of index
        )

        try {
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = paramRequest
            )

            waitForParameterEcho(paramName, 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting parameter $paramName", e)
            ParameterResult.Error(e.message ?: "Unknown error")
        }
    }
}
