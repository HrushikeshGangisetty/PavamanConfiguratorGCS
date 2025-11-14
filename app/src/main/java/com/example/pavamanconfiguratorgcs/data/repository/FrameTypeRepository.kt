package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.definitions.common.MavParamType
import com.example.pavamanconfiguratorgcs.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import java.util.Locale

/**
 * Repository for managing frame type configuration via MAVLink parameters
 * Supports both legacy FRAME and newer FRAME_CLASS+FRAME_TYPE parameter schemes
 */
class FrameTypeRepository(
    private val parameterRepository: ParameterRepository
) {
    companion object {
        private const val TAG = "FrameTypeRepository"

        // Parameter names
        private const val PARAM_FRAME = "FRAME"
        private const val PARAM_FRAME_CLASS = "FRAME_CLASS"
        private const val PARAM_FRAME_TYPE = "FRAME_TYPE"
    }

    // Current frame configuration
    private val _frameConfig = MutableStateFlow<FrameConfig>(
        FrameConfig(
            currentFrameType = null,
            paramScheme = FrameParamScheme.UNKNOWN,
            isDetected = false
        )
    )
    val frameConfig: StateFlow<FrameConfig> = _frameConfig.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Detect which frame parameter scheme the autopilot uses and read current values
     */
    suspend fun detectFrameParameters(): Result<FrameConfig> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "🔍 Detecting frame parameters...")

            // Get all parameters from cache
            var allParams = parameterRepository.getCachedParametersSnapshot()

            Log.d(TAG, "📊 Total parameters in cache: ${allParams.size}")

            // Log first few parameter names to debug
            if (allParams.isNotEmpty()) {
                val sampleParams = allParams.keys.take(10).joinToString(", ")
                Log.d(TAG, "Sample parameter names: $sampleParams")
            }

            // If cache is empty, attempt to request parameters from the FCU with retries
            if (allParams.isEmpty()) {
                Log.w(TAG, "⚠️ No parameters loaded. Attempting to request parameters from vehicle...")

                val maxAttempts = 5
                var attempt = 0
                var fetched: Map<String, ParameterRepository.ParameterValue> = emptyMap()

                while (attempt < maxAttempts && fetched.isEmpty()) {
                    attempt++
                    try {
                        Log.d(TAG, "Attempt $attempt/$maxAttempts - requesting all parameters from FCU (timeout 8000ms)")
                        fetched = parameterRepository.requestAllParameters(8000L)
                        if (fetched.isNotEmpty()) {
                            allParams = fetched
                            Log.d(TAG, "✅ Fetched ${fetched.size} parameters on attempt $attempt")
                            break
                        } else {
                            Log.w(TAG, "No parameters returned on attempt $attempt")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting parameters on attempt $attempt: ${e.message}")
                    }

                    // Wait a bit before retrying to allow the connection/FCU to respond
                    delay(1000L * attempt)
                }

                if (allParams.isEmpty()) {
                    Log.w(TAG, "No parameters returned from requestAllParameters after $maxAttempts attempts; attempting prefix search for 'FRAME'")
                    try {
                        val found = parameterRepository.findParametersByPrefix("FRAME")
                        if (found.isNotEmpty()) {
                            allParams = found
                            Log.d(TAG, "✅ Found ${found.size} FRAME-related parameters via prefix search")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during prefix search for parameters: ${e.message}")
                    }
                }
            }

            if (allParams.isEmpty()) {
                Log.w(TAG, "⚠️ No parameters loaded. Request parameters first.")
                _error.value = "No parameters loaded. Please fetch parameters first."
                return@withContext Result.failure(Exception("Parameters not loaded"))
            }

            // Create a case-insensitive lookup map
            val paramLookup = allParams.mapKeys { it.key.uppercase(Locale.ROOT) }

            // Helper function to find parameter with case-insensitive lookup
            fun findParam(name: String): ParameterRepository.ParameterValue? {
                val upperName = name.uppercase(Locale.ROOT)
                return paramLookup[upperName]
            }

            // Look for FRAME-related parameters in the cache
            val frameRelatedParams = paramLookup.filterKeys { key ->
                key.contains("FRAME")
            }

            Log.d(TAG, "🔍 Found ${frameRelatedParams.size} FRAME-related parameters:")
            frameRelatedParams.keys.forEach { key ->
                Log.d(TAG, "  - $key = ${frameRelatedParams[key]?.value}")
            }

            // Check which scheme is available using case-insensitive lookup
            val frameParam = findParam(PARAM_FRAME)
            val frameClassParam = findParam(PARAM_FRAME_CLASS)
            val frameTypeParam = findParam(PARAM_FRAME_TYPE)

            val hasFrame = frameParam != null
            val hasFrameClass = frameClassParam != null
            val hasFrameType = frameTypeParam != null

            Log.d(TAG, "Parameter detection: FRAME=$hasFrame, FRAME_CLASS=$hasFrameClass, FRAME_TYPE=$hasFrameType")

            val config = when {
                // Prefer CLASS_TYPE scheme if both params present
                hasFrameClass && hasFrameType -> {
                    val frameClass = frameClassParam.value
                    val frameType = frameTypeParam.value
                    val detectedFrame = ClassTypeFrameMapping.valuesToFrameType(frameClass, frameType)

                    Log.d(TAG, "✅ Detected CLASS_TYPE scheme: FRAME_CLASS=$frameClass, FRAME_TYPE=$frameType -> $detectedFrame")

                    FrameConfig(
                        currentFrameType = detectedFrame,
                        paramScheme = FrameParamScheme.CLASS_TYPE,
                        frameClassValue = frameClass,
                        frameTypeValue = frameType,
                        isDetected = true,
                        rebootRequired = false
                    )
                }
                // Use legacy FRAME parameter
                hasFrame -> {
                    val frameValue = frameParam.value
                    val detectedFrame = LegacyFrameMapping.valueToFrameType(frameValue)

                    Log.d(TAG, "✅ Detected LEGACY_FRAME scheme: FRAME=$frameValue -> $detectedFrame")

                    FrameConfig(
                        currentFrameType = detectedFrame,
                        paramScheme = FrameParamScheme.LEGACY_FRAME,
                        frameParamValue = frameValue,
                        isDetected = true,
                        rebootRequired = false
                    )
                }
                else -> {
                    // Try to find any parameter with "FRAME" in the name as a last resort
                    if (frameRelatedParams.isNotEmpty()) {
                        Log.w(TAG, "⚠️ Found FRAME-related parameters but couldn't map to known scheme")
                        _error.value = "Frame parameters found but format not recognized. Found: ${frameRelatedParams.keys.joinToString(", ")}"
                    } else {
                        Log.w(TAG, "⚠️ No frame parameters found in ${allParams.size} loaded parameters")
                        _error.value = "Frame parameters not found on this vehicle"
                    }

                    FrameConfig(
                        currentFrameType = null,
                        paramScheme = FrameParamScheme.UNKNOWN,
                        isDetected = false
                    )
                }
            }

            _frameConfig.value = config

            // Only clear error if we successfully detected parameters
            if (config.isDetected) {
                _error.value = null
                Log.i(TAG, "✅ Frame detection complete: ${config.currentFrameType?.displayName}")
            }

            Result.success(config)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detecting frame parameters", e)
            _error.value = e.message
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Change the frame type to a new configuration
     * Returns Result indicating success/failure
     */
    suspend fun changeFrameType(newFrameType: FrameType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            val currentConfig = _frameConfig.value

            if (!currentConfig.isDetected) {
                val error = "Frame parameters not detected. Please detect parameters first."
                Log.e(TAG, "❌ $error")
                _error.value = error
                return@withContext Result.failure(Exception(error))
            }

            Log.d(TAG, "📝 Changing frame type to ${newFrameType.displayName} using ${currentConfig.paramScheme} scheme")

            when (currentConfig.paramScheme) {
                FrameParamScheme.LEGACY_FRAME -> {
                    // Set single FRAME parameter
                    val frameValue = LegacyFrameMapping.frameTypeToValue(newFrameType)
                    val result = parameterRepository.setParameter(
                        paramName = PARAM_FRAME,
                        value = frameValue.toFloat(),
                        paramType = MavParamType.UINT8,
                        force = true // Force the write even if cached value matches
                    )

                    when (result) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.i(TAG, "✅ FRAME parameter set to $frameValue")
                            // Clear any previous error state
                            _error.value = null

                            // Update frame config with new values
                            _frameConfig.update { current ->
                                current.copy(
                                    currentFrameType = newFrameType,
                                    frameParamValue = frameValue.toFloat(),
                                    rebootRequired = true,
                                    isDetected = true
                                )
                            }

                            // Small delay to allow parameter to propagate
                            delay(300)

                            // Re-detect to confirm the change
                            detectFrameParameters()

                            Result.success(Unit)
                        }
                        is ParameterRepository.ParameterResult.Error -> {
                            Log.e(TAG, "❌ Failed to set FRAME: ${result.message}")
                            _error.value = "Failed to set frame: ${result.message}"
                            Result.failure(Exception(result.message))
                        }
                        is ParameterRepository.ParameterResult.Timeout -> {
                            Log.e(TAG, "❌ Timeout setting FRAME parameter")
                            _error.value = "Timeout setting frame parameter"
                            Result.failure(Exception("Timeout"))
                        }
                    }
                }

                FrameParamScheme.CLASS_TYPE -> {
                    // Set FRAME_CLASS and FRAME_TYPE parameters
                    val values = ClassTypeFrameMapping.frameTypeToValues(newFrameType)

                    // Set FRAME_CLASS first
                    val classResult = parameterRepository.setParameter(
                        paramName = PARAM_FRAME_CLASS,
                        value = values.frameClass.toFloat(),
                        paramType = MavParamType.UINT8,
                        force = true // Force the write
                    )

                    if (classResult !is ParameterRepository.ParameterResult.Success) {
                        val errorMsg = when (classResult) {
                            is ParameterRepository.ParameterResult.Error -> classResult.message
                            else -> "Timeout or failure"
                        }
                        Log.e(TAG, "❌ Failed to set FRAME_CLASS: $errorMsg")
                        _error.value = "Failed to set frame class: $errorMsg"
                        return@withContext Result.failure(Exception(errorMsg))
                    }

                    Log.d(TAG, "✅ FRAME_CLASS set to ${values.frameClass}")

                    // Small delay between parameter writes
                    delay(300)

                    // Set FRAME_TYPE
                    val typeResult = parameterRepository.setParameter(
                        paramName = PARAM_FRAME_TYPE,
                        value = values.frameType.toFloat(),
                        paramType = MavParamType.UINT8,
                        force = true // Force the write
                    )

                    when (typeResult) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.i(TAG, "✅ FRAME_TYPE set to ${values.frameType}")
                            // Clear any previous error state
                            _error.value = null

                            // Update frame config with new values
                            _frameConfig.update { current ->
                                current.copy(
                                    currentFrameType = newFrameType,
                                    frameClassValue = values.frameClass.toFloat(),
                                    frameTypeValue = values.frameType.toFloat(),
                                    rebootRequired = true,
                                    isDetected = true
                                )
                            }

                            // Small delay to allow parameters to propagate
                            delay(300)

                            // Re-detect to confirm the changes
                            detectFrameParameters()

                            Result.success(Unit)
                        }
                        is ParameterRepository.ParameterResult.Error -> {
                            Log.e(TAG, "❌ Failed to set FRAME_TYPE: ${typeResult.message}")
                            _error.value = "Failed to set frame type: ${typeResult.message}"
                            Result.failure(Exception(typeResult.message))
                        }
                        is ParameterRepository.ParameterResult.Timeout -> {
                            Log.e(TAG, "❌ Timeout setting FRAME_TYPE parameter")
                            _error.value = "Timeout setting frame type parameter"
                            Result.failure(Exception("Timeout"))
                        }
                    }
                }

                FrameParamScheme.UNKNOWN -> {
                    val error = "Unknown parameter scheme - cannot change frame type"
                    Log.e(TAG, "❌ $error")
                    _error.value = error
                    Result.failure(Exception(error))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error changing frame type", e)
            _error.value = e.message
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Get motor layout for current frame type
     */
    fun getCurrentMotorLayout(): MotorLayout? {
        val currentFrame = _frameConfig.value.currentFrameType ?: return null
        return MotorLayouts.getLayoutForFrame(currentFrame)
    }

    /**
     * Update internal frame config state
     */
    private fun updateFrameConfig(newFrameType: FrameType) {
        _frameConfig.update { current ->
            current.copy(
                currentFrameType = newFrameType,
                rebootRequired = true
            )
        }
    }

    /**
     * Clear reboot required flag (call after user reboots the vehicle)
     */
    fun clearRebootRequired() {
        _frameConfig.update { it.copy(rebootRequired = false) }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Get available frame types
     */
    fun getAvailableFrameTypes(): List<FrameType> {
        return FrameType.getAllFrameTypes()
    }
}
