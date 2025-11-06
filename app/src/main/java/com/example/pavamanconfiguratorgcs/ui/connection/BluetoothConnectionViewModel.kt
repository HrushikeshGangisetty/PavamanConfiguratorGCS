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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothConnectionViewModel(
    private val context: Context,
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BluetoothConnectionVM"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _uiState = MutableStateFlow<BluetoothConnectionUiState>(BluetoothConnectionUiState.Initial)
    val uiState: StateFlow<BluetoothConnectionUiState> = _uiState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceInfo>> = _pairedDevices.asStateFlow()

    val connectionState = telemetryRepository.connectionState
    val droneHeartbeatReceived = telemetryRepository.droneHeartbeatReceived

    sealed class BluetoothConnectionUiState {
        object Initial : BluetoothConnectionUiState()
        object Loading : BluetoothConnectionUiState()
        object BluetoothNotSupported : BluetoothConnectionUiState()
        object BluetoothDisabled : BluetoothConnectionUiState()
        object PermissionDenied : BluetoothConnectionUiState()
        object Ready : BluetoothConnectionUiState()
        object Connecting : BluetoothConnectionUiState()
        object Connected : BluetoothConnectionUiState()
        data class Error(val message: String) : BluetoothConnectionUiState()
    }

    data class BluetoothDeviceInfo(
        val name: String,
        val address: String,
        val device: BluetoothDevice
    )

    init {
        checkBluetoothAvailability()
    }

    private fun checkBluetoothAvailability() {
        if (bluetoothAdapter == null) {
            _uiState.value = BluetoothConnectionUiState.BluetoothNotSupported
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _uiState.value = BluetoothConnectionUiState.BluetoothDisabled
            return
        }

        if (!hasBluetoothPermissions()) {
            _uiState.value = BluetoothConnectionUiState.PermissionDenied
            return
        }

        loadPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        if (!hasBluetoothPermissions()) {
            _uiState.value = BluetoothConnectionUiState.PermissionDenied
            return
        }

        try {
            val devices = bluetoothAdapter?.bondedDevices?.mapNotNull { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    device = device
                )
            } ?: emptyList()

            _pairedDevices.value = devices
            _uiState.value = BluetoothConnectionUiState.Ready
            Log.d(TAG, "Found ${devices.size} paired devices")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while loading paired devices", e)
            _uiState.value = BluetoothConnectionUiState.PermissionDenied
        }
    }

    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        viewModelScope.launch {
            try {
                _uiState.value = BluetoothConnectionUiState.Connecting
                Log.d(TAG, "Connecting to device: ${deviceInfo.name}")

                val connectionProvider = BluetoothConnectionProvider(deviceInfo.device)
                val result = telemetryRepository.connect(connectionProvider)

                result.onSuccess {
                    _uiState.value = BluetoothConnectionUiState.Connected
                    Log.d(TAG, "Successfully connected and verified heartbeat")
                }.onFailure { error ->
                    _uiState.value = BluetoothConnectionUiState.Error(
                        error.message ?: "Failed to connect"
                    )
                    Log.e(TAG, "Connection failed", error)
                }
            } catch (e: Exception) {
                _uiState.value = BluetoothConnectionUiState.Error(e.message ?: "Connection error")
                Log.e(TAG, "Error during connection", e)
            }
        }
    }

    fun disconnect() {
        telemetryRepository.disconnect()
        _uiState.value = BluetoothConnectionUiState.Ready
    }

    fun retry() {
        checkBluetoothAvailability()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryRepository.disconnect()
    }
}

