package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.GpsRawInt
import com.divpundir.mavlink.definitions.common.MavParamType
import com.divpundir.mavlink.definitions.common.RcChannels
import com.divpundir.mavlink.definitions.common.ServoOutputRaw
import com.divpundir.mavlink.definitions.minimal.Heartbeat
import com.divpundir.mavlink.definitions.minimal.MavModeFlag
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for Failsafe configuration and live status.
 */
class FailsafeViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val parameterRepository: ParameterRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FailsafeVM"

        // Parameter names
        private const val FS_THR_ENABLE = "FS_THR_ENABLE"
        private const val FS_THR_VALUE = "FS_THR_VALUE"

        private const val BATT_FS_LOW_ACT = "BATT_FS_LOW_ACT"
        private const val LOW_VOLT = "LOW_VOLT"
        private const val FS_BATT_MAH = "FS_BATT_MAH"
        private const val BATT_LOW_TIMER = "BATT_LOW_TIMER"

        private const val FS_GCS_ENABLE = "FS_GCS_ENABLE"
    }

    // User-editable config parameters (loaded from FC and written back)
    data class FailsafeConfigState(
        val fsThrEnable: Int? = null,
        val fsThrValue: Int? = null,

        val battFsLowAct: Int? = null,
        val lowVolt: Float? = null,
        val fsBattMah: Int? = null,
        val battLowTimer: Int? = null,

        val fsGcsEnable: Int? = null
    )

    // Live vehicle status (updated from telemetry)
    data class VehicleStatusState(
        val isArmed: Boolean = false,
        val gpsFixType: Int = 0,
        val currentFlightMode: String = "",
        val ch3in: Int = 1500,
        val rcInputs: Map<Int, Int> = emptyMap(),
        val servoOutputs: Map<Int, Int> = emptyMap()
    )

    private val _config = MutableStateFlow(FailsafeConfigState())
    val config: StateFlow<FailsafeConfigState> = _config.asStateFlow()

    private val _status = MutableStateFlow(VehicleStatusState())
    val status: StateFlow<VehicleStatusState> = _status.asStateFlow()

    // User messages
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Connection indicator observed from TelemetryRepository
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Diagnostic: found parameters by prefix
    private val _foundParams = MutableStateFlow<Map<String, Float>>(emptyMap())
    val foundParams: StateFlow<Map<String, Float>> = _foundParams.asStateFlow()

    /**
     * Diagnostic function: find parameters on the FCU whose name matches the given prefix.
     * Populates [foundParams] with name -> value map for quick inspection in the UI.
     */
    fun findParametersByPrefix(prefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (telemetryRepository.connection == null) {
                    _errorMessage.value = "No connection - cannot search parameters"
                    return@launch
                }
                val map = parameterRepository.findParametersByPrefix(prefix)
                if (map.isEmpty()) {
                    _foundParams.value = emptyMap()
                    _errorMessage.value = "No parameters found for prefix '$prefix'"
                } else {
                    _foundParams.value = map.mapValues { it.value.value }
                    _successMessage.value = "Found ${map.size} parameters"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Parameter search failed: ${e.message}"
            }
        }
    }

    // Derived imminent throttle failsafe flag
    val isThrottleFailsafeImminent: StateFlow<Boolean> = combine(status, config) { s, c ->
        val thr = c.fsThrValue
        thr != null && s.ch3in < thr
    }.let { flow ->
        // state in scope
        val state = MutableStateFlow(false)
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    private var telemetryJob: Job? = null

    // Track whether params are loaded once to avoid duplicate requests
    private var paramsLoaded = false

    init {
        // Monitor connection state to enable/disable UI actions and trigger parameter load
        viewModelScope.launch(Dispatchers.IO) {
            telemetryRepository.connectionState.collect { st ->
                val connected = when (st) {
                    is TelemetryRepository.ConnectionState.Connected,
                    is TelemetryRepository.ConnectionState.HeartbeatVerified -> true
                    else -> false
                }
                _isConnected.value = connected

                if (connected && !paramsLoaded) {
                    paramsLoaded = true
                    // Load parameters once after connection established
                    try {
                        // Populate the repository cache first to ensure parameter types are known
                        try {
                            parameterRepository.requestAllParameters()
                            Log.d(TAG, "Parameter cache populated after connect")
                        } catch (e: Exception) {
                            Log.w(TAG, "requestAllParameters failed: ${e.message}")
                        }
                        loadFailsafeParameters()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load failsafe params after connect: ${e.message}")
                    }
                }
            }
        }

        startTelemetryCollectors()
        // Do not call loadFailsafeParameters() immediately here; wait for connection
    }

    /**
     * Subscribe to MAVLink frames and update live status.
     */
    private fun startTelemetryCollectors() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch(Dispatchers.IO) {
            telemetryRepository.mavFrame.collect { frame ->
                when (val msg = frame.message) {
                    is Heartbeat -> {
                        // Convert baseMode.value to Int via its string representation and check SAFETY_ARMED bit.
                        val armed = try {
                            val baseInt = msg.baseMode.value.toString().toIntOrNull() ?: 0
                            (baseInt and MavModeFlag.SAFETY_ARMED.value.toInt()) != 0
                        } catch (_: Exception) { false }

                        val mode = try { msg.customMode.toInt() } catch (_: Exception) { -1 }
                        val modeText = if (mode >= 0) "Mode $mode" else _status.value.currentFlightMode
                        _status.update { it.copy(isArmed = armed, currentFlightMode = modeText) }
                    }
                    is GpsRawInt -> {
                        val fix = msg.fixType.value.toInt()
                        _status.update { it.copy(gpsFixType = fix) }
                    }
                    is RcChannels -> {
                        val rc = mapOf(
                            1 to msg.chan1Raw.toInt(),
                            2 to msg.chan2Raw.toInt(),
                            3 to msg.chan3Raw.toInt(),
                            4 to msg.chan4Raw.toInt(),
                            5 to msg.chan5Raw.toInt(),
                            6 to msg.chan6Raw.toInt(),
                            7 to msg.chan7Raw.toInt(),
                            8 to msg.chan8Raw.toInt()
                        )
                        _status.update { it.copy(rcInputs = rc, ch3in = msg.chan3Raw.toInt()) }
                    }
                    is ServoOutputRaw -> {
                        val out = mapOf(
                            1 to msg.servo1Raw.toInt(),
                            2 to msg.servo2Raw.toInt(),
                            3 to msg.servo3Raw.toInt(),
                            4 to msg.servo4Raw.toInt(),
                            5 to msg.servo5Raw.toInt(),
                            6 to msg.servo6Raw.toInt(),
                            7 to msg.servo7Raw.toInt(),
                            8 to msg.servo8Raw.toInt()
                        )
                        _status.update { it.copy(servoOutputs = out) }
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * Load failsafe-related parameters from the FCU.
     */
    fun loadFailsafeParameters() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                suspend fun updateInt(name: String, setter: (Int) -> Unit) {
                    when (val r = parameterRepository.requestParameter(name)) {
                        is ParameterRepository.ParameterResult.Success -> setter(r.parameter.value.toInt())
                        else -> Log.w(TAG, "Param $name not loaded: $r")
                    }
                }
                suspend fun updateFloat(name: String, setter: (Float) -> Unit) {
                    when (val r = parameterRepository.requestParameter(name)) {
                        is ParameterRepository.ParameterResult.Success -> setter(r.parameter.value)
                        else -> Log.w(TAG, "Param $name not loaded: $r")
                    }
                }

                var fsThrEnable: Int? = null
                var fsThrValue: Int? = null
                var battAct: Int? = null
                var lowVolt: Float? = null
                var fsMah: Int? = null
                var lowTimer: Int? = null
                var gcsEnable: Int? = null

                updateInt(FS_THR_ENABLE) { fsThrEnable = it }
                updateInt(FS_THR_VALUE) { fsThrValue = it }

                updateInt(BATT_FS_LOW_ACT) { battAct = it }
                updateFloat(LOW_VOLT) { lowVolt = it }
                updateInt(FS_BATT_MAH) { fsMah = it }
                updateInt(BATT_LOW_TIMER) { lowTimer = it }

                updateInt(FS_GCS_ENABLE) { gcsEnable = it }

                _config.update {
                    it.copy(
                        fsThrEnable = fsThrEnable,
                        fsThrValue = fsThrValue,
                        battFsLowAct = battAct,
                        lowVolt = lowVolt,
                        fsBattMah = fsMah,
                        battLowTimer = lowTimer,
                        fsGcsEnable = gcsEnable
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading failsafe params", e)
                _errorMessage.value = "Failed to load parameters: ${e.message}"
            }
        }
    }

    /**
     * Generic helper: set a MAVLink parameter and then request it back to confirm the FCU accepted it.
     * This increases the chance Mission Planner (or any other client) will see the same value.
     */
    private suspend fun setMavlinkParam(name: String, value: Float, paramType: MavParamType): ParameterRepository.ParameterResult {
        val key = name.trim().uppercase()
        Log.d(TAG, "Setting param $key -> $value (type=$paramType)")

        // Quick pre-check: ensure we have an active connection and FCU detected
        val conn = telemetryRepository.connection
        if (conn == null || telemetryRepository.fcuSystemId == 0.toUByte()) {
            Log.w(TAG, "No active connection or FCU not detected - cannot set $key")
            return ParameterRepository.ParameterResult.Error("No active connection or FCU not detected")
        }

        // First attempt: normal set
        val setRes = parameterRepository.setParameter(key, value, paramType)
        if (setRes is ParameterRepository.ParameterResult.Success) {
            // request param back to confirm
            when (val r = parameterRepository.requestParameter(key)) {
                is ParameterRepository.ParameterResult.Success -> {
                    applyLocalConfigUpdate(key, r.parameter.value)
                    Log.d(TAG, "Confirmed $key = ${r.parameter.value}")
                    return r
                }
                else -> {
                    Log.w(TAG, "Parameter $key set but requestParameter confirmation failed: $r")
                    return r
                }
            }
        }

        // If first set failed or timed out, attempt a second write with force=true (some firmwares require exact typed writes)
        Log.w(TAG, "Initial set failed for $key: $setRes - retrying with force")
        try {
            // The underlying repository's setParameter supports a 'force' flag; try that path.
            val forced = parameterRepository.setParameter(key, value, paramType, force = true)
            if (forced is ParameterRepository.ParameterResult.Success) {
                when (val r2 = parameterRepository.requestParameter(key)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        applyLocalConfigUpdate(key, r2.parameter.value)
                        Log.d(TAG, "Forced write confirmed $key = ${r2.parameter.value}")
                        return r2
                    }
                    else -> {
                        Log.w(TAG, "Forced write succeeded but requestParameter failed for $key: $r2")
                        return r2
                    }
                }
            } else {
                Log.e(TAG, "Forced write failed for $key: $forced")
                return forced
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during forced write for $key", e)
            return ParameterRepository.ParameterResult.Error(e.message ?: "Forced write exception")
        }
    }

    /**
     * Write a single parameter change to the FCU immediately.
     * Uses setMavlinkParam to ensure confirmation.
     */
    fun writeParameterChange(paramName: String, newValue: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Quick connection check to fail fast and surface a helpful message
                if (telemetryRepository.connection == null || telemetryRepository.fcuSystemId == 0.toUByte()) {
                    _errorMessage.value = "No active connection or FCU not detected. Connect to vehicle first."
                    return@launch
                }

                val key = paramName.trim().uppercase()
                // Param-specific type overrides sourced from typical ArduPilot parameter metadata
                val overrideType: MavParamType? = when (key) {
                    FS_THR_ENABLE -> MavParamType.INT8
                    FS_THR_VALUE -> MavParamType.INT16
                    BATT_FS_LOW_ACT -> MavParamType.INT8
                    LOW_VOLT -> MavParamType.REAL32
                    FS_BATT_MAH -> MavParamType.INT32
                    BATT_LOW_TIMER -> MavParamType.INT16
                    FS_GCS_ENABLE -> MavParamType.INT8
                    else -> null
                }

                val (valueFloat, type) = when (newValue) {
                    is Int -> newValue.toFloat() to (overrideType ?: MavParamType.INT32)
                    is Float -> newValue to (overrideType ?: MavParamType.REAL32)
                    is Double -> newValue.toFloat() to (overrideType ?: MavParamType.REAL32)
                    is String -> {
                        // Try parse as Int first, then Float
                        newValue.toIntOrNull()?.let { it.toFloat() to (overrideType ?: MavParamType.INT32) }
                            ?: (newValue.toFloatOrNull()?.let { it to (overrideType ?: MavParamType.REAL32) }
                            ?: throw IllegalArgumentException("Invalid numeric value"))
                    }
                    else -> throw IllegalArgumentException("Unsupported value type")
                }

                // Perform the write+confirm flow
                val res = setMavlinkParam(key, valueFloat, type)

                when (res) {
                    is ParameterRepository.ParameterResult.Success -> _successMessage.value = "$key set -> ${res.parameter.value} (type=${res.parameter.type})"
                    is ParameterRepository.ParameterResult.Error -> _errorMessage.value = "Failed to set $key: ${res.message}"
                    is ParameterRepository.ParameterResult.Timeout -> _errorMessage.value = "Timeout writing $key"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Exception writing param: ${e.message}"
            }
        }
    }

    /**
     * Write all battery failsafe parameters at once, using correct types per parameter.
     * Refactored to use setMavlinkParam to confirm each write.
     */
    fun setAllBatteryFailsafe() {
        val c = _config.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Validate presence
                val battAct = c.battFsLowAct ?: throw IllegalStateException("BATT_FS_LOW_ACT is empty")
                val lowVolt = c.lowVolt ?: throw IllegalStateException("LOW_VOLT is empty")
                val fsMah = c.fsBattMah ?: throw IllegalStateException("FS_BATT_MAH is empty")
                val lowTimer = c.battLowTimer ?: throw IllegalStateException("BATT_LOW_TIMER is empty")

                val results = listOf(
                    setMavlinkParam(BATT_FS_LOW_ACT, battAct.toFloat(), MavParamType.INT8),
                    setMavlinkParam(LOW_VOLT, lowVolt, MavParamType.REAL32),
                    setMavlinkParam(FS_BATT_MAH, fsMah.toFloat(), MavParamType.INT32),
                    setMavlinkParam(BATT_LOW_TIMER, lowTimer.toFloat(), MavParamType.INT16)
                )

                val anyFail = results.any { it !is ParameterRepository.ParameterResult.Success }
                if (anyFail) {
                    _errorMessage.value = "Failed to set some battery parameters"
                } else {
                    _successMessage.value = "Battery failsafe updated"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    private fun applyLocalConfigUpdate(paramName: String, value: Float) {
        _config.update { c ->
            when (paramName.trim().uppercase()) {
                FS_THR_ENABLE -> c.copy(fsThrEnable = value.toInt())
                FS_THR_VALUE -> c.copy(fsThrValue = value.toInt())
                BATT_FS_LOW_ACT -> c.copy(battFsLowAct = value.toInt())
                LOW_VOLT -> c.copy(lowVolt = value)
                FS_BATT_MAH -> c.copy(fsBattMah = value.toInt())
                BATT_LOW_TIMER -> c.copy(battLowTimer = value.toInt())
                FS_GCS_ENABLE -> c.copy(fsGcsEnable = value.toInt())
                else -> c
            }
        }
    }

    fun updateLocalConfig(paramName: String, newValue: Any?) {
        // Update local state when user edits a field (without writing yet)
        _config.update { c ->
            when (paramName.trim().uppercase()) {
                FS_THR_ENABLE -> c.copy(fsThrEnable = (newValue as? Int))
                FS_THR_VALUE -> c.copy(fsThrValue = (newValue as? Int))
                BATT_FS_LOW_ACT -> c.copy(battFsLowAct = (newValue as? Int))
                LOW_VOLT -> c.copy(lowVolt = when (newValue) { is Float -> newValue; is String -> newValue.toFloatOrNull(); else -> null })
                FS_BATT_MAH -> c.copy(fsBattMah = (newValue as? Int))
                BATT_LOW_TIMER -> c.copy(battLowTimer = (newValue as? Int))
                FS_GCS_ENABLE -> c.copy(fsGcsEnable = (newValue as? Int))
                else -> c
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }

    // Formatting helpers
    fun formatArmingStatus(isArmed: Boolean): String = if (isArmed) "Armed" else "Disarmed"

    fun formatGpsStatus(fix: Int): String = when (fix) {
        0 -> "No GPS"
        1, 2 -> "No Fix"
        3 -> "3D Fix"
        else -> "Fix $fix"
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
    }
}
