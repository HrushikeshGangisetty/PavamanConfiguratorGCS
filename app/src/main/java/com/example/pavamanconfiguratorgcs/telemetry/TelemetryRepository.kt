package com.example.pavamanconfiguratorgcs.telemetry

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.tryConnect
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.connection.StreamState
import com.divpundir.mavlink.definitions.minimal.*
import com.example.pavamanconfiguratorgcs.telemetry.connections.MavConnectionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    // Track last heartbeat time from FCU (thread-safe using AtomicLong)
    private val lastFcuHeartbeatTime = AtomicLong(0L)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _droneHeartbeatReceived = MutableStateFlow(false)
    val droneHeartbeatReceived: StateFlow<Boolean> = _droneHeartbeatReceived.asStateFlow()

    private val _fcuDetected = MutableStateFlow(false)
    val fcuDetected: StateFlow<Boolean> = _fcuDetected.asStateFlow()

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

        // Log raw messages
        scope.launch {
            mavFrame.collect {
                Log.d(TAG, "Frame: ${it.message.javaClass.simpleName} (sysId=${it.systemId}, compId=${it.componentId})")
            }
        }

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
     * Expose underlying MAVLink connection for other components (nullable)
     */
    fun getConnection(): CoroutinesMavConnection? {
        return connection
    }
}
