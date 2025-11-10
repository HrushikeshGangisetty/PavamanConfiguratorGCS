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
        // JSON format has DisplayName, Description, and Units (but no defaults/ranges)
        private const val COPTER_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json"
        private const val PLANE_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/ArduPlane/apm.pdef.json"
        private const val ROVER_PARAMS_URL = "https://autotest.ardupilot.org/Parameters/Rover/apm.pdef.json"

        // Cache duration - 7 days
        private const val CACHE_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    }

    private val metadataCache = mutableMapOf<String, ParamMetadata>()
    private var isLoaded = false
    private var lastLoadTime = 0L

    data class ParamMetadata(
        val displayName: String = "",
        val description: String = "",
        val units: String = "",
        val minValue: Float? = null,
        val maxValue: Float? = null,
        val increment: Float? = null,
        val defaultValue: Float? = null,
        val rebootRequired: Boolean = false
    )

    /**
     * Load parameter metadata from ArduPilot definition files
     * @param vehicleType: "copter", "plane", or "rover"
     */
    suspend fun loadMetadata(vehicleType: String = "copter"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()

            // Check if cache is still valid
            if (isLoaded && metadataCache.isNotEmpty() && (currentTime - lastLoadTime) < CACHE_DURATION_MS) {
                Log.d(TAG, "Metadata already loaded (${metadataCache.size} parameters), using cached version")
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

            // Parse JSON format
            parseJsonMetadata(jsonString)

            isLoaded = true
            lastLoadTime = currentTime
            Log.i(TAG, "✅ Successfully loaded metadata for ${metadataCache.size} parameters")

            // Additional validation - check if we actually got data
            if (metadataCache.isNotEmpty()) {
                val withDefaults = metadataCache.values.count { it.defaultValue != null }
                val withRanges = metadataCache.values.count { it.minValue != null || it.maxValue != null }
                Log.i(TAG, "📊 Metadata quality:")
                Log.i(TAG, "   - ${metadataCache.size} total parameters")
                Log.i(TAG, "   - $withDefaults have default values")
                Log.i(TAG, "   - $withRanges have range constraints")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load parameter metadata: ${e.message}", e)
            // Don't fail - just continue without metadata
            Result.success(Unit)
        }
    }

    /**
     * Parse JSON metadata format
     */
    private fun parseJsonMetadata(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)

            // The JSON structure is: { "GROUP_": { "PARAM_NAME": { "DisplayName": "...", ... } } }
            val groupKeys = jsonObject.keys()
            var count = 0

            while (groupKeys.hasNext()) {
                val groupKey = groupKeys.next()
                val groupObj = jsonObject.optJSONObject(groupKey)

                if (groupObj == null) {
                    // Skip non-object entries
                    continue
                }

                // Iterate through parameters within this group
                val paramKeys = groupObj.keys()
                while (paramKeys.hasNext()) {
                    val paramName = paramKeys.next()
                    val paramObj = groupObj.optJSONObject(paramName)

                    if (paramObj == null) {
                        // Skip non-object entries
                        continue
                    }

                    // Extract metadata fields
                    val displayName = paramObj.optString("DisplayName", "")
                    val description = paramObj.optString("Description", "")
                    val units = paramObj.optString("Units", "")

                    // Use simple heuristic for rebootRequired (based on name patterns)
                    val rebootRequired = paramName.contains("Reboot", ignoreCase = true)

                    val metadata = ParamMetadata(
                        displayName = displayName,
                        description = description,
                        units = units,
                        rebootRequired = rebootRequired
                    )

                    metadataCache[paramName] = metadata
                    count++

                    // Log first few for verification
                    if (count <= 5) {
                        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.i(TAG, "Sample JSON param #$count: $paramName")
                        Log.i(TAG, "  Group: $groupKey")
                        Log.i(TAG, "  DisplayName: '$displayName'")
                        Log.i(TAG, "  Description: '${description.take(minOf(50, description.length))}'")
                        Log.i(TAG, "  Units: '$units'")
                        Log.i(TAG, "  Reboot Required: '${metadata.rebootRequired}'")
                        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    }
                }
            }

            Log.i(TAG, "Parsed $count parameters from JSON")

            // If JSON parsing failed, log warning
            if (count == 0) {
                Log.w(TAG, "⚠️ JSON parsing found 0 parameters. JSON format may have changed.")
                Log.d(TAG, "JSON preview (first 500 chars): ${jsonString.take(500)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON metadata: ${e.message}", e)
            Log.d(TAG, "JSON preview (first 500 chars): ${jsonString.take(500)}")
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

    /**
     * Extract metadata from parameter JSON object
     * (Kept for backward compatibility, but JSON format is incomplete)
     */
    private fun extractMetadata(paramObj: JSONObject, paramName: String): ParamMetadata? {
        // Check if there's a nested "fields" object (some ArduPilot versions)
        val fieldsObj = paramObj.optJSONObject("fields") ?: paramObj

        val displayName = fieldsObj.optString("DisplayName", "")
        val description = fieldsObj.optString("Description", "")
        val cleanDescription = description.trim()

        val units = fieldsObj.optString("Units", "")
        val range = fieldsObj.optString("Range", "")
        val increment = fieldsObj.optString("Increment", "")
        val defaultValue = fieldsObj.optString("Default", "")
        val rebootRequired = fieldsObj.optString("RebootRequired", "").equals("True", ignoreCase = true)

        // Also try alternate field names
        val altDisplayName = fieldsObj.optString("humanName", "")
        val altDescription = fieldsObj.optString("documentation", "")
        val altUnits = fieldsObj.optString("units", "")

        // Use whichever is non-empty
        val finalDisplayName = displayName.ifEmpty { altDisplayName }
        val finalDescription = cleanDescription.ifEmpty { altDescription }
        val finalUnits = units.ifEmpty { altUnits }

        // Parse min/max from range string (e.g., "0 100")
        var minValue: Float? = null
        var maxValue: Float? = null
        if (range.isNotEmpty()) {
            val rangeParts = range.trim().split(Regex("\\s+"))
            if (rangeParts.size >= 2) {
                minValue = rangeParts[0].toFloatOrNull()
                maxValue = rangeParts[1].toFloatOrNull()
            }
        }

        val incrementValue = increment.toFloatOrNull()
        val defaultValueFloat = defaultValue.toFloatOrNull()

        return ParamMetadata(
            displayName = finalDisplayName,
            description = finalDescription,
            units = finalUnits,
            minValue = minValue,
            maxValue = maxValue,
            increment = incrementValue,
            defaultValue = defaultValueFloat,
            rebootRequired = rebootRequired
        )
    }

    /**
     * Log sample parameter metadata for debugging
     */
    private fun logSampleParameter(count: Int, paramName: String, groupKey: String, paramObj: JSONObject, metadata: ParamMetadata) {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "Sample param #$count: $paramName")
        Log.i(TAG, "  Group: $groupKey")

        // Log ALL fields with their actual values
        val allFields = paramObj.keys()
        Log.i(TAG, "  All fields in this parameter:")
        while (allFields.hasNext()) {
            val fieldKey = allFields.next()
            val fieldValue = paramObj.opt(fieldKey)
            val valuePreview = when {
                fieldValue == null -> "null"
                fieldValue is String && fieldValue.length > 50 -> fieldValue.take(50) + "..."
                else -> fieldValue.toString()
            }
            Log.i(TAG, "    $fieldKey = '$valuePreview'")
        }

        Log.i(TAG, "  Extracted values:")
        Log.i(TAG, "    DisplayName: '${metadata.displayName}'")
        Log.i(TAG, "    Description: '${metadata.description.take(50)}'")
        Log.i(TAG, "    Units: '${metadata.units}'")
        Log.i(TAG, "    Default: '${metadata.defaultValue}'")
        Log.i(TAG, "    Range: '${metadata.minValue} ${metadata.maxValue}'")
        Log.i(TAG, "    Final DisplayName: '${metadata.displayName}'")
        Log.i(TAG, "    Final Description: '${metadata.description.take(50)}'")
        Log.i(TAG, "    Final Units: '${metadata.units}'")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}
