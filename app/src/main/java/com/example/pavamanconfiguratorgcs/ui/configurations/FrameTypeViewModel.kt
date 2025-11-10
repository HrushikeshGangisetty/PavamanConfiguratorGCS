package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import com.divpundir.mavlink.definitions.common.MavParamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for selecting frame type (Quad/Hexa/Octa) and applying parameters to firmware.
 */
class FrameTypeViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FrameTypeViewModel"
    }

    enum class FrameKind(val displayName: String) {
        QUAD("Quad"),
        HEXA("Hexa"),
        OCTA("Octa")
    }

    data class UiState(
        val selectedFrame: FrameKind? = null,
        val selectedSubtype: String = "",
        val isApplying: Boolean = false,
        val appliedCount: Int = 0,
        val isSuccess: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Return only the three required parameters for a frame change: FRAME, FRAME_CLASS, FRAME_TYPE.
     *
     * IMPORTANT: The numeric values provided below are placeholders. Verify the correct
     * numeric IDs for your firmware/version before using in production. These values
     * MUST match what your autopilot expects (integer codes typically represented as floats
     * in MAVLink parameters).
     */
    private fun frameParamsForKind(frame: FrameKind): Map<String, Float> {
        return when (frame) {
            FrameKind.QUAD -> mapOf(
                // Replace the example values (e.g., 10f) with the correct FRAME/CLASS/TYPE IDs
                "FRAME" to 10f,
                "FRAME_CLASS" to 1f,
                "FRAME_TYPE" to 1f
            )
            FrameKind.HEXA -> mapOf(
                "FRAME" to 20f,
                "FRAME_CLASS" to 1f,
                "FRAME_TYPE" to 2f
            )
            FrameKind.OCTA -> mapOf(
                "FRAME" to 30f,
                "FRAME_CLASS" to 1f,
                "FRAME_TYPE" to 3f
            )
        }
    }

    fun selectFrame(frame: FrameKind) {
        // Enforce X subtype
        val subtype = "X"
        _uiState.value = _uiState.value.copy(
            selectedFrame = frame,
            selectedSubtype = subtype,
            isApplying = true,
            appliedCount = 0,
            isSuccess = false,
            errorMessage = null
        )

        // Build a map that contains only the three allowed parameters
        val paramsToApply = frameParamsForKind(frame)

        // If no params defined, short-circuit with message
        if (paramsToApply.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isApplying = false,
                isSuccess = false,
                errorMessage = "No parameter mapping defined for ${frame.displayName}. Edit FrameTypeViewModel.frameParamsForKind to add mappings."
            )
            Log.w(TAG, "No parameter mapping defined for ${frame.displayName}")
            return
        }

        // Apply parameters asynchronously on IO to avoid blocking the main thread
        viewModelScope.launch(Dispatchers.IO) {
            // IMPORTANT: Do NOT request all parameters. We only request the specific parameters
            // we need (one-by-one) to avoid loading unrelated parameters from the FCU.

            var successCount = 0
            var failed = false
            val failedNames = mutableListOf<String>()

            for ((name, value) in paramsToApply) {
                try {
                    Log.d(TAG, "Preparing to set $name = $value for frame ${frame.name}")

                    // Normalize expected key (16-char truncate + uppercasing) to match repository cache
                    val expectedId = name.take(16).replace("\u0000", "").trim().uppercase()

                    // Request the specific parameter from the FCU (do not request the full list).
                    // This will prompt the FCU to send a PARAM_VALUE echo for this param only.
                    // Ask the FCU for this parameter and only proceed if it exists
                    val requestResult = try {
                        parameterRepository.requestParameter(name)
                    } catch (e: Exception) {
                        Log.d(TAG, "requestParameter call threw for $name: ${e.message}")
                        null
                    }

                    // Variables we'll use for the set call. If we successfully got a PARAM_VALUE
                    // we prefer the FCU-provided name and type - otherwise we fall back to the
                    // normalized name and a safe default type so we still attempt the set.
                    val paramNameToUseFromFcu: String
                    val paramTypeToUseFromFcu: com.divpundir.mavlink.definitions.common.MavParamType

                    if (requestResult is ParameterRepository.ParameterResult.Success) {
                        val fcuParam = requestResult.parameter
                        paramNameToUseFromFcu = fcuParam.name
                        paramTypeToUseFromFcu = fcuParam.type
                    } else {
                        // FCU did not reply with a PARAM_VALUE for this param. Instead of skipping
                        // the set, attempt to set it directly using the normalized ID and a safe
                        // default type. FRAME/FRAME_CLASS/FRAME_TYPE are integer-like params in
                        // many firmwares, so use INT8 as a safer default rather than REAL32.
                        Log.w(TAG, "Parameter $name not present on FCU or request timed out; will attempt set anyway using fallback type INT8")
                        paramNameToUseFromFcu = expectedId
                        paramTypeToUseFromFcu = MavParamType.INT8
                    }

                    // small delay to ensure cache updated if we received parameter info
                    delay(20)

                    // Try setting the parameter; allow one retry with force=true
                    var attempt = 0
                    var paramSuccess = false
                    var lastResult: ParameterRepository.ParameterResult? = null

                    while (attempt < 2 && !paramSuccess) {
                        attempt++
                        Log.d(TAG, "Setting attempt $attempt for $name (using id=${paramNameToUseFromFcu} type=${paramTypeToUseFromFcu})")
                        // Use the FCU-provided name and type (or our fallback)
                        val result = parameterRepository.setParameter(paramNameToUseFromFcu, value, paramType = paramTypeToUseFromFcu, force = attempt > 1)
                        lastResult = result
                        when (result) {
                            is ParameterRepository.ParameterResult.Success -> {
                                paramSuccess = true
                                successCount++
                            }
                            is ParameterRepository.ParameterResult.Error -> {
                                Log.e(TAG, "Error setting $name: ${result.message} (attempt $attempt)")
                                // if first attempt failed, try requesting parameter and retry once
                                if (attempt == 1) {
                                    try {
                                        parameterRepository.requestParameter(paramNameToUseFromFcu)
                                        delay(50)
                                    } catch (e: Exception) {
                                        Log.d(TAG, "requestParameter retry failed for $name: ${e.message}")
                                    }
                                }
                            }
                            is ParameterRepository.ParameterResult.Timeout -> {
                                Log.e(TAG, "Timeout setting $name (attempt $attempt)")
                                if (attempt == 1) {
                                    // wait a bit and retry
                                    delay(100)
                                }
                            }
                        }
                    }

                    if (!paramSuccess) {
                        // If this was FRAME and we failed with the default fallback type, try a few
                        // additional integer/float types. Some firmwares expect a different param
                        // encoding (e.g., INT32 or REAL32) for the FRAME parameter.
                        if (expectedId == "FRAME") {
                            val altTypes = listOf(
                                com.divpundir.mavlink.definitions.common.MavParamType.INT16,
                                com.divpundir.mavlink.definitions.common.MavParamType.INT32,
                                com.divpundir.mavlink.definitions.common.MavParamType.REAL32
                            )
                            for (altType in altTypes) {
                                try {
                                    Log.d(TAG, "FRAME fallback attempt with type=$altType")
                                    val fallbackResult = parameterRepository.setParameter(paramNameToUseFromFcu, value, paramType = altType, force = true)
                                    if (fallbackResult is com.example.pavamanconfiguratorgcs.data.ParameterRepository.ParameterResult.Success) {
                                        paramSuccess = true
                                        successCount++
                                        Log.d(TAG, "FRAME applied successfully with fallback type=$altType")
                                        break
                                    } else {
                                        Log.w(TAG, "FRAME fallback type $altType failed: $fallbackResult")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Exception during FRAME fallback $altType", e)
                                }
                            }

                            // If still not successful, try alternative parameter names that some firmwares use
                            if (!paramSuccess) {
                                val altNames = listOf("FRAME_ID", "FRAME_IDX", "FRAME_CONFIG", "FRAME_PARAM", "FRAMEID")
                                for (altName in altNames) {
                                    try {
                                        val altId = altName.take(16).uppercase()
                                        Log.d(TAG, "Attempting FRAME as alternative param name: $altId (type INT8)")
                                        val altRes = parameterRepository.setParameter(altId, value, paramType = com.divpundir.mavlink.definitions.common.MavParamType.INT8, force = true)
                                        if (altRes is com.example.pavamanconfiguratorgcs.data.ParameterRepository.ParameterResult.Success) {
                                            paramSuccess = true
                                            successCount++
                                            Log.d(TAG, "FRAME applied successfully using alt name $altId (INT8)")
                                            break
                                        }

                                        // try other types for this alt name
                                        for (altType in altTypes) {
                                            val altRes2 = parameterRepository.setParameter(altId, value, paramType = altType, force = true)
                                            if (altRes2 is com.example.pavamanconfiguratorgcs.data.ParameterRepository.ParameterResult.Success) {
                                                paramSuccess = true
                                                successCount++
                                                Log.d(TAG, "FRAME applied successfully using alt name $altId with type=$altType")
                                                break
                                            }
                                        }

                                        if (paramSuccess) break
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Exception while trying alternative FRAME name $altName", e)
                                    }
                                }

                                // If still not successful, try a bounded diagnostic request to find real parameter names
                                if (!paramSuccess) {
                                    try {
                                        Log.d(TAG, "FRAME: running bounded diagnostic to discover parameter names containing 'FRAME'")
                                        val discovered = parameterRepository.findParametersByPrefix("FRAME", timeoutMs = 1500L)
                                        if (discovered.isNotEmpty()) {
                                            Log.d(TAG, "FRAME: discovered ${discovered.size} candidate param names: ${discovered.keys}")
                                            for ((candName, candParam) in discovered) {
                                                try {
                                                    Log.d(TAG, "Attempting FRAME using discovered param $candName with detected type=${candParam.type}")
                                                    val resCand = parameterRepository.setParameter(candName, value, paramType = candParam.type, force = true)
                                                    if (resCand is com.example.pavamanconfiguratorgcs.data.ParameterRepository.ParameterResult.Success) {
                                                        paramSuccess = true
                                                        successCount++
                                                        Log.d(TAG, "FRAME applied successfully using discovered param $candName")
                                                        break
                                                    }
                                                    // try other types if initial type didn't work
                                                    for (altType in altTypes) {
                                                        val resCand2 = parameterRepository.setParameter(candName, value, paramType = altType, force = true)
                                                        if (resCand2 is com.example.pavamanconfiguratorgcs.data.ParameterRepository.ParameterResult.Success) {
                                                            paramSuccess = true
                                                            successCount++
                                                            Log.d(TAG, "FRAME applied successfully using discovered param $candName with type=$altType")
                                                            break
                                                        }
                                                    }
                                                    if (paramSuccess) break
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Exception while setting discovered param $candName", e)
                                                }
                                            }
                                        } else {
                                            Log.w(TAG, "FRAME: diagnostic found no matching parameters on FCU")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "FRAME: diagnostic search failed", e)
                                    }
                                }
                            }
                        }
                    }

                    if (!paramSuccess) {
                        failed = true
                        failedNames.add(name)
                        Log.e(TAG, "Failed to set parameter $name after retries. Last result: $lastResult")
                    }
                } catch (e: Exception) {
                    failed = true
                    failedNames.add(name)
                    Log.e(TAG, "Exception while setting $name", e)
                }

                // update applied count in UI state after each attempt (switch to Main)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(appliedCount = successCount)
                }
            }

            // Finalize UI state
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    isSuccess = !failed && successCount == paramsToApply.size,
                    errorMessage = if (failed) "Some parameters failed to apply for: ${failedNames.joinToString(", ")}. See logs." else null,
                    appliedCount = successCount
                )
            }

            Log.d(TAG, "Finished applying parameters for ${frame.name}. Successes: $successCount, Failed: $failed")
         }
     }

    /**
     * Reload/clear the UI state (optional helper)
     */
    fun clearSelection() {
        _uiState.value = UiState()
    }
}
