package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.models.Parameter
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Detailed device information model for HWID screen
 */
data class DeviceInfoDetailed(
    val paramName: String,
    val deviceId: UInt,
    val busType: String,
    val busAddress: String
)

class HWIDViewModel(
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HWIDViewModel"
    }

    private val _devices = MutableStateFlow<List<DeviceInfoDetailed>>(emptyList())
    val devices: StateFlow<List<DeviceInfoDetailed>> = _devices.asStateFlow()

    init {
        // Observe parameter map and update devices when parameters change
        viewModelScope.launch {
            parameterRepository.parameters.collect { params ->
                try {
                    val processed = processParameters(params)
                    _devices.value = processed
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing parameters for HWID", e)
                }
            }
        }

        // Trigger an initial parameter fetch (non-blocking)
        refreshParameters()
    }

    /** Public: request fresh parameters from vehicle */
    fun refreshParameters() {
        viewModelScope.launch {
            try {
                parameterRepository.requestAllParameters()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request parameters", e)
            }
        }
    }

    /** Convert raw Parameter map into sorted DeviceInfoDetailed list */
    private fun processParameters(params: Map<String, Parameter>): List<DeviceInfoDetailed> {
        // Filtering rules
        val filtered = params.values
            .filter { p ->
                val name = p.name
                val hasId = name.contains("_ID") || name.contains("_DEVID")
                val excluded = name.contains("_IDX") || name.contains("FRSKY")
                hasId && !excluded
            }

        val mapped = filtered.mapNotNull { p ->
            try {
                val rawUInt = p.value.toInt().toUInt()

                val busType = decodeBusType(rawUInt, p.name)
                val busAddress = decodeBusAddress(rawUInt, p.name)

                DeviceInfoDetailed(
                    paramName = p.name,
                    deviceId = rawUInt,
                    busType = busType,
                    busAddress = busAddress
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping param ${p.name} due to parsing error: ${e.message}")
                null
            }
        }

        return mapped.sortedBy { it.paramName }
    }

    // Decode bus type using common ArduPilot-style encoding (bits 16-23)
    private fun decodeBusType(rawId: UInt, paramName: String): String {
        val busId = (rawId shr 16) and 0xFFu
        val mapped = when (busId) {
            0u -> "None"
            1u -> "I2C"
            2u -> "SPI"
            3u -> "CAN"
            4u -> "UART"
            else -> "Unknown ($busId)"
        }

        // Heuristic fallback: sometimes bus type isn't encoded, infer from parameter name
        if (mapped.startsWith("Unknown")) {
            val nameUpper = paramName.uppercase()
            return when {
                "I2C" in nameUpper -> "I2C"
                "SPI" in nameUpper -> "SPI"
                "CAN" in nameUpper -> "CAN"
                "UART" in nameUpper || "SERIAL" in nameUpper -> "UART"
                else -> mapped
            }
        }

        return mapped
    }

    // Decode bus address using common encoding (bits 0-7)
    private fun decodeBusAddress(rawId: UInt, paramName: String): String {
        val address = rawId and 0xFFu
        return if (address > 0u) {
            // Return decimal representation (base 10) instead of hex
            address.toString()
        } else {
            // Heuristic: some params encode address in upper bytes or in the parameter name
            val nameUpper = paramName.uppercase()
            val addrFromName = "".let {
                // try to extract trailing digits
                val digits = Regex("(\\d+)").find(nameUpper)?.value
                digits
            }

            if (!addrFromName.isNullOrEmpty()) {
                try {
                    val v = addrFromName.toInt()
                    // return decimal digits
                    v.toString()
                } catch (_: Exception) {
                    "N/A"
                }
            } else {
                "N/A"
            }
        }
    }
}
