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
        // Increase default timeout to be robust on slower links
        // Increased to 6000ms to allow slower links and firmwares more time to echo PARAM_VALUE
        private const val PARAM_TIMEOUT_MS = 6000L
        // Increase retries to handle flaky links
        private const val MAX_RETRIES = 5
        // When setting multiple params give the FCU a little time to apply
        private const val BETWEEN_PARAM_DELAY_MS = 200L
    }

    // no unused scope; coroutine usage uses withContext / caller scope

    // Store parameters locally
    private val _parameters = MutableStateFlow<Map<String, ParameterValue>>(emptyMap())

    /**
     * Return a snapshot of currently cached parameters (keys are normalized names used internally).
     */
    @Suppress("unused")
    fun getCachedParametersSnapshot(): Map<String, ParameterValue> = _parameters.value

    data class ParameterValue(
        val name: String,
        val value: Float,
        val type: MavParamType,
        val index: Int = -1,
        // component id where this PARAM_VALUE was observed (helps target writes)
        val component: Int = -1
    )

    sealed class ParameterResult {
        data class Success(val parameter: ParameterValue) : ParameterResult()
        data class Error(val message: String) : ParameterResult()
        object Timeout : ParameterResult()
    }

    // Utility: approximate equality for floats to avoid spurious skips
    private fun floatEquals(a: Float, b: Float, eps: Float = 1e-5f): Boolean = kotlin.math.abs(a - b) <= eps

    /**
     * Set a parameter on the autopilot with retry logic.
     * Follows the MAVLink PARAM_SET -> PARAM_VALUE echo pattern.
     * If PARAM_VALUE echo is not received, falls back to requesting the parameter and
     * accepting the value if it matches (some firmwares don't echo immediately).
     */
    suspend fun setParameter(
        paramName: String,
        value: Float,
        paramType: MavParamType = MavParamType.REAL32,
        force: Boolean = false
    ): ParameterResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "setParameter: $paramName = $value (requested type: $paramType)")

        // Normalize the param name to uppercase and use the 16-char ID the firmware expects
        val normalizedRaw = paramName.trim().uppercase()
        val paramId16 = normalizedRaw.take(16)

        // Check cache for existing parameter
        var currentParam = _parameters.value[paramId16]

        // If we don't have a cached type and caller didn't specify a concrete type, try to request the parameter first
        var effectiveParamType = paramType
        if (currentParam != null) {
            effectiveParamType = currentParam.type
        } else if (paramType == MavParamType.REAL32) {
            // Try to populate the cache for this parameter so we send the correct type (important for integer parameters)
            Log.d(TAG, "No cached param for $paramId16 - requesting its current value to learn type before set")
            val r = requestParameter(paramId16)
            if (r is ParameterResult.Success) {
                currentParam = r.parameter
                effectiveParamType = currentParam.type
                Log.d(TAG, "Discovered type for $paramId16 = $effectiveParamType via request")
            } else {
                Log.w(TAG, "Could not determine type for $paramId16 before set (request returned $r), will use provided/default type: $paramType")
            }
        }

        // Optimization: skip if already set and not forced
        if (!force && currentParam != null && floatEquals(currentParam.value, value)) {
            Log.d(TAG, "Parameter $paramName already set to $value (cached), skipping")
            return@withContext ParameterResult.Success(currentParam)
        }

        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.e(TAG, "No active connection")
            return@withContext ParameterResult.Error("No active connection")
        }

        // Prepare parameter ID (max 16 characters). Pad/truncate to be consistent.
        val paramIdForMessage = paramId16.padEnd(16, '\u0000')

        // If we have a cached param and it recorded a component that sent PARAM_VALUEs, target that component.
        val targetComponentToUse = currentParam?.component?.toUByte() ?: telemetryRepository.fcuComponentId

        val paramSet = ParamSet(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = targetComponentToUse,
            paramId = paramIdForMessage,
            paramValue = value,
            paramType = effectiveParamType.wrap()
        )

        var retries = MAX_RETRIES
        var result: ParameterResult = ParameterResult.Timeout

        while (retries > 0) {
            try {
                Log.d(TAG, "Sending PARAM_SET for $paramName (attempt ${MAX_RETRIES - retries + 1}/$MAX_RETRIES) with id='$paramId16' and type=$effectiveParamType")

                // Send PARAM_SET message
                connection.trySendUnsignedV2(
                    systemId = telemetryRepository.gcsSystemId,
                    componentId = telemetryRepository.gcsComponentId,
                    payload = paramSet
                )

                // Wait for PARAM_VALUE echo with timeout. Pass the truncated id so matching is consistent.
                result = waitForParameterEcho(paramId16, PARAM_TIMEOUT_MS)

                if (result is ParameterResult.Success) {
                    val successParam = result.parameter
                    // Update cache entry using normalized key
                    val key = successParam.name.replace("\u0000", "").trim().uppercase().take(16)
                    _parameters.update { it + (key to successParam.copy(name = key)) }
                    Log.d(TAG, "Parameter $paramName set successfully to $value (echoed as $key)")
                    return@withContext result
                }

                // If we timed out waiting for an echo, try requesting the parameter directly as a fallback.
                if (result is ParameterResult.Timeout) {
                    Log.w(TAG, "No PARAM_VALUE echo for $paramName, requesting parameter as fallback")
                    val req = requestParameter(paramId16)
                    if (req is ParameterResult.Success) {
                        val got = req.parameter
                        if (floatEquals(got.value, value)) {
                            val key = got.name.replace("\u0000", "").trim().uppercase().take(16)
                            _parameters.update { it + (key to got.copy(name = key)) }
                            Log.d(TAG, "Parameter $paramName appears applied after request fallback: $value (read as $key)")
                            return@withContext ParameterResult.Success(got)
                        } else {
                            Log.w(TAG, "Requested parameter $paramName returned ${got.value}, expected $value")
                        }
                    }
                }

                Log.w(TAG, "Retry $paramName (${MAX_RETRIES - retries + 1}/$MAX_RETRIES): $result")
                retries--
                if (retries > 0) {
                    delay(150) // Small delay between retries
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

        // Final fallback: refresh the full parameter cache and check if FCU applied the value
        try {
            Log.d(TAG, "Attempting final cache refresh to check for eventual application of $paramName")
            val refreshed = requestAllParameters(timeoutMs = 5000L)
            val cached = refreshed[paramId16] ?: _parameters.value[paramId16]
            if (cached != null && floatEquals(cached.value, value)) {
                Log.i(TAG, "Parameter $paramName appears applied after cache refresh: ${cached.value}")
                return@withContext ParameterResult.Success(cached)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Final cache refresh for $paramName failed: ${e.message}")
        }

        // Last-ditch attempt: send one final PARAM_SET and wait longer for an echo
        try {
            Log.d(TAG, "Performing a last-ditch PARAM_SET for $paramName and waiting up to 8s for echo")
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = paramSet
            )
            val finalRes = waitForParameterEcho(paramId16, 8000L)
            if (finalRes is ParameterResult.Success) {
                Log.i(TAG, "Last-ditch PARAM_SET succeeded for $paramName")
                return@withContext finalRes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Last-ditch PARAM_SET for $paramName failed: ${e.message}")
        }

        // Broadcast attempt: send PARAM_SET with targetComponent=0 in case a different component handles param writes
        try {
            Log.d(TAG, "Attempting broadcast PARAM_SET (component=0) for $paramName and waiting up to 8s for echo")
            val broadcastParamSet = ParamSet(
                targetSystem = telemetryRepository.fcuSystemId,
                targetComponent = 0.toUByte(),
                paramId = paramIdForMessage,
                paramValue = value,
                paramType = effectiveParamType.wrap()
            )
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = broadcastParamSet
            )
            val bcRes = waitForParameterEcho(paramId16, 8000L)
            if (bcRes is ParameterResult.Success) {
                Log.i(TAG, "Broadcast PARAM_SET succeeded for $paramName")
                return@withContext bcRes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast PARAM_SET for $paramName failed: ${e.message}")
        }

        result
    }

    /**
     * Wait for PARAM_VALUE message that echoes our parameter set request.
     * Matches using the truncated 16-character param id. Matching is tolerant to padding,
     * truncation and ordering differences across firmwares.
     */
    private suspend fun waitForParameterEcho(
        paramId: String,
        timeoutMs: Long
    ): ParameterResult = withTimeoutOrNull(timeoutMs) {
        // Normalize expected id for robust matching
        val expected = paramId.replace("\u0000", "").trim().uppercase()

        telemetryRepository.mavFrame
            .filter { frame ->
                // Only require the system id to match the FCU. Some firmwares/components reply from a
                // different component id than the one we target; requiring componentId equality can
                // cause us to miss valid PARAM_VALUE echoes. Matching by systemId (and paramId)
                // is robust while still targeted to the vehicle.
                frame.message is ParamValue &&
                frame.systemId == telemetryRepository.fcuSystemId
            }
            .map { frame -> frame } // keep frame so we can capture componentId
            .filter { frame ->
                val paramValue = frame.message as ParamValue
                val recv = paramValue.paramId.replace("\u0000", "").trim().uppercase()
                recv == expected || recv.startsWith(expected) || expected.startsWith(recv) || recv.contains(expected) || expected.contains(recv)
            }
            .first()
            .let { frame ->
                val paramValue = frame.message as ParamValue
                // Extract the type value from the MavEnumValue
                val typeValue = paramValue.paramType.value.toInt()
                val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue }
                    ?: MavParamType.REAL32

                // Normalize the name we store in the cache
                val recvName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)

                val parameter = ParameterValue(
                    name = recvName,
                    value = paramValue.paramValue,
                    type = paramTypeEnum,
                    index = paramValue.paramIndex.toInt(),
                    component = frame.componentId.toInt()
                )

                // Update local cache with normalized key
                _parameters.update { it + (recvName to parameter) }

                Log.d(TAG, "Received PARAM_VALUE: ${recvName} = ${paramValue.paramValue} (component=${frame.componentId})")
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

        val paramIdRaw = paramName.trim().uppercase()
        val paramId = paramIdRaw.take(16).padEnd(16, '\u0000')

        val paramRequest = ParamRequestRead(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId,
            paramId = paramId,
            paramIndex = -1 // -1 means use param_id instead of index
        )

        return@withContext try {
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = paramRequest
            )

            // Wait for the param value and return it
            waitForParameterEcho(paramIdRaw.take(16), 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting parameter $paramName", e)
            ParameterResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Request all parameters from the autopilot using PARAM_REQUEST_LIST and collect ParamValue
     * replies for the given timeout period. Updates the local cache and returns a map of
     * received parameters.
     */
    @Suppress("unused")
    suspend fun requestAllParameters(timeoutMs: Long = 3000L): Map<String, ParameterValue> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Requesting all parameters (timeout ${timeoutMs}ms)")

        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.e(TAG, "No active connection for requestAllParameters")
            return@withContext emptyMap()
        }

        val request = ParamRequestList(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId
        )

        try {
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = request
            )

            val received = mutableMapOf<String, ParameterValue>()

            withTimeoutOrNull(timeoutMs) {
                telemetryRepository.mavFrame
                    .filter { frame ->
                        // Match by systemId only to avoid missing ParamValue frames originating from
                        // different component IDs on the vehicle.
                        frame.message is ParamValue &&
                        frame.systemId == telemetryRepository.fcuSystemId
                    }
                    .map { frame -> frame }
                    .collect { frame ->
                        val paramValue = frame.message as ParamValue
                        val typeValue = paramValue.paramType.value.toInt()
                        val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue }
                            ?: MavParamType.REAL32

                        // Normalize the received param id before storing
                        val recvName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)

                        val parameter = ParameterValue(
                            name = recvName,
                            value = paramValue.paramValue,
                            type = paramTypeEnum,
                            index = paramValue.paramIndex.toInt(),
                            component = frame.componentId.toInt()
                        )

                        received[recvName] = parameter

                        // Update local cache incrementally
                        _parameters.update { it + (recvName to parameter) }

                        Log.d(TAG, "Received PARAM_VALUE (all): ${recvName} = ${paramValue.paramValue} (component=${frame.componentId})")
                     }
            }

            Log.d(TAG, "requestAllParameters - collected ${received.size} parameters")
            return@withContext received.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting all parameters", e)
            return@withContext emptyMap()
        }
    }

    /**
     * Apply only frame-related parameters sequentially.
     *
     * Usage: pass a map where keys are real FCU parameter names (e.g. "FRAME", "MOT_COUNT")
     * and values are the numeric values you want to apply. This function will only attempt
     * to set those parameters and will not touch other parameters.
     *
     * This implementation intentionally skips performing a reboot. Instead it will:
     *  - Set each parameter (with retries) in sequence
     *  - Read back each parameter to verify the value was applied
     *  - Refresh the local cache via requestAllParameters
     *  - Attempt a safe disarm (COMMAND_LONG MAV_CMD_COMPONENT_ARM_DISARM with param1=0)
     *
     * Returns a map of parameter name -> result (Success/Error/Timeout) for the set operation.
     */
    @Suppress("unused")
    suspend fun applyFrameParameters(frameParams: Map<String, Float>): Map<String, ParameterResult> = withContext(Dispatchers.IO) {
        // Only allow these three parameters to be written to the FCU when changing frame
        val allowedParams = setOf("FRAME", "FRAME_CLASS", "FRAME_TYPE")

        // Normalize incoming keys to the firmware keys (uppercase, trimmed, 16-char) and filter
        val normalized = frameParams.mapKeys { it.key.trim().uppercase().take(16) }
            .filter { (k, _) -> allowedParams.contains(k) }

        Log.d(TAG, "Applying frame parameters (no reboot) - will only set: ${allowedParams.intersect(normalized.keys)}")

        val results = mutableMapOf<String, ParameterResult>()

        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.e(TAG, "No active connection - cannot apply frame parameters")
            // Populate results with errors for visibility for the allowed params
            allowedParams.forEach { results[it] = ParameterResult.Error("No active connection") }
            return@withContext results.toMap()
        }

        if (normalized.isEmpty()) {
            Log.w(TAG, "No supported frame parameters provided - nothing to apply")
            // Return a clear map indicating nothing was done
            allowedParams.forEach { results[it] = ParameterResult.Error("Not provided") }
            return@withContext results.toMap()
        }

        // Apply each allowed parameter sequentially and store the result
        for ((name, value) in normalized) {
            try {
                val res = setParameter(name, value)
                results[name] = res

                if (res is ParameterResult.Success) {
                    Log.d(TAG, "Applied frame param $name = $value")
                } else {
                    Log.w(TAG, "Failed to apply frame param $name: $res")
                }

                // Give the FCU a short moment to apply before the next parameter
                delay(BETWEEN_PARAM_DELAY_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception while applying frame parameter $name", e)
                results[name] = ParameterResult.Error(e.message ?: "Unknown error")
            }
        }

        // Verify only the allowed parameters we attempted to set
        for ((name, expected) in normalized) {
            try {
                val read = requestParameter(name)
                when (read) {
                    is ParameterResult.Success -> {
                        if (!floatEquals(read.parameter.value, expected)) {
                            Log.w(TAG, "Verification mismatch for $name: expected=$expected read=${read.parameter.value}")
                            // keep the original result if it exists, but mark mismatch as Error if it was OK
                            if (results[name] is ParameterResult.Success) {
                                results[name] = ParameterResult.Error("Verification mismatch: read=${read.parameter.value}")
                            }
                        } else {
                            Log.d(TAG, "Verification OK for $name: $expected")
                            // ensure success is recorded
                            results[name] = ParameterResult.Success(read.parameter)
                        }
                    }
                    is ParameterResult.Timeout -> {
                        Log.w(TAG, "Verification timeout reading $name")
                        results[name] = ParameterResult.Timeout
                    }
                    is ParameterResult.Error -> {
                        Log.w(TAG, "Verification error reading $name: ${read.message}")
                        results[name] = ParameterResult.Error(read.message)
                    }
                }

                // Small delay between verification reads to avoid flooding
                delay(50)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception while verifying frame parameter $name", e)
            }
        }

        // Do NOT requestAllParameters or write any other parameters here — per requirement

        results.toMap()
    }

    /**
     * Diagnostic helper: request PARAM_REQUEST_LIST and collect ParamValue replies for up to
     * `timeoutMs`. Returns a map of param name -> ParameterValue for params whose normalized
     * name starts with the provided prefix (case-insensitive). This is intended as a
     * bounded diagnostic (short timeout) to discover actual parameter names on the FCU.
     */
    suspend fun findParametersByPrefix(prefix: String, timeoutMs: Long = 2000L): Map<String, ParameterValue> = withContext(Dispatchers.IO) {
        val connection = telemetryRepository.connection
        if (connection == null) {
            Log.w(TAG, "findParametersByPrefix: no active connection")
            return@withContext emptyMap<String, ParameterValue>()
        }

        val normPrefix = prefix.trim().uppercase()

        val request = ParamRequestList(
            targetSystem = telemetryRepository.fcuSystemId,
            targetComponent = telemetryRepository.fcuComponentId
        )

        try {
            connection.trySendUnsignedV2(
                systemId = telemetryRepository.gcsSystemId,
                componentId = telemetryRepository.gcsComponentId,
                payload = request
            )

            val found = mutableMapOf<String, ParameterValue>()

            withTimeoutOrNull(timeoutMs) {
                // Match by systemId only to avoid missing ParamValue frames originating from
                // different component IDs on the vehicle.
                telemetryRepository.mavFrame
                    .filter { frame -> frame.message is ParamValue && frame.systemId == telemetryRepository.fcuSystemId }
                    .map { frame -> frame }
                    .collect { frame ->
                        val paramValue = frame.message as ParamValue
                        val recvName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)
                        if (recvName.startsWith(normPrefix) || recvName.contains(normPrefix)) {
                            val typeValue = paramValue.paramType.value.toInt()
                            val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue } ?: MavParamType.REAL32
                            val pv = ParameterValue(name = recvName, value = paramValue.paramValue, type = paramTypeEnum, index = paramValue.paramIndex.toInt(), component = frame.componentId.toInt())
                            found[recvName] = pv
                        }
                    }
            }

            Log.d(TAG, "findParametersByPrefix: found ${found.size} params matching prefix '$prefix'")
            return@withContext found.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "findParametersByPrefix failed: ${e.message}")
            return@withContext emptyMap()
        }
    }}
