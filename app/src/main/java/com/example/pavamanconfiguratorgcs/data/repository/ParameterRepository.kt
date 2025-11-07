package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.api.MavEnumValue
import com.example.pavamanconfiguratorgcs.data.models.Parameter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * Repository for managing MAVLink parameters
 * Handles fetching, updating, and caching of flight controller parameters
 */
class ParameterRepository(
    private val connection: CoroutinesMavConnection,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ParameterRepository"
        private const val TARGET_SYSTEM: UByte = 1u
        private const val TARGET_COMPONENT: UByte = 1u
        private const val GCS_SYSTEM: UByte = 255u
        private const val GCS_COMPONENT: UByte = 0u
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

    private var parameterJob: Job? = null

    // Metadata provider for parameter descriptions, units, etc.
    private val metadataProvider = ParameterMetadataProvider()

    data class LoadingProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean = false,
        val errorMessage: String? = null
    )

    init {
        // Start listening for PARAM_VALUE messages
        startListening()

        // Load parameter metadata in background
        // DON'T start here - wait until parameters are actually requested
    }

    /**
     * Start listening for PARAM_VALUE messages from flight controller
     */
    private fun startListening() {
        parameterJob = scope.launch {
            connection.mavFrame
                .map { it.message }
                .filterIsInstance<ParamValue>()
                .collect { paramValue ->
                    handleParamValue(paramValue)
                }
        }
    }

    /**
     * MAIN COMMAND: Request all parameters from flight controller
     * This sends PARAM_REQUEST_LIST MAVLink message
     */
    suspend fun requestAllParameters(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Requesting all parameters...")

            // **CRITICAL**: Load metadata FIRST before requesting parameters
            ensureMetadataLoaded()

            // Reset state
            receivedIndices.clear()
            expectedParamCount = 0u
            _loadingProgress.value = LoadingProgress(0, 0)

            // COMMAND: Send PARAM_REQUEST_LIST
            val request = ParamRequestList(
                targetSystem = TARGET_SYSTEM,
                targetComponent = TARGET_COMPONENT
            )

            connection.sendUnsignedV2(
                systemId = GCS_SYSTEM,
                componentId = GCS_COMPONENT,
                payload = request
            )

            Log.i(TAG, "✅ PARAM_REQUEST_LIST sent to FC")

            // Wait for completion
            waitForParameterCompletion()

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to request parameters", e)
            _loadingProgress.value = LoadingProgress(
                current = receivedIndices.size,
                total = expectedParamCount.toInt(),
                errorMessage = e.message
            )
            Result.failure(e)
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

                    // Verify metadata is actually there
                    val testParams = listOf("WPNAV_SPEED", "BATT_CAPACITY", "ANGLE_MAX", "RTL_ALT", "SYSID_THISMAV")
                    testParams.forEach { paramName ->
                        val meta = metadataProvider.getMetadata(paramName)
                        Log.i(TAG, "  Test '$paramName': displayName='${meta.displayName}', units='${meta.units}', default=${meta.defaultValue}")
                    }

                    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                },
                onFailure = { error ->
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.e(TAG, "❌❌❌ METADATA LOAD FAILED ❌❌❌")
                    Log.e(TAG, "Error type: ${error.javaClass.simpleName}")
                    Log.e(TAG, "Error message: ${error.message}")
                    Log.e(TAG, "Stack trace:")
                    error.printStackTrace()
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.w(TAG, "⚠️ Continuing without metadata - parameters will have no descriptions")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e(TAG, "❌❌❌ EXCEPTION IN ensureMetadataLoaded ❌❌❌")
            Log.e(TAG, "Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
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
            val paramName = paramValue.paramId.trimEnd('\u0000')

            // Check if this is a write confirmation
            if (inFlightWrites.contains(paramName)) {
                inFlightWrites.remove(paramName)
                Log.d(TAG, "✅ Write confirmed: $paramName = ${paramValue.paramValue}")
            }

            // Get metadata for this parameter
            val metadata = metadataProvider.getMetadata(paramName)

            // Log metadata enrichment for first few parameters to verify data is coming through
            if (receivedIndices.size < 5) {
                val descPreview = if (metadata.description.length > 50) {
                    metadata.description.take(50) + "..."
                } else {
                    metadata.description
                }
                Log.i(TAG, "📝 Parameter #${receivedIndices.size + 1}: $paramName")
                Log.i(TAG, "   Display Name: '${metadata.displayName}'")
                Log.i(TAG, "   Units: '${metadata.units}'")
                Log.i(TAG, "   Description: '$descPreview'")
                Log.i(TAG, "   Default: ${metadata.defaultValue}")
                Log.i(TAG, "   Range: ${metadata.minValue} - ${metadata.maxValue}")
                Log.i(TAG, "   Reboot Required: ${metadata.rebootRequired}")
            }

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

            // Track index
            if (paramValue.paramIndex != 65535u.toUShort()) {
                receivedIndices.add(paramValue.paramIndex)
            }

            // Update progress
            _loadingProgress.value = LoadingProgress(
                current = receivedIndices.size,
                total = expectedParamCount.toInt()
            )

            if (receivedIndices.size % 50 == 0) {
                Log.d(TAG, "📥 Progress: ${receivedIndices.size}/${expectedParamCount}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling PARAM_VALUE", e)
        }
    }

    /**
     * Wait for all parameters to be received with timeout
     */
    private suspend fun waitForParameterCompletion() {
        val startTime = System.currentTimeMillis()
        val overallTimeout = 120_000L // 2 minutes overall timeout
        val noProgressTimeout = 10_000L // 10 seconds without new parameters
        var lastProgressTime = System.currentTimeMillis()
        var lastReceivedCount = 0

        while (true) {
            delay(100)

            val currentReceivedCount = receivedIndices.size

            // Update last progress time if we received new parameters
            if (currentReceivedCount > lastReceivedCount) {
                lastProgressTime = System.currentTimeMillis()
                lastReceivedCount = currentReceivedCount

                // Log progress every 50 parameters
                if (currentReceivedCount % 50 == 0 && expectedParamCount > 0u) {
                    Log.i(TAG, "📥 Progress: $currentReceivedCount/${expectedParamCount}")
                }
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

            // Check no-progress timeout (no new parameters for 10 seconds)
            val timeSinceProgress = System.currentTimeMillis() - lastProgressTime
            if (expectedParamCount > 0u && timeSinceProgress > noProgressTimeout) {
                val missing = expectedParamCount.toInt() - receivedIndices.size
                Log.w(TAG, "⏱️ No progress timeout: Received ${receivedIndices.size}/${expectedParamCount}, Missing: $missing")

                // Request missing parameters
                if (missing > 0) {
                    Log.i(TAG, "🔄 Attempting to recover $missing missing parameters...")
                    requestMissingParameters()

                    // Reset progress timer and wait for recovery
                    lastProgressTime = System.currentTimeMillis()
                    delay(5000) // Wait 5 seconds for missing params to arrive

                    // Check if we got them all now
                    if (receivedIndices.size >= expectedParamCount.toInt()) {
                        _loadingProgress.value = LoadingProgress(
                            current = receivedIndices.size,
                            total = expectedParamCount.toInt(),
                            isComplete = true
                        )
                        Log.i(TAG, "✅ All parameters received after recovery: ${receivedIndices.size}/${expectedParamCount}")
                        break
                    }

                    // If still missing some, try one more time
                    val stillMissing = expectedParamCount.toInt() - receivedIndices.size
                    if (stillMissing > 0 && stillMissing < 100) {
                        Log.i(TAG, "🔄 Second recovery attempt for $stillMissing parameters...")
                        requestMissingParameters()
                        lastProgressTime = System.currentTimeMillis()
                        delay(5000)
                    }
                }

                // Final check after recovery attempts
                val finalMissing = expectedParamCount.toInt() - receivedIndices.size
                if (finalMissing > 0) {
                    Log.w(TAG, "⚠️ Incomplete: Received ${receivedIndices.size}/${expectedParamCount}, Still missing: $finalMissing")
                    _loadingProgress.value = LoadingProgress(
                        current = receivedIndices.size,
                        total = expectedParamCount.toInt(),
                        isComplete = false,
                        errorMessage = "Incomplete: $finalMissing parameters missing"
                    )
                } else {
                    _loadingProgress.value = LoadingProgress(
                        current = receivedIndices.size,
                        total = expectedParamCount.toInt(),
                        isComplete = true
                    )
                }
                break
            }

            // Check overall timeout (2 minutes total)
            if (System.currentTimeMillis() - startTime > overallTimeout) {
                val missing = if (expectedParamCount > 0u) expectedParamCount.toInt() - receivedIndices.size else 0
                Log.e(TAG, "❌ Overall timeout: Received ${receivedIndices.size}/${expectedParamCount}, Missing: $missing")

                _loadingProgress.value = LoadingProgress(
                    current = receivedIndices.size,
                    total = expectedParamCount.toInt(),
                    isComplete = false,
                    errorMessage = "Overall timeout: $missing parameters missing"
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
                    targetSystem = TARGET_SYSTEM,
                    targetComponent = TARGET_COMPONENT,
                    paramId = "",
                    paramIndex = index.toShort()
                )

                connection.sendUnsignedV2(
                    systemId = GCS_SYSTEM,
                    componentId = GCS_COMPONENT,
                    payload = request
                )

                delay(50) // Small delay between requests
            }
        }
    }

    /**
     * SAVE COMMAND: Set parameter value to flight controller
     * This sends PARAM_SET MAVLink message
     */
    suspend fun setParameter(
        paramName: String,
        paramValue: Float,
        paramType: MavEnumValue<MavParamType>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💾 Setting parameter: $paramName = $paramValue")

            inFlightWrites.add(paramName)

            // COMMAND: Send PARAM_SET
            val paramSet = ParamSet(
                targetSystem = TARGET_SYSTEM,
                targetComponent = TARGET_COMPONENT,
                paramId = paramName,
                paramValue = paramValue,
                paramType = paramType
            )

            connection.sendUnsignedV2(
                systemId = GCS_SYSTEM,
                componentId = GCS_COMPONENT,
                payload = paramSet
            )

            // Wait for confirmation (PARAM_VALUE response)
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
     * Request specific parameter by name
     */
    suspend fun requestParameter(paramName: String): Result<Parameter> = withContext(Dispatchers.IO) {
        try {
            val request = ParamRequestRead(
                targetSystem = TARGET_SYSTEM,
                targetComponent = TARGET_COMPONENT,
                paramId = paramName,
                paramIndex = -1
            )

            connection.sendUnsignedV2(
                systemId = GCS_SYSTEM,
                componentId = GCS_COMPONENT,
                payload = request
            )

            // Wait for response
            val paramValue = withTimeout(3.seconds) {
                connection.mavFrame
                    .map { it.message }
                    .filterIsInstance<ParamValue>()
                    .first { it.paramId.trimEnd('\u0000') == paramName }
            }

            val parameter = Parameter(
                name = paramName,
                value = paramValue.paramValue,
                type = paramValue.paramType,
                index = paramValue.paramIndex
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
     * This can be enhanced to detect from MAVLink AUTOPILOT_VERSION message
     */
    private fun detectVehicleType(): String? {
        // For now, default to copter
        // TODO: Detect from MAVLink messages in future
        return "copter"
    }
}
