package com.example.pavamanconfiguratorgcs.telemetry

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.tryConnect
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.connection.StreamState
import com.divpundir.mavlink.definitions.minimal.*
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.definitions.ardupilotmega.MagCalProgress
import com.example.pavamanconfiguratorgcs.telemetry.connections.MavConnectionProvider
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

class TelemetryRepository {

    companion object {
        private const val TAG = "TelemetryRepository"
        private const val HEARTBEAT_TIMEOUT_MS = 3000L
    }

    val gcsSystemId: UByte = 255u
    val gcsComponentId: UByte = 190u

    var fcuSystemId: UByte = 0u
    var fcuComponentId: UByte = 0u

    private val scope = CoroutineScope(Dispatchers.IO)

    private var connectionProvider: MavConnectionProvider? = null
    var connection: CoroutinesMavConnection? = null
        private set
    lateinit var mavFrame: Flow<MavFrame<out MavMessage<*>>>
        private set

    // Parameter repository instance (lazy initialized)
    private var parameterRepository: ParameterRepository? = null

    // Track last heartbeat time from FCU (thread-safe using AtomicLong)
    private val lastFcuHeartbeatTime = AtomicLong(0L)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _droneHeartbeatReceived = MutableStateFlow(false)
    val droneHeartbeatReceived: StateFlow<Boolean> = _droneHeartbeatReceived.asStateFlow()

    private val _fcuDetected = MutableStateFlow(false)
    val fcuDetected: StateFlow<Boolean> = _fcuDetected.asStateFlow()

    // Compass calibration message flows
    private val _magCalProgress = MutableSharedFlow<MagCalProgress>(replay = 0, extraBufferCapacity = 10)
    val magCalProgress: SharedFlow<MagCalProgress> = _magCalProgress.asSharedFlow()

    private val _magCalReport = MutableSharedFlow<MagCalReport>(replay = 0, extraBufferCapacity = 10)
    val magCalReport: SharedFlow<MagCalReport> = _magCalReport.asSharedFlow()

    private val _commandAck = MutableSharedFlow<CommandAck>(replay = 0, extraBufferCapacity = 10)
    val commandAck: SharedFlow<CommandAck> = _commandAck.asSharedFlow()

    private val _calibrationStatus = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val calibrationStatus: SharedFlow<String> = _calibrationStatus.asSharedFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object VerifyingHeartbeat : ConnectionState()
        object HeartbeatVerified : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    suspend fun connect(provider: MavConnectionProvider): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.Connecting
            _droneHeartbeatReceived.value = false
            _fcuDetected.value = false

            connectionProvider = provider
            val conn = provider.createConnection()
            connection = conn

            // Try to connect
            if (!conn.tryConnect(scope)) {
                throw Exception("Failed to establish connection")
            }

            _connectionState.value = ConnectionState.Connected
            Log.d(TAG, "MAVLink connection established")

            // Start all collectors
            startCollectors()

            // Start sending heartbeats
            startSendingHeartbeats()

            // Start monitoring connection state
            startStreamStateMonitor()

            // Start heartbeat timeout monitor
            startHeartbeatTimeoutMonitor()

            // Wait for FCU detection and heartbeat
            _connectionState.value = ConnectionState.VerifyingHeartbeat
            val heartbeatReceived = waitForDroneHeartbeat()

