package com.example.pavamanconfiguratorgcs.data.network

import android.content.Context
import android.util.Log
import com.example.pavamanconfiguratorgcs.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.simpleframework.xml.core.Persister
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service to fetch parameter metadata from ArduPilot GitHub repository
 * Supports both JSON and XML formats with caching
 */
class ParameterMetadataService(private val context: Context) {

    companion object {
        private const val TAG = "ParamMetadataService"

        // Mission Planner's consolidated XML (easier to parse)
        private const val MISSION_PLANNER_PARAMS_URL =
            "https://autotest.ardupilot.org/Parameters"

        // Cache settings
        private const val CACHE_DIR = "param_metadata"
        private const val CACHE_EXPIRY_DAYS = 7L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val serializer = Persister()

    // In-memory cache
    private val metadataCache = mutableMapOf<String, ParameterMetadata>()

    /**
     * Fetch parameter metadata for specific vehicle type
     */
    suspend fun fetchMetadata(vehicleType: String): Result<Map<String, ParameterMetadata>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching metadata for $vehicleType...")

                // Check cache first
                val cached = loadFromCache(vehicleType)
                if (cached != null && !isCacheExpired(vehicleType)) {
                    Log.d(TAG, "Using cached metadata (${cached.size} params)")
                    metadataCache.putAll(cached)
                    return@withContext Result.success(cached)
                }

                // Fetch from network
                val metadata = fetchVehicleMetadata(vehicleType)

                // Save to cache
                saveToCache(vehicleType, metadata)
                metadataCache.putAll(metadata)

                Log.i(TAG, "Fetched ${metadata.size} parameter definitions")
                Result.success(metadata)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch metadata", e)

                // Try to use cached data even if expired
                val cached = loadFromCache(vehicleType)
                if (cached != null) {
                    Log.w(TAG, "Using expired cache due to network error")
                    metadataCache.putAll(cached)
                    return@withContext Result.success(cached)
                }

                Result.failure(e)
            }
        }

    /**
     * Fetch metadata for specific vehicle from Mission Planner XML
     */
    private suspend fun fetchVehicleMetadata(vehicle: String): Map<String, ParameterMetadata> {
        val vehicleName = when (vehicle.lowercase()) {
            "copter", "arducopter" -> "Copter"
            "plane", "arduplane" -> "Plane"
            "rover", "ardurover" -> "Rover"
            "sub", "ardusub" -> "Sub"
            else -> "Copter"
        }

        val url = "$MISSION_PLANNER_PARAMS_URL/$vehicleName/apm.pdef.xml"
        val xml = downloadFile(url)
        return parseParameterXml(xml)
    }

    /**
     * Download file from URL
     */
    private suspend fun downloadFile(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw Exception("Empty response")
        }
    }

    /**
     * Parse ArduPilot parameter XML format
     */
    private fun parseParameterXml(xml: String): Map<String, ParameterMetadata> {
        val metadata = mutableMapOf<String, ParameterMetadata>()

        try {
            val params = serializer.read(ParametersXml::class.java, xml)

            params.params?.forEach { param ->
                val name = param.name ?: return@forEach

                // Parse values/bitmask
                val values = mutableMapOf<String, String>()
                val bitmask = mutableMapOf<Int, String>()

                param.valuesList?.firstOrNull()?.values?.forEach { value ->
                    val code = value.code ?: ""
                    val text = value.text ?: ""

                    // Try to parse as number for bitmask
                    code.toIntOrNull()?.let { intCode ->
                        bitmask[intCode] = text
                    }
                    values[code] = text
                }

                // Parse range (format: "min max")
                val rangeParts = (param.field?.value ?: "").split(" ")
                val minValue = rangeParts.getOrNull(0)?.toFloatOrNull()
                val maxValue = rangeParts.getOrNull(1)?.toFloatOrNull()

                metadata[name] = ParameterMetadata(
                    name = name,
                    displayName = param.humanName ?: name,
                    description = param.documentation ?: "",
                    units = extractUnits(param.documentation ?: ""),
                    range = param.field?.value ?: "",
                    values = values,
                    bitmask = bitmask,
                    minValue = minValue,
                    maxValue = maxValue
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XML", e)
        }

        return metadata
    }

    /**
     * Extract units from documentation text (often in parentheses)
     */
    private fun extractUnits(documentation: String): String {
        val unitsRegex = """\((m|cm|mm|deg|rad|s|ms|Hz|%|A|V|W|mAh)\)""".toRegex()
        return unitsRegex.find(documentation)?.groupValues?.get(1) ?: ""
    }

    /**
     * Get metadata for specific parameter
     */
    fun getMetadata(paramName: String): ParameterMetadata {
        return metadataCache[paramName] ?: ParameterMetadata.empty(paramName)
    }

    /**
     * Check if parameter has metadata
     */
    fun hasMetadata(paramName: String): Boolean {
        return metadataCache.containsKey(paramName)
    }

    // ============ CACHE MANAGEMENT ============

    private fun getCacheDir(): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(vehicleType: String): File {
        return File(getCacheDir(), "$vehicleType.cache")
    }

    private fun isCacheExpired(vehicleType: String): Boolean {
        val cacheFile = getCacheFile(vehicleType)
        if (!cacheFile.exists()) return true

        val age = System.currentTimeMillis() - cacheFile.lastModified()
        val expiry = TimeUnit.DAYS.toMillis(CACHE_EXPIRY_DAYS)

        return age > expiry
    }

    private fun saveToCache(vehicleType: String, metadata: Map<String, ParameterMetadata>) {
        try {
            val cacheFile = getCacheFile(vehicleType)
            val json = metadata.entries.joinToString("\n") { (name, meta) ->
                "$name|${meta.displayName}|${meta.description}|${meta.units}|${meta.range}|${meta.minValue}|${meta.maxValue}|${meta.defaultValue}"
            }
            cacheFile.writeText(json)
            Log.d(TAG, "Saved ${metadata.size} params to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private fun loadFromCache(vehicleType: String): Map<String, ParameterMetadata>? {
        try {
            val cacheFile = getCacheFile(vehicleType)
            if (!cacheFile.exists()) return null

            val metadata = mutableMapOf<String, ParameterMetadata>()
            cacheFile.readLines().forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    metadata[parts[0]] = ParameterMetadata(
                        name = parts[0],
                        displayName = parts.getOrNull(1) ?: "",
                        description = parts.getOrNull(2) ?: "",
                        units = parts.getOrNull(3) ?: "",
                        range = parts.getOrNull(4) ?: "",
                        minValue = parts.getOrNull(5)?.toFloatOrNull(),
                        maxValue = parts.getOrNull(6)?.toFloatOrNull(),
                        defaultValue = parts.getOrNull(7)?.toFloatOrNull()
                    )
                }
            }

            return metadata.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
            return null
        }
    }

    /**
     * Clear cache
     */
    fun clearCache() {
        getCacheDir().listFiles()?.forEach { it.delete() }
        metadataCache.clear()
    }
}

