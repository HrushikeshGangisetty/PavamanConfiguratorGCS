package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.api.wrap
import com.example.pavamanconfiguratorgcs.data.models.Parameter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.cancellation.CancellationException

/**
 * Unified repository for managing MAVLink parameters
 * Handles fetching, updating, and caching of flight controller parameters
 * with robust retry logic and metadata support
 */
class ParameterRepository(
    private val connection: CoroutinesMavConnection,
    private val scope: CoroutineScope,
    private val gcsSystemId: UByte = 255u,
    private val gcsComponentId: UByte = 190u,
    private val fcuSystemId: UByte = 1u,
    private val fcuComponentId: UByte = 1u
) {
    companion object {
        private const val TAG = "ParameterRepository"
        private const val PARAM_TIMEOUT_MS = 6000L
        private const val MAX_RETRIES = 5
        private const val BETWEEN_PARAM_DELAY_MS = 200L
    }

    // Parameters storage
    private val _parameters = MutableStateFlow<Map<String, Parameter>>(emptyMap())
    val parameters: StateFlow<Map<String, Parameter>> = _parameters.asStateFlow()

    // Loading progress
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress(0, 0))
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    // Track received parameter indices
    private val receivedIndices = mutableSetOf<UShort>()
    private var expectedParamCount: UShort = 0u
    private val inFlightWrites = mutableSetOf<String>()

    // Flag to prevent duplicate fetch requests
    private var isFetching = false

    private var parameterJob: Job? = null

    // Metadata provider for parameter descriptions, units, etc.
    private val metadataProvider = ParameterMetadataProvider()

    data class LoadingProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean = false,
        val errorMessage: String? = null
    )

    // Result types for compatibility with existing code
    sealed class ParameterResult {
        data class Success(val parameter: ParameterValue) : ParameterResult()
        data class Error(val message: String) : ParameterResult()
        object Timeout : ParameterResult()
    }

    data class ParameterValue(
        val name: String,
        val value: Float,
        val type: MavParamType,
        val index: Int = -1,
        val component: Int = -1
    )

    init {
        // Start listening for PARAM_VALUE messages
        startListening()
    }

    /**
     * Start listening for PARAM_VALUE messages from flight controller
     */
    private fun startListening() {
        Log.d(TAG, "🎧 Starting ParamValue message listener...")
        parameterJob = scope.launch {
            try {
                Log.d(TAG, "🎧 ParamValue collector launched, waiting for messages...")
                connection.mavFrame
                    .buffer(capacity = 2000) // Large buffer to handle rapid parameter influx
                    .map { it.message }
                    .filterIsInstance<ParamValue>()
                    .collect { paramValue ->
                        Log.v(TAG, "📨 ParamValue message received in collector")
                        handleParamValue(paramValue)
                    }
                Log.d(TAG, "🎧 ParamValue collector completed (connection closed?)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in ParamValue collector", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * MAIN COMMAND: Request all parameters from flight controller
     * This sends PARAM_REQUEST_LIST MAVLink message
     */
    suspend fun requestAllParameters(timeoutMs: Long = 120000L, forceRefresh: Boolean = false): Map<String, ParameterValue> = withContext(Dispatchers.IO) {
        try {
            // Check if already fetching
            if (isFetching) {
                Log.d(TAG, "⏳ Parameter fetch already in progress, skipping duplicate request")
                // Return current cached parameters as ParameterValue map
                return@withContext _parameters.value.mapValues { (_, param) ->
                    ParameterValue(
                        name = param.name,
                        value = param.value,
                        type = mavParamTypeFromEnumValue(param.type),
                        index = param.index.toInt(),
                        component = -1
                    )
                }
            }

            // Check if parameters already loaded (skip if forceRefresh is true)
            if (!forceRefresh && _parameters.value.isNotEmpty() && expectedParamCount > 0u && receivedIndices.size >= expectedParamCount.toInt()) {
                Log.d(TAG, "✅ Parameters already loaded (${_parameters.value.size} params), skipping fetch")
                return@withContext _parameters.value.mapValues { (_, param) ->
                    ParameterValue(
                        name = param.name,
                        value = param.value,
                        type = mavParamTypeFromEnumValue(param.type),
                        index = param.index.toInt(),
                        component = -1
                    )
                }
            }

            if (forceRefresh) {
                Log.d(TAG, "🔄 Force refresh requested - clearing cache and fetching fresh parameters")
            }

            isFetching = true
            Log.d(TAG, "📋 Requesting all parameters...")

            // **CRITICAL**: Load metadata FIRST before requesting parameters
            ensureMetadataLoaded()

            // Reset state
            receivedIndices.clear()
            expectedParamCount = 0u
            _loadingProgress.value = LoadingProgress(0, 0)

            // COMMAND: Send PARAM_REQUEST_LIST
            val request = ParamRequestList(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId
            )

            connection.trySendUnsignedV2(
                systemId = gcsSystemId,
                componentId = gcsComponentId,
                payload = request
            )

            Log.i(TAG, "✅ PARAM_REQUEST_LIST sent to FC")

            // Wait for completion
            waitForParameterCompletion(timeoutMs)

            isFetching = false

            // Return as ParameterValue map
            _parameters.value.mapValues { (_, param) ->
                ParameterValue(
                    name = param.name,
                    value = param.value,
                    type = mavParamTypeFromEnumValue(param.type),
                    index = param.index.toInt(),
                    component = -1
                )
            }

        } catch (e: Exception) {
            isFetching = false
            Log.e(TAG, "❌ Failed to request parameters", e)
            _loadingProgress.value = LoadingProgress(
                current = receivedIndices.size,
                total = expectedParamCount.toInt(),
                errorMessage = e.message
            )
            emptyMap()
        }
    }

    /**
     * Ensure metadata is loaded before processing parameters
     */
    private suspend fun ensureMetadataLoaded() {
        try {
            if (metadataProvider.isMetadataLoaded()) {
                Log.i(TAG, "✅ Metadata already loaded")
                return
            }

            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "🔄 STARTING METADATA LOAD PROCESS")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            val vehicleType = detectVehicleType() ?: "copter"
            Log.i(TAG, "Vehicle type detected: $vehicleType")

            val result = metadataProvider.loadMetadata(vehicleType)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "✅ Metadata loaded successfully")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ METADATA LOAD FAILED: ${error.message}")
                    Log.w(TAG, "⚠️ Continuing without metadata - parameters will have no descriptions")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION IN ensureMetadataLoaded: ${e.message}")
        }
    }

    /**
     * Handle incoming PARAM_VALUE message from flight controller
     */
    private fun handleParamValue(paramValue: ParamValue) {
        try {
            // Update expected count on first message
            if (expectedParamCount == 0.toUShort()) {
                expectedParamCount = paramValue.paramCount
                Log.d(TAG, "📊 Expected parameters: $expectedParamCount")
            }

            // Extract parameter name (remove null terminators)
            val paramName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)

            // Check if this is a write confirmation
            if (inFlightWrites.contains(paramName)) {
                inFlightWrites.remove(paramName)
                Log.d(TAG, "✅ Write confirmed: $paramName = ${paramValue.paramValue}")
            }

            // Get metadata for this parameter
            val metadata = metadataProvider.getMetadata(paramName)

            // Create parameter object with metadata
            val parameter = Parameter(
                name = paramName,
                value = paramValue.paramValue,
                type = paramValue.paramType,
                index = paramValue.paramIndex,
                originalValue = paramValue.paramValue,
                displayName = metadata.displayName.ifEmpty { paramName },
                description = metadata.description,
                units = metadata.units,
                minValue = metadata.minValue,
                maxValue = metadata.maxValue,
                defaultValue = metadata.defaultValue,
                rebootRequired = metadata.rebootRequired
            )

            // Add to collection
            val currentParams = _parameters.value.toMutableMap()
            currentParams[paramName] = parameter
            _parameters.value = currentParams

            // Track index (handle invalid index 65535)
            val actualIndex = paramValue.paramIndex
            if (actualIndex != 65535u.toUShort()) {
                receivedIndices.add(actualIndex)
                if (receivedIndices.size <= 10 || receivedIndices.size % 50 == 0) {
                    Log.d(TAG, "📥 Progress: ${receivedIndices.size}/${expectedParamCount}")
                }
            }

            // Update progress
            _loadingProgress.value = LoadingProgress(
                current = receivedIndices.size,
                total = expectedParamCount.toInt()
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling PARAM_VALUE", e)
        }
    }

    /**
     * Wait for all parameters to be received with timeout
     */
    private suspend fun waitForParameterCompletion(overallTimeout: Long) {
        val startTime = System.currentTimeMillis()
        val noProgressTimeout = 5_000L
        var lastProgressTime = System.currentTimeMillis()
        var lastReceivedCount = 0
        var recoveryAttempts = 0
        val maxRecoveryAttempts = 3

        while (true) {
            delay(100)

            val currentReceivedCount = receivedIndices.size

            // Update last progress time if we received new parameters
            if (currentReceivedCount > lastReceivedCount) {
                lastProgressTime = System.currentTimeMillis()
                lastReceivedCount = currentReceivedCount
                recoveryAttempts = 0
            }

            // Check if all parameters received
            if (expectedParamCount > 0u && receivedIndices.size >= expectedParamCount.toInt()) {
                _loadingProgress.value = LoadingProgress(
                    current = receivedIndices.size,
                    total = expectedParamCount.toInt(),
                    isComplete = true
                )
                Log.i(TAG, "✅ All parameters received: ${receivedIndices.size}/${expectedParamCount}")
                break
            }

            // Check no-progress timeout
            val timeSinceProgress = System.currentTimeMillis() - lastProgressTime
            if (expectedParamCount > 0u && timeSinceProgress > noProgressTimeout && recoveryAttempts < maxRecoveryAttempts) {
                val missing = expectedParamCount.toInt() - receivedIndices.size
                Log.w(TAG, "⏱️ No progress timeout (attempt ${recoveryAttempts + 1}/$maxRecoveryAttempts): Missing $missing")

                requestMissingParameters()
                recoveryAttempts++
                lastProgressTime = System.currentTimeMillis()
                delay(5000)
            }

            // After max recovery attempts, give up
            if (recoveryAttempts >= maxRecoveryAttempts) {
                val finalMissing = expectedParamCount.toInt() - receivedIndices.size
                Log.w(TAG, "⚠️ Incomplete: Still missing $finalMissing parameters")
                _loadingProgress.value = LoadingProgress(
                    current = receivedIndices.size,
                    total = expectedParamCount.toInt(),
                    isComplete = false,
                    errorMessage = "Incomplete: $finalMissing parameters missing"
                )
                break
            }

            // Check overall timeout
            if (System.currentTimeMillis() - startTime > overallTimeout) {
                val missing = if (expectedParamCount > 0u) expectedParamCount.toInt() - receivedIndices.size else 0
                Log.e(TAG, "❌ Overall timeout: Missing $missing")
                _loadingProgress.value = LoadingProgress(
                    current = receivedIndices.size,
                    total = expectedParamCount.toInt(),
                    isComplete = false,
                    errorMessage = "Timeout: $missing parameters missing"
                )
                break
            }
        }
    }

    /**
     * Request missing parameters individually by index
     */
    private suspend fun requestMissingParameters() {
        Log.d(TAG, "🔄 Requesting missing parameters...")

        for (index in 0 until expectedParamCount.toInt()) {
            if (!receivedIndices.contains(index.toUShort())) {
                val request = ParamRequestRead(
                    targetSystem = fcuSystemId,
                    targetComponent = fcuComponentId,
                    paramId = "",
                    paramIndex = index.toShort()
                )

                connection.trySendUnsignedV2(
                    systemId = gcsSystemId,
                    componentId = gcsComponentId,
                    payload = request
                )

                delay(50)
            }
        }
    }

    /**
     * Set a parameter on the autopilot with retry logic - returns ParameterResult
     */
    suspend fun setParameter(
        paramName: String,
        value: Float,
        paramType: MavParamType = MavParamType.REAL32,
        force: Boolean = false
    ): ParameterResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "setParameter: $paramName = $value (type: $paramType)")

        val normalizedRaw = paramName.trim().uppercase()
        val paramId16 = normalizedRaw.take(16)

        var currentParam = _parameters.value[paramId16]
        var effectiveParamType = paramType

        if (currentParam != null) {
            effectiveParamType = mavParamTypeFromEnumValue(currentParam.type)
        }

        // Check if already set
        if (!force && currentParam != null && floatEquals(currentParam.value, value)) {
            Log.d(TAG, "Parameter $paramName already set to $value, skipping")
            return@withContext ParameterResult.Success(
                ParameterValue(paramId16, value, effectiveParamType, currentParam.index.toInt(), -1)
            )
        }

        val paramIdForMessage = paramId16.padEnd(16, '\u0000')
        val paramSet = ParamSet(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            paramId = paramIdForMessage,
            paramValue = value,
            paramType = effectiveParamType.wrap()
        )

        var retries = MAX_RETRIES
        var result: ParameterResult = ParameterResult.Timeout

        while (retries > 0) {
            try {
                Log.d(TAG, "Sending PARAM_SET for $paramName (attempt ${MAX_RETRIES - retries + 1}/$MAX_RETRIES)")

                connection.trySendUnsignedV2(
                    systemId = gcsSystemId,
                    componentId = gcsComponentId,
                    payload = paramSet
                )

                result = waitForParameterEcho(paramId16, PARAM_TIMEOUT_MS)

                if (result is ParameterResult.Success) {
                    Log.d(TAG, "Parameter $paramName set successfully to $value")
                    return@withContext result
                }

                // Fallback: request parameter directly
                if (result is ParameterResult.Timeout) {
                    Log.w(TAG, "No PARAM_VALUE echo for $paramName, requesting parameter as fallback")
                    val req = requestParameter(paramId16)
                    if (req is ParameterResult.Success && floatEquals(req.parameter.value, value)) {
                        Log.d(TAG, "Parameter $paramName appears applied after request fallback")
                        return@withContext req
                    }
                }

                Log.w(TAG, "Retry $paramName (${MAX_RETRIES - retries + 1}/$MAX_RETRIES): $result")
                retries--
                if (retries > 0) delay(150)
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
     * SAVE COMMAND: Set parameter value (original signature for compatibility)
     */
    suspend fun setParameter(
        paramName: String,
        paramValue: Float,
        paramType: MavEnumValue<MavParamType>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Setting parameter: $paramName = $paramValue")

            inFlightWrites.add(paramName)

            val paramSet = ParamSet(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                paramId = paramName,
                paramValue = paramValue,
                paramType = paramType
            )

            connection.trySendUnsignedV2(
                systemId = gcsSystemId,
                componentId = gcsComponentId,
                payload = paramSet
            )

            // Wait for confirmation
            val confirmed = withTimeoutOrNull(5.seconds) {
                while (inFlightWrites.contains(paramName)) {
                    delay(100)
                }
                true
            }

            if (confirmed != true) {
                inFlightWrites.remove(paramName)
                Log.w(TAG, "⏱️ Timeout waiting for confirmation: $paramName")
                return@withContext Result.failure(Exception("Timeout waiting for confirmation"))
            }

            Log.i(TAG, "✅ Parameter saved: $paramName = $paramValue")
            Result.success(Unit)

        } catch (e: Exception) {
            inFlightWrites.remove(paramName)
            Log.e(TAG, "❌ Failed to set parameter: $paramName", e)
            Result.failure(e)
        }
    }

    /**
     * Wait for PARAM_VALUE echo
     */
    private suspend fun waitForParameterEcho(
        paramId: String,
        timeoutMs: Long
    ): ParameterResult = withTimeoutOrNull(timeoutMs) {
        val expected = paramId.replace("\u0000", "").trim().uppercase()

        connection.mavFrame
            .filter { frame ->
                frame.message is ParamValue && frame.systemId == fcuSystemId
            }
            .map { frame -> frame }
            .filter { frame ->
                val paramValue = frame.message as ParamValue
                val recv = paramValue.paramId.replace("\u0000", "").trim().uppercase()
                recv == expected || recv.startsWith(expected) || expected.startsWith(recv)
            }
            .first()
            .let { frame ->
                val paramValue = frame.message as ParamValue
                val typeValue = paramValue.paramType.value.toInt()
                val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue } ?: MavParamType.REAL32
                val recvName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)

                val parameter = ParameterValue(
                    name = recvName,
                    value = paramValue.paramValue,
                    type = paramTypeEnum,
                    index = paramValue.paramIndex.toInt(),
                    component = frame.componentId.toInt()
                )

                // Update cache
                val metadata = metadataProvider.getMetadata(recvName)
                val param = Parameter(
                    name = recvName,
                    value = paramValue.paramValue,
                    type = paramValue.paramType,
                    index = paramValue.paramIndex,
                    originalValue = paramValue.paramValue,
                    displayName = metadata.displayName.ifEmpty { recvName },
                    description = metadata.description,
                    units = metadata.units,
                    minValue = metadata.minValue,
                    maxValue = metadata.maxValue,
                    defaultValue = metadata.defaultValue,
                    rebootRequired = metadata.rebootRequired
                )
                _parameters.update { it + (recvName to param) }

                Log.d(TAG, "Received PARAM_VALUE: $recvName = ${paramValue.paramValue}")
                ParameterResult.Success(parameter)
            }
    } ?: ParameterResult.Timeout

    /**
     * Get a parameter from cache without sending a MAVLink request.
     * Returns null if not in cache.
     */
    fun getCachedParameter(paramName: String): Parameter? {
        val normalizedName = paramName.trim().uppercase().take(16)
        return _parameters.value[normalizedName]
    }

    /**
     * Request a specific parameter from the autopilot
     * Checks cache first, then sends MAVLink request if not found
     */
    suspend fun requestParameter(paramName: String): ParameterResult = withContext(Dispatchers.IO) {
        val paramIdRaw = paramName.trim().uppercase()

        // Check cache first
        val cached = getCachedParameter(paramIdRaw)
        if (cached != null) {
            Log.d(TAG, "Parameter $paramIdRaw found in cache: ${cached.value}")
            return@withContext ParameterResult.Success(
                ParameterValue(
                    name = cached.name,
                    value = cached.value,
                    type = mavParamTypeFromEnumValue(cached.type),
                    index = cached.index.toInt(),
                    component = -1
                )
            )
        }

        Log.d(TAG, "Requesting parameter from FC: $paramName")

        val paramId = paramIdRaw.take(16).padEnd(16, '\u0000')

        val paramRequest = ParamRequestRead(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            paramId = paramId,
            paramIndex = -1
        )

        return@withContext try {
            connection.trySendUnsignedV2(
                systemId = gcsSystemId,
                componentId = gcsComponentId,
                payload = paramRequest
            )

            waitForParameterEcho(paramIdRaw.take(16), 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting parameter $paramName", e)
            ParameterResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Request specific parameter by name (original signature for compatibility)
     */
    suspend fun requestParameter(paramName: String, returnResult: Boolean = false): Result<Parameter> = withContext(Dispatchers.IO) {
        try {
            val request = ParamRequestRead(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                paramId = paramName,
                paramIndex = -1
            )

            connection.trySendUnsignedV2(
                systemId = gcsSystemId,
                componentId = gcsComponentId,
                payload = request
            )

            // Wait for response
            val paramValue = withTimeout(3.seconds) {
                connection.mavFrame
                    .map { it.message }
                    .filterIsInstance<ParamValue>()
                    .first { it.paramId.trimEnd('\u0000') == paramName }
            }

            val metadata = metadataProvider.getMetadata(paramName)
            val parameter = Parameter(
                name = paramName,
                value = paramValue.paramValue,
                type = paramValue.paramType,
                index = paramValue.paramIndex,
                originalValue = paramValue.paramValue,
                displayName = metadata.displayName.ifEmpty { paramName },
                description = metadata.description,
                units = metadata.units,
                minValue = metadata.minValue,
                maxValue = metadata.maxValue,
                defaultValue = metadata.defaultValue,
                rebootRequired = metadata.rebootRequired
            )

            Result.success(parameter)

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Timeout requesting parameter: $paramName")
            Result.failure(Exception("Timeout"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request parameter: $paramName", e)
            Result.failure(e)
        }
    }

    /**
     * Apply frame-related parameters sequentially
     */
    suspend fun applyFrameParameters(frameParams: Map<String, Float>): Map<String, ParameterResult> = withContext(Dispatchers.IO) {
        val allowedParams = setOf("FRAME", "FRAME_CLASS", "FRAME_TYPE")
        val normalized = frameParams.mapKeys { it.key.trim().uppercase().take(16) }
            .filter { (k, _) -> allowedParams.contains(k) }

        Log.d(TAG, "Applying frame parameters: ${normalized.keys}")

        val results = mutableMapOf<String, ParameterResult>()

        if (normalized.isEmpty()) {
            Log.w(TAG, "No supported frame parameters provided")
            allowedParams.forEach { results[it] = ParameterResult.Error("Not provided") }
            return@withContext results.toMap()
        }

        for ((name, value) in normalized) {
            try {
                val res = setParameter(name, value)
                results[name] = res

                if (res is ParameterResult.Success) {
                    Log.d(TAG, "Applied frame param $name = $value")
                } else {
                    Log.w(TAG, "Failed to apply frame param $name: $res")
                }

                delay(BETWEEN_PARAM_DELAY_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception while applying frame parameter $name", e)
                results[name] = ParameterResult.Error(e.message ?: "Unknown error")
            }
        }

        // Verify parameters
        for ((name, expected) in normalized) {
            try {
                val read = requestParameter(name)
                when (read) {
                    is ParameterResult.Success -> {
                        if (!floatEquals(read.parameter.value, expected)) {
                            Log.w(TAG, "Verification mismatch for $name: expected=$expected read=${read.parameter.value}")
                            if (results[name] is ParameterResult.Success) {
                                results[name] = ParameterResult.Error("Verification mismatch: read=${read.parameter.value}")
                            }
                        } else {
                            Log.d(TAG, "Verification OK for $name: $expected")
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
                delay(50)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception while verifying frame parameter $name", e)
            }
        }

        results.toMap()
    }

    /**
     * Find parameters by prefix
     */
    suspend fun findParametersByPrefix(prefix: String, timeoutMs: Long = 2000L): Map<String, ParameterValue> = withContext(Dispatchers.IO) {
        val normPrefix = prefix.trim().uppercase()

        val request = ParamRequestList(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId
        )

        try {
            connection.trySendUnsignedV2(
                systemId = gcsSystemId,
                componentId = gcsComponentId,
                payload = request
            )

            val found = mutableMapOf<String, ParameterValue>()

            withTimeoutOrNull(timeoutMs) {
                connection.mavFrame
                    .filter { frame -> frame.message is ParamValue && frame.systemId == fcuSystemId }
                    .map { frame -> frame }
                    .collect { frame ->
                        val paramValue = frame.message as ParamValue
                        val recvName = paramValue.paramId.replace("\u0000", "").trim().uppercase().take(16)
                        if (recvName.startsWith(normPrefix) || recvName.contains(normPrefix)) {
                            val typeValue = paramValue.paramType.value.toInt()
                            val paramTypeEnum = MavParamType.entries.find { it.value.toInt() == typeValue } ?: MavParamType.REAL32
                            val pv = ParameterValue(
                                name = recvName,
                                value = paramValue.paramValue,
                                type = paramTypeEnum,
                                index = paramValue.paramIndex.toInt(),
                                component = frame.componentId.toInt()
                            )
                            found[recvName] = pv
                        }
                    }
            }

            Log.d(TAG, "findParametersByPrefix: found ${found.size} params matching prefix '$prefix'")
            found.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "findParametersByPrefix failed: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Get cached parameters snapshot
     */
    fun getCachedParametersSnapshot(): Map<String, ParameterValue> {
        return _parameters.value.mapValues { (_, param) ->
            ParameterValue(
                name = param.name,
                value = param.value,
                type = mavParamTypeFromEnumValue(param.type),
                index = param.index.toInt(),
                component = -1
            )
        }
    }

    fun clearParameters() {
        _parameters.value = emptyMap()
        receivedIndices.clear()
        expectedParamCount = 0u
        _loadingProgress.value = LoadingProgress(0, 0)
    }

    fun cleanup() {
        parameterJob?.cancel()
    }

    /**
     * Detect vehicle type from system ID or default to copter
     */
    private fun detectVehicleType(): String? {
        return "copter"
    }

    // Utility functions
    private fun floatEquals(a: Float, b: Float, eps: Float = 1e-5f): Boolean =
        kotlin.math.abs(a - b) <= eps

    private fun mavParamTypeFromEnumValue(enumValue: MavEnumValue<MavParamType>): MavParamType {
        val typeValue = enumValue.value.toInt()
        return MavParamType.entries.find { it.value.toInt() == typeValue } ?: MavParamType.REAL32
    }
}
