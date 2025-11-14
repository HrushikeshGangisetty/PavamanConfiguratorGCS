package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavParamType
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Battery Monitor Setup Configuration.
 * Manages local caching of battery parameters and handles writing to the Flight Controller.
 */
class BatteryMonitorViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BatteryMonitorVM"
        private const val BATT_MONITOR = "BATT_MONITOR"
        private const val BATT_CAPACITY = "BATT_CAPACITY"
        private const val BATT_VOLT_PIN = "BATT_VOLT_PIN"
        private const val BATT_CURR_PIN = "BATT_CURR_PIN"
    }

    data class BatteryConfigState(
        val battMonitor: Int = 0,
        val sensorSelection: String = "APM2.5",
        val hwVersion: Int = 0,
        val battCapacity: Float = 0f,
        val voltPin: Int = -1,
        val currPin: Int = -1,
        val isLoading: Boolean = false
    )

    private val _configState = MutableStateFlow(BatteryConfigState())
    val configState: StateFlow<BatteryConfigState> = _configState.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            telemetryRepository.connectionState.collect { state ->
                val connected = when (state) {
                    is TelemetryRepository.ConnectionState.Connected,
                    is TelemetryRepository.ConnectionState.HeartbeatVerified -> true
                    else -> false
                }
                _isConnected.value = connected
                if (connected) {
                    loadBatteryParameters()
                }
            }
        }
    }

    fun loadBatteryParameters() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _configState.value = _configState.value.copy(isLoading = true)
                _errorMessage.value = null

                var battMonitor = 0
                var battCapacity = 0f
                var voltPin = -1
                var currPin = -1

                when (val result = parameterRepository.requestParameter(BATT_MONITOR)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        battMonitor = result.parameter.value.toInt()
                        Log.d(TAG, "Loaded $BATT_MONITOR = $battMonitor")
                    }
                    else -> Log.w(TAG, "Failed to load $BATT_MONITOR: $result")
                }

                when (val result = parameterRepository.requestParameter(BATT_CAPACITY)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        battCapacity = result.parameter.value
                        Log.d(TAG, "Loaded $BATT_CAPACITY = $battCapacity")
                    }
                    else -> Log.w(TAG, "Failed to load $BATT_CAPACITY: $result")
                }

                when (val result = parameterRepository.requestParameter(BATT_VOLT_PIN)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        voltPin = result.parameter.value.toInt()
                        Log.d(TAG, "Loaded $BATT_VOLT_PIN = $voltPin")
                    }
                    else -> Log.w(TAG, "Failed to load $BATT_VOLT_PIN: $result")
                }

                when (val result = parameterRepository.requestParameter(BATT_CURR_PIN)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        currPin = result.parameter.value.toInt()
                        Log.d(TAG, "Loaded $BATT_CURR_PIN = $currPin")
                    }
                    else -> Log.w(TAG, "Failed to load $BATT_CURR_PIN: $result")
                }

                val hwVersion = determineHwVersionFromPins(voltPin, currPin)

                _configState.value = _configState.value.copy(
                    battMonitor = battMonitor,
                    battCapacity = battCapacity,
                    voltPin = voltPin,
                    currPin = currPin,
                    hwVersion = hwVersion,
                    isLoading = false
                )

                _successMessage.value = "Parameters loaded successfully"
                Log.i(TAG, "Battery parameters loaded successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading battery parameters", e)
                _errorMessage.value = "Failed to load parameters: ${e.message}"
                _configState.value = _configState.value.copy(isLoading = false)
            }
        }
    }

    fun updateBattMonitor(value: Int) {
        _configState.value = _configState.value.copy(battMonitor = value)
        Log.d(TAG, "Cache updated: BATT_MONITOR = $value")
    }

    fun updateSensorSelection(selection: String) {
        _configState.value = _configState.value.copy(sensorSelection = selection)
        Log.d(TAG, "Cache updated: Sensor Selection = $selection")
    }

    fun updateHwVersion(hwVersion: Int) {
        val (voltPin, currPin) = calculatePinsForHwVersion(hwVersion)
        _configState.value = _configState.value.copy(
            hwVersion = hwVersion,
            voltPin = voltPin,
            currPin = currPin
        )
        Log.d(TAG, "Cache updated: HW Version = $hwVersion, VOLT_PIN = $voltPin, CURR_PIN = $currPin")
    }

    fun updateBattCapacity(capacity: Float) {
        _configState.value = _configState.value.copy(battCapacity = capacity)
        Log.d(TAG, "Cache updated: BATT_CAPACITY = $capacity")
    }

    fun uploadParameters() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _configState.value = _configState.value.copy(isLoading = true)
                _errorMessage.value = null
                _successMessage.value = null

                val state = _configState.value
                val startTime = System.currentTimeMillis()

                Log.i(TAG, "Starting FAST parallel parameter upload...")

                // Upload all parameters in parallel using async for maximum speed
                val results = listOf(
                    async {
                        Triple(
                            BATT_MONITOR,
                            parameterRepository.setParameter(BATT_MONITOR, state.battMonitor.toFloat(), MavParamType.INT32),
                            state.battMonitor
                        )
                    },
                    async {
                        Triple(
                            BATT_CAPACITY,
                            parameterRepository.setParameter(BATT_CAPACITY, state.battCapacity, MavParamType.REAL32),
                            state.battCapacity
                        )
                    },
                    async {
                        Triple(
                            BATT_VOLT_PIN,
                            parameterRepository.setParameter(BATT_VOLT_PIN, state.voltPin.toFloat(), MavParamType.INT8),
                            state.voltPin
                        )
                    },
                    async {
                        Triple(
                            BATT_CURR_PIN,
                            parameterRepository.setParameter(BATT_CURR_PIN, state.currPin.toFloat(), MavParamType.INT8),
                            state.currPin
                        )
                    }
                ).awaitAll()

                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "⚡ All parameters sent in ${elapsed}ms")

                // Process results
                var allSuccess = true
                val errors = mutableListOf<String>()

                results.forEach { (paramName, result, value) ->
                    when (result) {
                        is ParameterRepository.ParameterResult.Success -> {
                            Log.i(TAG, "✅ $paramName = $value written successfully")
                        }
                        else -> {
                            allSuccess = false
                            errors.add("$paramName write failed")
                            Log.e(TAG, "❌ Failed to write $paramName: $result")
                        }
                    }
                }

                _configState.value = _configState.value.copy(isLoading = false)

                if (allSuccess) {
                    _successMessage.value = "All parameters uploaded in ${elapsed}ms! ⚡"
                    Log.i(TAG, "✅ Parameter upload completed successfully in ${elapsed}ms")
                } else {
                    _errorMessage.value = "Some parameters failed: ${errors.joinToString(", ")}"
                    Log.e(TAG, "❌ Parameter upload completed with errors: ${errors.joinToString(", ")}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading parameters", e)
                _errorMessage.value = "Upload failed: ${e.message}"
                _configState.value = _configState.value.copy(isLoading = false)
            }
        }
    }

    private fun calculatePinsForHwVersion(hwVersion: Int): Pair<Int, Int> {
        return when (hwVersion) {
            0 -> Pair(0, 1)
            1 -> Pair(1, 2)
            2 -> Pair(13, 12)
            3 -> Pair(2, 3)
            4 -> Pair(5, 6)
            5 -> Pair(14, 15)
            6 -> Pair(10, 11)
            7 -> Pair(100, 101)
            else -> Pair(-1, -1)
        }
    }

    private fun determineHwVersionFromPins(voltPin: Int, currPin: Int): Int {
        return when {
            voltPin == 0 && currPin == 1 -> 0
            voltPin == 1 && currPin == 2 -> 1
            voltPin == 13 && currPin == 12 -> 2
            voltPin == 2 && currPin == 3 -> 3
            voltPin == 5 && currPin == 6 -> 4
            voltPin == 14 && currPin == 15 -> 5
            voltPin == 10 && currPin == 11 -> 6
            else -> 7
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "BatteryMonitorViewModel cleared")
    }
}
