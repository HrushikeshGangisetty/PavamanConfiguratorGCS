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
import com.example.pavamanconfiguratorgcs.data.ParameterRepository
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

    init {
        startTelemetryCollectors()
        loadFailsafeParameters()
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
                        // Robust armed flag detection supporting both list and bitmask representations
                        val armed = try {
                            val base = msg.baseMode.value
                            when (base) {
                                is List<*> -> base.contains(MavModeFlag.SAFETY_ARMED)
                                is UInt -> (base.toInt() and MavModeFlag.SAFETY_ARMED.value.toInt()) != 0
                                is Int -> (base and MavModeFlag.SAFETY_ARMED.value.toInt()) != 0
                                else -> false
                            }
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
     * Write a single parameter change to the FCU immediately.
     * Chooses a parameter-type override based on known ArduPilot definitions to avoid
     * sending incorrect integer sizes (prevents rounding or truncation issues).
     */
    fun writeParameterChange(paramName: String, newValue: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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

                val (value, type) = when (newValue) {
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

                when (val res = parameterRepository.setParameter(key, value, paramType = type)) {
                    is ParameterRepository.ParameterResult.Success -> {
                        _successMessage.value = "$key set"
                        // Reflect change in config cache
                        applyLocalConfigUpdate(key, value)
                    }
                    is ParameterRepository.ParameterResult.Error -> _errorMessage.value = "Failed: ${res.message}"
                    is ParameterRepository.ParameterResult.Timeout -> _errorMessage.value = "Timeout writing $key"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Write all battery failsafe parameters at once, using correct types per parameter.
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
                    parameterRepository.setParameter(BATT_FS_LOW_ACT, battAct.toFloat(), MavParamType.INT8),
                    parameterRepository.setParameter(LOW_VOLT, lowVolt, MavParamType.REAL32),
                    parameterRepository.setParameter(FS_BATT_MAH, fsMah.toFloat(), MavParamType.INT32),
                    parameterRepository.setParameter(BATT_LOW_TIMER, lowTimer.toFloat(), MavParamType.INT16)
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
