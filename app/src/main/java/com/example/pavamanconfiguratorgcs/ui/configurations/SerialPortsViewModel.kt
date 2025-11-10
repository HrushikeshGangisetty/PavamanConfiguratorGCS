package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.models.*
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.data.repository.SerialPortRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Serial Ports configuration screen
 */
class SerialPortsViewModel(
    private val parameterRepository: ParameterRepository,
    private val serialPortRepository: SerialPortRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SerialPortsViewModel"
    }

    // Serial ports state
    val serialPorts = serialPortRepository.serialPorts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Loading state
    val isLoading = serialPortRepository.isLoading
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Error state
    val error = serialPortRepository.error
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // UI state for showing dialogs
    private val _showBitmaskDialog = MutableStateFlow<SerialPortConfig?>(null)
    val showBitmaskDialog: StateFlow<SerialPortConfig?> = _showBitmaskDialog.asStateFlow()

    private val _showRebootDialog = MutableStateFlow(false)
    val showRebootDialog: StateFlow<Boolean> = _showRebootDialog.asStateFlow()

    // Success/error messages for UI feedback
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        // Discover serial ports on initialization
        discoverPorts()
    }

    /**
     * Discover serial ports from parameters
     */
    fun discoverPorts() {
        viewModelScope.launch {
            Log.d(TAG, "Starting serial port discovery...")
            val result = serialPortRepository.discoverSerialPorts()

            result.fold(
                onSuccess = {
                    Log.i(TAG, "✅ Serial ports discovered successfully")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ Failed to discover serial ports: ${error.message}")
                }
            )
        }
    }

    /**
     * Change baud rate for a port
     */
    fun changeBaudRate(portNumber: Int, newBaud: Int) {
        viewModelScope.launch {
            Log.d(TAG, "Changing baud rate for port $portNumber to $newBaud")

            val result = serialPortRepository.changeBaudRate(portNumber, newBaud)

            result.fold(
                onSuccess = {
                    _feedbackMessage.value = "Baud rate updated. Reboot required."
                    _showRebootDialog.value = true
                },
                onFailure = { error ->
                    _feedbackMessage.value = "Failed to update baud rate: ${error.message}"
                }
            )
        }
    }

    /**
     * Change protocol for a port
     */
    fun changeProtocol(portNumber: Int, newProtocol: Int) {
        viewModelScope.launch {
            Log.d(TAG, "Changing protocol for port $portNumber to $newProtocol")

            val result = serialPortRepository.changeProtocol(portNumber, newProtocol)

            result.fold(
                onSuccess = {
                    _feedbackMessage.value = "Protocol updated. Reboot required."
                    _showRebootDialog.value = true
                },
                onFailure = { error ->
                    _feedbackMessage.value = "Failed to update protocol: ${error.message}"
                }
            )
        }
    }

    /**
     * Change options bitmask for a port
     */
    fun changeOptions(portNumber: Int, newOptions: Int) {
        viewModelScope.launch {
            Log.d(TAG, "Changing options for port $portNumber to $newOptions")

            val result = serialPortRepository.changeOptions(portNumber, newOptions)

            result.fold(
                onSuccess = {
                    _feedbackMessage.value = "Options updated. Reboot required."
                    _showRebootDialog.value = true
                    _showBitmaskDialog.value = null
                },
                onFailure = { error ->
                    _feedbackMessage.value = "Failed to update options: ${error.message}"
                }
            )
        }
    }

    /**
     * Show bitmask editor dialog
     */
    fun showBitmaskEditor(port: SerialPortConfig) {
        _showBitmaskDialog.value = port
    }

    /**
     * Hide bitmask editor dialog
     */
    fun hideBitmaskEditor() {
        _showBitmaskDialog.value = null
    }

    /**
     * Hide reboot dialog
     */
    fun hideRebootDialog() {
        _showRebootDialog.value = false
    }

    /**
     * Clear feedback message
     */
    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    /**
     * Clear error
     */
    fun clearError() {
        serialPortRepository.clearError()
    }

    /**
     * Trigger vehicle reboot
     */
    fun rebootVehicle() {
        viewModelScope.launch {
            Log.d(TAG, "User requested vehicle reboot")
            // Note: Actual reboot command would be sent via MAVLink COMMAND_LONG
            // MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN with param1=1
            // For safety, we'll just show a message to manually reboot
            _feedbackMessage.value = "Please manually power-cycle the vehicle to apply changes"
            _showRebootDialog.value = false
        }
    }
}

