package com.example.pavamanconfiguratorgcs.ui.connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import com.example.pavamanconfiguratorgcs.telemetry.connections.BluetoothConnectionProvider
import com.example.pavamanconfiguratorgcs.telemetry.connections.TcpConnectionProvider
import com.example.pavamanconfiguratorgcs.telemetry.connections.UsbSerialConnectionProvider
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
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
        private const val USB_PERMISSION_ACTION = "com.example.pavamanconfiguratorgcs.USB_PERMISSION"
    }

    // Store only the application context to avoid leaking an Activity context
    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager? = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val usbManager: UsbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

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

    // USB-specific state
    private val _usbDevices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val usbDevices: StateFlow<List<UsbSerialDevice>> = _usbDevices.asStateFlow()

    private val _selectedUsbDevice = MutableStateFlow<UsbSerialDevice?>(null)
    val selectedUsbDevice: StateFlow<UsbSerialDevice?> = _selectedUsbDevice.asStateFlow()

    private val _baudRate = MutableStateFlow(115200)
    val baudRate: StateFlow<Int> = _baudRate.asStateFlow()

    val connectionState = telemetryRepository.connectionState
    @Suppress("unused")
    val droneHeartbeatReceived = telemetryRepository.droneHeartbeatReceived

    private var bluetoothDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private var usbDriverMap = mutableMapOf<Int, UsbSerialDriver>()

    // USB permission receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (USB_PERMISSION_ACTION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB permission granted for ${it.deviceName}")
                            viewModelScope.launch {
                                connectToUsb(it)
                            }
                        }
                    } else {
                        Log.w(TAG, "USB permission denied for ${device?.deviceName}")
                    }
                }
            }
        }
    }

    // USB device detached receiver for handling physical disconnection
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                device?.let {
                    Log.d(TAG, "USB device detached: ${it.deviceName}")
                    // Check if this is the currently connected device
                    if (_selectedUsbDevice.value?.deviceId == it.deviceId) {
                        Log.w(TAG, "Active USB connection lost")
                        cancelConnection()
                    }
                }
            }
        }
    }

    init {
        // Register USB permission receiver
        val permissionFilter = IntentFilter(USB_PERMISSION_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbPermissionReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                appContext,
                usbPermissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Register USB detach receiver
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbDetachReceiver, detachFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(usbDetachReceiver, detachFilter)
        }
    }

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

    fun onBaudRateChange(rate: Int) {
        _baudRate.value = rate
    }

    fun onUsbDeviceSelected(device: UsbSerialDevice) {
        _selectedUsbDevice.value = device
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

    fun discoverUsbDevices() {
        try {
            val prober = UsbSerialProber.getDefaultProber()
            val availableDrivers = prober.findAllDrivers(usbManager)

            val devices = availableDrivers.map { driver ->
                usbDriverMap[driver.device.deviceId] = driver
                UsbSerialDevice(
                    name = driver.device.productName ?: "Unknown USB Device",
                    vendorId = driver.device.vendorId,
                    productId = driver.device.productId,
                    deviceId = driver.device.deviceId
                )
            }

            _usbDevices.value = devices
            Log.d(TAG, "Found ${devices.size} USB serial devices")
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering USB devices", e)
            _usbDevices.value = emptyList()
        }
    }

    private suspend fun connectToUsb(usbDevice: UsbDevice) {
        val driver = usbDriverMap[usbDevice.deviceId]
        if (driver == null) {
            Log.e(TAG, "USB driver not found for device ${usbDevice.deviceId}")
            return
        }

        val connection = usbManager.openDevice(usbDevice)
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device")
            return
        }

        try {
            Log.d(TAG, "Connecting to USB device: ${usbDevice.productName} at ${_baudRate.value} baud")
            val connectionProvider = UsbSerialConnectionProvider(driver, connection, _baudRate.value)
            val result = telemetryRepository.connect(connectionProvider)

            result.onFailure { error ->
                Log.e(TAG, "USB connection failed: ${error.message}", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during USB connection", e)
            connection.close()
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
                    ConnectionType.USB -> {
                        val selected = _selectedUsbDevice.value
                        if (selected == null) {
                            Log.w(TAG, "No USB device selected")
                            return@launch
                        }

                        val driver = usbDriverMap[selected.deviceId]
                        if (driver == null) {
                            Log.e(TAG, "USB driver not found")
                            return@launch
                        }

                        // Request USB permission
                        if (!usbManager.hasPermission(driver.device)) {
                            Log.d(TAG, "Requesting USB permission for ${driver.device.deviceName}")
                            val permissionIntent = PendingIntent.getBroadcast(
                                appContext,
                                0,
                                Intent(USB_PERMISSION_ACTION),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                            usbManager.requestPermission(driver.device, permissionIntent)
                        } else {
                            // Already have permission, connect directly
                            Log.d(TAG, "USB permission already granted")
                            connectToUsb(driver.device)
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

    override fun onCleared() {
        super.onCleared()
        try {
            appContext.unregisterReceiver(usbPermissionReceiver)
            appContext.unregisterReceiver(usbDetachReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers", e)
        }
    }
}
