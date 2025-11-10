package com.example.pavamanconfiguratorgcs.data.repository

import android.util.Log
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.definitions.common.MavParamType
import com.example.pavamanconfiguratorgcs.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Repository for managing serial port configurations via MAVLink parameters
 */
class SerialPortRepository(
    private val parameterRepository: ParameterRepository
) {
    companion object {
        private const val TAG = "SerialPortRepository"
    }

    // Discovered serial ports
    private val _serialPorts = MutableStateFlow<List<SerialPortConfig>>(emptyList())
    val serialPorts: StateFlow<List<SerialPortConfig>> = _serialPorts.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Discover all serial port parameters from the loaded parameter list
     */
    suspend fun discoverSerialPorts(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            _error.value = null

            Log.d(TAG, "🔍 Discovering serial port parameters...")

            // Get all parameters from the parameter repository
            val allParams = parameterRepository.parameters.value

            if (allParams.isEmpty()) {
                Log.w(TAG, "⚠️ No parameters loaded. Request parameters first.")
                _error.value = "No parameters loaded. Please fetch parameters first."
                return@withContext Result.failure(Exception("Parameters not loaded"))
            }

            // Filter serial-related parameters
            val serialParams = allParams.filter { (name, _) ->
                name.startsWith("SERIAL")
            }

            Log.d(TAG, "📋 Found ${serialParams.size} SERIAL* parameters")

            // Group parameters by port number
            val portMap = mutableMapOf<Int, MutableMap<String, Parameter>>()

            serialParams.forEach { (name, param) ->
                // Extract port number from parameter name (e.g., SERIAL1_BAUD -> 1)
                val portNumberMatch = Regex("SERIAL(\\d+)_").find(name)
                if (portNumberMatch != null) {
                    val portNumber = portNumberMatch.groupValues[1].toInt()
                    portMap.getOrPut(portNumber) { mutableMapOf() }[name] = param
                }
            }

            // Build SerialPortConfig objects
            val ports = portMap.keys.sorted().mapNotNull { portNumber ->
                buildSerialPortConfig(portNumber, portMap[portNumber] ?: emptyMap())
            }

            _serialPorts.value = ports
            Log.d(TAG, "✅ Discovered ${ports.size} serial ports")

            ports.forEach { port ->
                Log.d(TAG, "  Port ${port.portNumber}: Baud=${port.currentBaud.toInt()}, Protocol=${port.currentProtocol.toInt()}")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error discovering serial ports", e)
            _error.value = e.message
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Build a SerialPortConfig from parameters for a specific port
     */
    private fun buildSerialPortConfig(
        portNumber: Int,
        params: Map<String, Parameter>
    ): SerialPortConfig? {
        val baudParamName = "SERIAL${portNumber}_BAUD"
        val protocolParamName = "SERIAL${portNumber}_PROTOCOL"
        val optionsParamName = "SERIAL${portNumber}_OPTIONS"

        val baudParam = params[baudParamName]
        val protocolParam = params[protocolParamName]
        val optionsParam = params[optionsParamName]

        // At minimum, we need baud and protocol params
        if (baudParam == null || protocolParam == null) {
            Log.w(TAG, "⚠️ Port $portNumber missing required parameters")
            return null
        }

        return SerialPortConfig(
            portNumber = portNumber,
            baudParamName = baudParamName,
            protocolParamName = protocolParamName,
            optionsParamName = if (optionsParam != null) optionsParamName else null,
            currentBaud = baudParam.value,
            currentProtocol = protocolParam.value,
            currentOptions = optionsParam?.value,
            baudMetadata = null,  // Can be enhanced with metadata
            protocolMetadata = null,
            optionsMetadata = null,
            txPin = getTxPinDescription(portNumber),
            rxPin = getRxPinDescription(portNumber)
        )
    }

    /**
     * Get TX pin description for a port (platform-specific)
     */
    private fun getTxPinDescription(portNumber: Int): String {
        // This is hardware-specific - can be customized based on board type
        return when (portNumber) {
            0 -> "USB"
            1 -> "TELEM1"
            2 -> "TELEM2"
            3 -> "GPS"
            4 -> "GPS2"
            5 -> "USER"
            else -> ""
        }
    }

    /**
     * Get RX pin description for a port (platform-specific)
     */
    private fun getRxPinDescription(portNumber: Int): String {
        return getTxPinDescription(portNumber)  // Usually same label
    }

    /**
     * Change baud rate for a serial port
     */
    suspend fun changeBaudRate(
        portNumber: Int,
        newBaud: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 Setting SERIAL${portNumber}_BAUD = $newBaud")

            val paramName = "SERIAL${portNumber}_BAUD"
            val param = parameterRepository.parameters.value[paramName]
            val paramType = param?.type ?: MavParamType.REAL32.wrap()

            val result = parameterRepository.setParameter(paramName, newBaud.toFloat(), paramType)

            if (result.isSuccess) {
                // Update local state
                refreshPort()
                Log.i(TAG, "✅ Baud rate updated successfully")
            } else {
                Log.e(TAG, "❌ Failed to update baud rate")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error changing baud rate", e)
            Result.failure(e)
        }
    }

    /**
     * Change protocol for a serial port
     */
    suspend fun changeProtocol(
        portNumber: Int,
        newProtocol: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 Setting SERIAL${portNumber}_PROTOCOL = $newProtocol")

            val paramName = "SERIAL${portNumber}_PROTOCOL"
            val param = parameterRepository.parameters.value[paramName]
            val paramType = param?.type ?: MavParamType.REAL32.wrap()

            val result = parameterRepository.setParameter(paramName, newProtocol.toFloat(), paramType)

            if (result.isSuccess) {
                // Update local state
                refreshPort()
                Log.i(TAG, "✅ Protocol updated successfully")
            } else {
                Log.e(TAG, "❌ Failed to update protocol")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error changing protocol", e)
            Result.failure(e)
        }
    }

    /**
     * Change options bitmask for a serial port
     */
    suspend fun changeOptions(
        portNumber: Int,
        newOptions: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 Setting SERIAL${portNumber}_OPTIONS = $newOptions")

            val paramName = "SERIAL${portNumber}_OPTIONS"
            val param = parameterRepository.parameters.value[paramName]
            val paramType = param?.type ?: MavParamType.REAL32.wrap()

            val result = parameterRepository.setParameter(paramName, newOptions.toFloat(), paramType)

            if (result.isSuccess) {
                // Update local state
                refreshPort()
                Log.i(TAG, "✅ Options updated successfully")
            } else {
                Log.e(TAG, "❌ Failed to update options")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error changing options", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh port data from the parameter repository
     */
    private suspend fun refreshPort() {
        // Re-discover all ports to get updated values
        discoverSerialPorts()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }
}
