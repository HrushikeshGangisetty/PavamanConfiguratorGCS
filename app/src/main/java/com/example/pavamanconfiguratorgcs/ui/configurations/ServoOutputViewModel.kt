package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.models.ServoChannel
import com.example.pavamanconfiguratorgcs.data.models.ServoFunction
import com.example.pavamanconfiguratorgcs.data.models.VehicleState
import com.example.pavamanconfiguratorgcs.data.repository.ServoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ServoOutputViewModel(
    private val servoRepository: ServoRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ServoOutputViewModel"
    }

    // Expose servo channels to UI
    val servoChannels: StateFlow<List<ServoChannel>> = servoRepository.servoChannels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Vehicle state for safety checks
    val vehicleState: StateFlow<VehicleState> = servoRepository.vehicleState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VehicleState()
        )

    // UI state for showing messages/errors
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // Track if test mode is enabled
    private val _testModeEnabled = MutableStateFlow(false)
    val testModeEnabled: StateFlow<Boolean> = _testModeEnabled.asStateFlow()

    init {
        // Request servo parameters when ViewModel is created
        viewModelScope.launch {
            servoRepository.requestServoParameters()
        }
    }

    /**
     * Update servo function for a channel
     */
    fun updateChannelFunction(channelIndex: Int, function: ServoFunction) {
        viewModelScope.launch {
            Log.d(TAG, "Update channel $channelIndex function to ${function.displayName}")
            val result = servoRepository.setServoFunction(channelIndex, function)
            result.onFailure { error ->
                _uiMessage.value = "Failed to update function: ${error.message}"
                Log.e(TAG, "Failed to update function", error)
            }.onSuccess {
                _uiMessage.value = "Function updated successfully"
            }
        }
    }

    /**
     * Update reverse setting for a channel
     */
    fun updateChannelReverse(channelIndex: Int, reversed: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "Update channel $channelIndex reverse to $reversed")
            val result = servoRepository.setServoReverse(channelIndex, reversed)
            result.onFailure { error ->
                _uiMessage.value = "Failed to update reverse: ${error.message}"
                Log.e(TAG, "Failed to update reverse", error)
            }.onSuccess {
                _uiMessage.value = "Reverse setting updated"
            }
        }
    }

    /**
     * Update min PWM for a channel
     */
    fun updateChannelMin(channelIndex: Int, minPwm: Int) {
        if (minPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $minPwm"
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "Update channel $channelIndex min to $minPwm")
            val result = servoRepository.setServoMin(channelIndex, minPwm)
            result.onFailure { error ->
                _uiMessage.value = "Failed to update min PWM: ${error.message}"
                Log.e(TAG, "Failed to update min PWM", error)
            }.onSuccess {
                _uiMessage.value = "Min PWM updated to $minPwm"
            }
        }
    }

    /**
     * Update trim PWM for a channel
     */
    fun updateChannelTrim(channelIndex: Int, trimPwm: Int) {
        if (trimPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $trimPwm"
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "Update channel $channelIndex trim to $trimPwm")
            val result = servoRepository.setServoTrim(channelIndex, trimPwm)
            result.onFailure { error ->
                _uiMessage.value = "Failed to update trim PWM: ${error.message}"
                Log.e(TAG, "Failed to update trim PWM", error)
            }.onSuccess {
                _uiMessage.value = "Trim PWM updated to $trimPwm"
            }
        }
    }

    /**
     * Update max PWM for a channel
     */
    fun updateChannelMax(channelIndex: Int, maxPwm: Int) {
        if (maxPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $maxPwm"
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "Update channel $channelIndex max to $maxPwm")
            val result = servoRepository.setServoMax(channelIndex, maxPwm)
            result.onFailure { error ->
                _uiMessage.value = "Failed to update max PWM: ${error.message}"
                Log.e(TAG, "Failed to update max PWM", error)
            }.onSuccess {
                _uiMessage.value = "Max PWM updated to $maxPwm"
            }
        }
    }

    /**
     * Send test PWM to a servo channel
     */
    fun testServo(channelIndex: Int, pwmValue: Int) {
        viewModelScope.launch {
            // Safety check
            if (!servoRepository.canSendServoCommands()) {
                _uiMessage.value = "Cannot send servo commands while armed!"
                return@launch
            }

            if (pwmValue !in 800..2200) {
                _uiMessage.value = "PWM value must be between 800 and 2200"
                return@launch
            }

            val result = servoRepository.setServoPwm(channelIndex, pwmValue)
            result.onSuccess {
                Log.d(TAG, "Successfully sent servo test: channel=$channelIndex, pwm=$pwmValue")
            }.onFailure { error ->
                _uiMessage.value = "Failed to test servo: ${error.message}"
                Log.e(TAG, "Failed to test servo", error)
            }
        }
    }

    /**
     * Enable/disable test mode
     */
    fun setTestMode(enabled: Boolean) {
        if (enabled && vehicleState.value.isArmed) {
            _uiMessage.value = "Cannot enable test mode while armed!"
            return
        }
        _testModeEnabled.value = enabled
    }

    /**
     * Clear UI message
     */
    fun clearMessage() {
        _uiMessage.value = null
    }

    /**
     * Refresh servo parameters from vehicle
     */
    fun refreshParameters() {
        viewModelScope.launch {
            servoRepository.requestServoParameters()
            _uiMessage.value = "Refreshing parameters..."
        }
    }
}
