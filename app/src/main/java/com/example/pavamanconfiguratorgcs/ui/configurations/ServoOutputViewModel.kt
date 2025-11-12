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
        // This would typically send a PARAM_SET command
        // For now, just log it
        Log.d(TAG, "Update channel $channelIndex function to ${function.displayName}")
        // TODO: Implement PARAM_SET command
    }

    /**
     * Update reverse setting for a channel
     */
    fun updateChannelReverse(channelIndex: Int, reversed: Boolean) {
        Log.d(TAG, "Update channel $channelIndex reverse to $reversed")
        // TODO: Implement PARAM_SET command
    }

    /**
     * Update min PWM for a channel
     */
    fun updateChannelMin(channelIndex: Int, minPwm: Int) {
        if (minPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $minPwm"
            return
        }
        Log.d(TAG, "Update channel $channelIndex min to $minPwm")
        // TODO: Implement PARAM_SET command
    }

    /**
     * Update trim PWM for a channel
     */
    fun updateChannelTrim(channelIndex: Int, trimPwm: Int) {
        if (trimPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $trimPwm"
            return
        }
        Log.d(TAG, "Update channel $channelIndex trim to $trimPwm")
        // TODO: Implement PARAM_SET command
    }

    /**
     * Update max PWM for a channel
     */
    fun updateChannelMax(channelIndex: Int, maxPwm: Int) {
        if (maxPwm !in 800..2200) {
            _uiMessage.value = "Invalid PWM value: $maxPwm"
            return
        }
        Log.d(TAG, "Update channel $channelIndex max to $maxPwm")
        // TODO: Implement PARAM_SET command
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