            if (heartbeatReceived) {
                _connectionState.value = ConnectionState.HeartbeatVerified
                _droneHeartbeatReceived.value = true
                Log.d(TAG, "Drone heartbeat verified")
                Result.success(Unit)
            } else {
                disconnect()
                _connectionState.value = ConnectionState.Error("No heartbeat received from drone")
                Result.failure(Exception("No heartbeat received from drone"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    private suspend fun waitForDroneHeartbeat(): Boolean {
        val startTime = System.currentTimeMillis()
        val timeoutMs = 5000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (_droneHeartbeatReceived.value && _fcuDetected.value) {
                return true
            }
            delay(100)
        }

        Log.w(TAG, "Timeout waiting for drone heartbeat")
        return false
    }

    private fun startCollectors() {
        val conn = connection ?: return

        // Shared message stream
        mavFrame = conn.mavFrame
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

        // NOTE: removed unfiltered logging of every MAVLink frame to avoid flooding logs and
        // doing unnecessary processing on each message. Keep only targeted collectors below
        // (e.g., heartbeat handling) which perform the required logic.

        // Detect FCU and handle heartbeat
        scope.launch {
            mavFrame
                .filter { it.message is Heartbeat && (it.message as Heartbeat).type != MavType.GCS.wrap() }
                .collect { frame ->
                    // Update heartbeat timestamp
                    lastFcuHeartbeatTime.set(System.currentTimeMillis())

                    if (!_fcuDetected.value) {
                        fcuSystemId = frame.systemId
                        fcuComponentId = frame.componentId
                        Log.i(TAG, "FCU detected sysId=$fcuSystemId compId=$fcuComponentId")
                        _fcuDetected.value = true
                        _droneHeartbeatReceived.value = true
                        _connectionState.value = ConnectionState.Connected

                        // Automatically initialize ParameterRepository and load all parameters in background
                        scope.launch {
                            try {
                                Log.i(TAG, "🚀 Auto-loading parameters in background...")
                                val paramRepo = getParameterRepository()
                                val result = paramRepo.requestAllParameters()
                                if (result.isNotEmpty()) {
                                    Log.i(TAG, "✅ Background parameter loading completed successfully (${result.size} parameters)")
                                } else {
                                    Log.w(TAG, "⚠️ Background parameter loading returned empty result")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error during background parameter loading", e)
                            }
                        }
                    } else if (!_droneHeartbeatReceived.value) {
                        // FCU was detected before but heartbeat was lost, now it's back
                        Log.i(TAG, "FCU heartbeat resumed")
                        _droneHeartbeatReceived.value = true
                        if (_connectionState.value is ConnectionState.Error) {
                            _connectionState.value = ConnectionState.Connected
                        }
                    }
                }
        }

        // Collect MAG_CAL_PROGRESS messages
        scope.launch {
            mavFrame
                .filter { it.message is MagCalProgress }
                .collect { frame ->
                    val msg = frame.message as MagCalProgress
                    _magCalProgress.emit(msg)
                }
        }

        // Collect MAG_CAL_REPORT messages
        scope.launch {
            mavFrame
                .filter { it.message is MagCalReport }
                .collect { frame ->
                    val msg = frame.message as MagCalReport
                    _magCalReport.emit(msg)
                }
        }

        // Collect COMMAND_ACK messages
        scope.launch {
            mavFrame
                .filter { it.message is CommandAck }
                .collect { frame ->
                    val msg = frame.message as CommandAck
                    _commandAck.emit(msg)
                }
        }

        // Collect STATUSTEXT messages for calibration feedback
        scope.launch {
            mavFrame
                .filter { it.message is Statustext }
                .collect { frame ->
                    val msg = frame.message as Statustext
                    // Handle text as String directly (it's already a String in the MAVLink definition)
                    val text = msg.text.trimEnd('\u0000')
                    _calibrationStatus.emit(text)
                }
        }
    }

    private fun startSendingHeartbeats() {
        scope.launch {
            val heartbeat = Heartbeat(
                type = MavType.GCS.wrap(),
                autopilot = MavAutopilot.INVALID.wrap(),
                baseMode = emptyList<MavModeFlag>().wrap(),
                customMode = 0u,
                systemStatus = MavState.ACTIVE.wrap(),
                mavlinkVersion = 3u
            )

            while (isActive) {
                try {
                    connection?.trySendUnsignedV2(gcsSystemId, gcsComponentId, heartbeat)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send heartbeat", e)
                }
                delay(1000)
            }
        }
    }

    private fun startStreamStateMonitor() {
        scope.launch {
            connection?.streamState?.collect { state ->
                when (state) {
                    is StreamState.Active -> {
                        Log.i(TAG, "Stream Active - waiting for FCU heartbeat")
                    }
                    is StreamState.Inactive -> {
                        Log.i(TAG, "Stream Inactive")
                        if (_connectionState.value != ConnectionState.Disconnected) {
                            _connectionState.value = ConnectionState.Error("Connection lost")
                            _droneHeartbeatReceived.value = false
                            _fcuDetected.value = false
                            lastFcuHeartbeatTime.set(0L)
                        }
                    }
                }
            }
        }
    }

    private fun startHeartbeatTimeoutMonitor() {
        scope.launch {
            while (isActive) {
                delay(1000) // Check every second

                if (_fcuDetected.value && lastFcuHeartbeatTime.get() > 0L) {
                    val timeSinceLastHeartbeat = System.currentTimeMillis() - lastFcuHeartbeatTime.get()

                    if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        if (_droneHeartbeatReceived.value) {
                            Log.w(TAG, "FCU heartbeat timeout - marking as disconnected")
                            _connectionState.value = ConnectionState.Error("Heartbeat timeout")
                            _droneHeartbeatReceived.value = false
                            _fcuDetected.value = false
                            lastFcuHeartbeatTime.set(0L)
                        }
                    }
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                connection?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection", e)
            }
        }

        connection = null
        connectionProvider = null
        _connectionState.value = ConnectionState.Disconnected
        _droneHeartbeatReceived.value = false
        _fcuDetected.value = false
        lastFcuHeartbeatTime.set(0L)
        Log.d(TAG, "Disconnected")
    }

    /**
     * Set connection error state
     */
    fun setConnectionError(message: String) {
        _connectionState.value = ConnectionState.Error(message)
        Log.e(TAG, "Connection error: $message")
    }

    /**
     * Send a MAVLink command to the autopilot
     */
    suspend fun sendCommand(
        commandId: UInt,
        param1: Float = 0f,
        param2: Float = 0f,
        param3: Float = 0f,
        param4: Float = 0f,
        param5: Float = 0f,
        param6: Float = 0f,
        param7: Float = 0f
    ): Boolean {
        val conn = connection ?: return false

        try {
            // Use MavEnumValue.fromValue() to create the command enum value
            val commandEnum = MavEnumValue.fromValue<MavCmd>(commandId)

            val commandLong = CommandLong(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                command = commandEnum,
                confirmation = 0u,
                param1 = param1,
                param2 = param2,
                param3 = param3,
                param4 = param4,
                param5 = param5,
                param6 = param6,
                param7 = param7
            )

            conn.trySendUnsignedV2(gcsSystemId, gcsComponentId, commandLong)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command $commandId", e)
            return false
        }
    }

    /**
     * Wait for COMMAND_ACK with timeout
     */
    suspend fun awaitCommandAck(commandId: UInt, timeoutMs: Long = 5000): CommandAck? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                commandAck
                    .filter { it.command.value == commandId }
                    .first()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for command ack", e)
            null
        }
    }

    /**
     * Request message interval for MAG_CAL messages
     */
    suspend fun requestMagCalMessages(hz: Float = 10f) {
        val intervalUs = (1_000_000f / hz).toInt()

        // Request MAG_CAL_PROGRESS (message ID 191)
        sendCommand(
            commandId = 511u, // MAV_CMD_SET_MESSAGE_INTERVAL
            param1 = 191f,    // MAG_CAL_PROGRESS
            param2 = intervalUs.toFloat()
        )

        delay(50)

        // Request MAG_CAL_REPORT (message ID 192)
        sendCommand(
            commandId = 511u, // MAV_CMD_SET_MESSAGE_INTERVAL
            param1 = 192f,    // MAG_CAL_REPORT
            param2 = intervalUs.toFloat()
        )
    }

    /**
     * Stop MAG_CAL message streaming
     */
    suspend fun stopMagCalMessages() {
        // Stop MAG_CAL_PROGRESS (set interval to 0)
        sendCommand(
            commandId = 511u, // MAV_CMD_SET_MESSAGE_INTERVAL
            param1 = 191f,    // MAG_CAL_PROGRESS
            param2 = 0f       // Stop streaming
        )

        delay(50)

        // Stop MAG_CAL_REPORT
        sendCommand(
            commandId = 511u, // MAV_CMD_SET_MESSAGE_INTERVAL
            param1 = 192f,    // MAG_CAL_REPORT
            param2 = 0f       // Stop streaming
        )
    }

    /**
     * Get or create the ParameterRepository instance
     */
    fun getParameterRepository(): ParameterRepository {
        if (parameterRepository == null) {
            val conn = connection ?: throw IllegalStateException("Not connected to vehicle")
            parameterRepository = ParameterRepository(conn, scope)
        }
        return parameterRepository!!
    }
}
