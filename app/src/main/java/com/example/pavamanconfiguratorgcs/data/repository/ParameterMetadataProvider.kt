package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Provides parameter metadata (description, units, min/max) from ArduPilot parameter definitions
 */
class ParameterMetadataProvider {

    companion object {
        private const val TAG = "ParamMetadata"

        // ArduPilot parameter metadata URLs for different vehicle types
        private const val COPTER_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json"
        private const val PLANE_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/ArduPlane/apm.pdef.json"
        private const val ROVER_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/Rover/apm.pdef.json"
    }

    private val metadataCache = mutableMapOf<String, ParamMetadata>()
    private var isLoaded = false

    data class ParamMetadata(
        val description: String = "",
        val units: String = "",
        val minValue: Float? = null,
        val maxValue: Float? = null,
        val increment: Float? = null,
        val defaultValue: Float? = null // Add default value from ArduPilot
    )

    /**
     * Load parameter metadata from ArduPilot definition files
     * @param vehicleType: "copter", "plane", or "rover"
     */
    suspend fun loadMetadata(vehicleType: String = "copter"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isLoaded && metadataCache.isNotEmpty()) {
                Log.d(TAG, "Metadata already loaded (${metadataCache.size} parameters)")
                return@withContext Result.success(Unit)
            }

            val url = when (vehicleType.lowercase()) {
                "plane" -> PLANE_PARAMS_URL
                "rover" -> ROVER_PARAMS_URL
                else -> COPTER_PARAMS_URL
            }

            Log.i(TAG, "📥 Loading parameter metadata from: $url")

            val jsonString = URL(url).readText()
            Log.d(TAG, "Downloaded ${jsonString.length} bytes of metadata")

            val jsonObject = JSONObject(jsonString)

            // Parse parameter definitions
            val keys = jsonObject.keys()
            var count = 0

            while (keys.hasNext()) {
                val paramName = keys.next()
                val paramObj = jsonObject.optJSONObject(paramName) ?: continue

                val displayName = paramObj.optString("DisplayName", "")
                val description = paramObj.optString("Description", "")
                val fullDescription = if (displayName.isNotEmpty() && description.isNotEmpty()) {
                    "$displayName - $description"
                } else if (displayName.isNotEmpty()) {
                    displayName
                } else if (description.isNotEmpty()) {
                    description
                } else {
                    ""
                }

                val units = paramObj.optString("Units", "")
                val range = paramObj.optString("Range", "")
                val increment = paramObj.optString("Increment", "")
                val defaultValue = paramObj.optString("Default", "")

                // Parse min/max from range string (e.g., "0 100")
                var minValue: Float? = null
                var maxValue: Float? = null
                if (range.isNotEmpty()) {
                    val rangeParts = range.trim().split(" ")
                    if (rangeParts.size >= 2) {
                        minValue = rangeParts[0].toFloatOrNull()
                        maxValue = rangeParts[1].toFloatOrNull()
                    }
                }

                val incrementValue = increment.toFloatOrNull()
                val defaultValueFloat = defaultValue.toFloatOrNull()

                metadataCache[paramName] = ParamMetadata(
                    description = fullDescription.trim(),
                    units = units,
                    minValue = minValue,
                    maxValue = maxValue,
                    increment = incrementValue,
                    defaultValue = defaultValueFloat
                )
                count++

                // Log first few parameters as examples
                if (count <= 3) {
                    Log.d(TAG, "Sample param: $paramName = units:'$units', desc:'${fullDescription.take(50)}'")
                }
            }

            isLoaded = true
            Log.i(TAG, "✅ Successfully loaded metadata for $count parameters")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load parameter metadata: ${e.message}", e)
            // Don't fail - just continue without metadata
            Result.success(Unit)
        }
    }

    /**
     * Get metadata for a specific parameter
     */
    fun getMetadata(paramName: String): ParamMetadata {
        return metadataCache[paramName] ?: ParamMetadata()
    }

    /**
     * Check if metadata is loaded
     */
    fun isMetadataLoaded(): Boolean = isLoaded && metadataCache.isNotEmpty()

    /**
     * Clear cached metadata
     */
    fun clearCache() {
        metadataCache.clear()
        isLoaded = false
    }
}
