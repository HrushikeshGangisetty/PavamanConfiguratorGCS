package com.example.pavamanconfiguratorgcs.ui.connection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import com.example.pavamanconfiguratorgcs.telemetry.connections.BluetoothConnectionProvider
import com.example.pavamanconfiguratorgcs.telemetry.connections.TcpConnectionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    context: Context,
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ConnectionViewModel"
    }

    // Store only the application context to avoid leaking an Activity context
    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager? = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionType = MutableStateFlow(ConnectionType.TCP)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _ipAddress = MutableStateFlow("10.0.2.2")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow("5762")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<PairedDevice?>(null)
    val selectedDevice: StateFlow<PairedDevice?> = _selectedDevice.asStateFlow()

    val connectionState = telemetryRepository.connectionState
    @Suppress("unused") // kept for potential UI binding
    val droneHeartbeatReceived = telemetryRepository.droneHeartbeatReceived

    private var bluetoothDeviceMap = mutableMapOf<String, BluetoothDevice>()

    fun onConnectionTypeChange(type: ConnectionType) {
        _connectionType.value = type
    }

    fun onIpAddressChange(ip: String) {
        _ipAddress.value = ip
    }

    fun onPortChange(portValue: String) {
        _port.value = portValue
    }

    fun onDeviceSelected(device: PairedDevice) {
        _selectedDevice.value = device
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Bluetooth permissions not granted")
            return
        }

        try {
            val pairedBtDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            val devices = pairedBtDevices.map { device ->
                bluetoothDeviceMap[device.address] = device
                PairedDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }
            _pairedDevices.value = devices
            Log.d(TAG, "Found ${devices.size} paired Bluetooth devices")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while loading paired devices", e)
        }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (_connectionType.value) {
                    ConnectionType.TCP -> {
                        val host = _ipAddress.value
                        val portNum = _port.value.toIntOrNull() ?: 5760
                        Log.d(TAG, "Connecting to TCP: $host:$portNum")

                        val connectionProvider = TcpConnectionProvider(host, portNum)
                        val result = telemetryRepository.connect(connectionProvider)

                        result.onFailure { error ->
                            Log.e(TAG, "TCP connection failed: ${error.message}", error)
                        }
                    }
                    ConnectionType.BLUETOOTH -> {
                        val selected = _selectedDevice.value
                        if (selected == null) {
                            Log.w(TAG, "No Bluetooth device selected")
                            return@launch
                        }

                        val bluetoothDevice = bluetoothDeviceMap[selected.address]
                        if (bluetoothDevice == null) {
                            Log.e(TAG, "Bluetooth device not found in map")
                            return@launch
                        }

                        Log.d(TAG, "Connecting to Bluetooth device: ${selected.name}")
                        val connectionProvider = BluetoothConnectionProvider(bluetoothDevice)
                        val result = telemetryRepository.connect(connectionProvider)

                        result.onFailure { error ->
                            Log.e(TAG, "Bluetooth connection failed: ${error.message}", error)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during connection", e)
            }
        }
    }

    fun cancelConnection() {
        telemetryRepository.disconnect()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
