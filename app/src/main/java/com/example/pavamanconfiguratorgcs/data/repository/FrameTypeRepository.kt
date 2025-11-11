package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.definitions.common.MavParamType
import com.example.pavamanconfiguratorgcs.data.ParameterRepository
import com.example.pavamanconfiguratorgcs.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

            // If cache is empty, attempt to request parameters from the FCU
            if (allParams.isEmpty()) {
                Log.w(TAG, "⚠️ No parameters loaded. Attempting to request parameters from vehicle...")

                // Try requesting all params (one or two attempts)
                try {
                    val fetched = parameterRepository.requestAllParameters()
                    if (fetched.isNotEmpty()) {
                        allParams = fetched
                        Log.d(TAG, "✅ Fetched ${fetched.size} parameters via requestAllParameters()")
                    } else {
                        // As a last resort try prefix-based discovery for FRAME-related params
                        Log.w(TAG, "No parameters returned from requestAllParameters(); attempting prefix search for 'FRAME'")
                        val found = parameterRepository.findParametersByPrefix("FRAME")
                        if (found.isNotEmpty()) {
                            allParams = found
                            Log.d(TAG, "✅ Found ${found.size} FRAME-related parameters via findParametersByPrefix")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error while requesting parameters: ${e.message}")
                }
            }

            if (allParams.isEmpty()) {
                Log.w(TAG, "⚠️ No parameters loaded. Request parameters first.")
                _error.value = "No parameters loaded. Please fetch parameters first."
                return@withContext Result.failure(Exception("Parameters not loaded"))
            }

            // Check which scheme is available
            val hasFrame = allParams.containsKey(PARAM_FRAME)
            val hasFrameClass = allParams.containsKey(PARAM_FRAME_CLASS)
            val hasFrameType = allParams.containsKey(PARAM_FRAME_TYPE)

            Log.d(TAG, "Parameter detection: FRAME=$hasFrame, FRAME_CLASS=$hasFrameClass, FRAME_TYPE=$hasFrameType")

            val config = when {
                // Prefer CLASS_TYPE scheme if both params present
                hasFrameClass && hasFrameType -> {
                    val frameClass = allParams[PARAM_FRAME_CLASS]!!.value
                    val frameType = allParams[PARAM_FRAME_TYPE]!!.value
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
                    val frameValue = allParams[PARAM_FRAME]!!.value
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
                    Log.w(TAG, "⚠️ No frame parameters found")
                    _error.value = "Frame parameters not found on this vehicle"
                    FrameConfig(
                        currentFrameType = null,
                        paramScheme = FrameParamScheme.UNKNOWN,
                        isDetected = false
                    )
                }
            }

            _frameConfig.value = config

            if (config.isValid) {
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
                        paramType = MavParamType.UINT8
                    )

                    when (result) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.i(TAG, "✅ FRAME parameter set to $frameValue")
                            updateFrameConfig(newFrameType, rebootRequired = true)
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
                        paramType = MavParamType.UINT8
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
                    delay(200)

                    // Set FRAME_TYPE
                    val typeResult = parameterRepository.setParameter(
                        paramName = PARAM_FRAME_TYPE,
                        value = values.frameType.toFloat(),
                        paramType = MavParamType.UINT8
                    )

                    when (typeResult) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.i(TAG, "✅ FRAME_TYPE set to ${values.frameType}")
                            updateFrameConfig(newFrameType, rebootRequired = true)
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
    private fun updateFrameConfig(newFrameType: FrameType, rebootRequired: Boolean) {
        _frameConfig.update { current ->
            current.copy(
                currentFrameType = newFrameType,
                rebootRequired = rebootRequired
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
