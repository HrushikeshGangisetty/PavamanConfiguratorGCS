package com.example.pavamanconfiguratorgcs.ui.configurations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.models.FrameType
import com.example.pavamanconfiguratorgcs.data.models.FrameConfig
import com.example.pavamanconfiguratorgcs.data.models.MotorLayout
import com.example.pavamanconfiguratorgcs.data.repository.FrameTypeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for Frame Type selection and configuration
 * Supports Quad-X, Hexa-X, and Octa-X frame types only
 */
class FrameTypeViewModel(
    private val frameTypeRepository: FrameTypeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FrameTypeViewModel"
    }

    // Frame configuration state
    val frameConfig: StateFlow<FrameConfig> = frameTypeRepository.frameConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FrameConfig(
                currentFrameType = null,
                paramScheme = com.example.pavamanconfiguratorgcs.data.models.FrameParamScheme.UNKNOWN,
                isDetected = false
            )
        )

    // Loading state
    val isLoading: StateFlow<Boolean> = frameTypeRepository.isLoading
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Error state
    val error: StateFlow<String?> = frameTypeRepository.error
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // UI message state
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // Available frame types (restricted to Quad-X, Hexa-X, Octa-X)
    val availableFrameTypes: List<FrameType> = frameTypeRepository.getAvailableFrameTypes()

    // Motor layout for current frame
    val motorLayout: StateFlow<MotorLayout?> = frameConfig.map { config ->
        config.currentFrameType?.let { frameTypeRepository.getCurrentMotorLayout() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Detect frame parameters from the vehicle
     * Call this after parameters are loaded
     */
    fun detectFrameParameters() {
        viewModelScope.launch {
            Log.d(TAG, "Detecting frame parameters...")
            // Clear any stale messages
            _uiMessage.value = null

            val result = frameTypeRepository.detectFrameParameters()

            result.fold(
                onSuccess = { config ->
                    val message = if (config.isValid) {
                        "Frame type detected: ${config.currentFrameType?.displayName}"
                    } else {
                        "Frame parameters not found or unsupported frame type"
                    }
                    _uiMessage.value = message
                    Log.i(TAG, message)
                },
                onFailure = { error ->
                    val message = "Failed to detect frame: ${error.message}"
                    _uiMessage.value = null // Don't show UI message for errors, use error state
                    Log.e(TAG, message, error)
                }
            )
        }
    }

    /**
     * Change to a new frame type
     * @param frameType The new frame type (Quad-X, Hexa-X, or Octa-X)
     */
    fun changeFrameType(frameType: FrameType) {
        viewModelScope.launch {
            Log.d(TAG, "Changing frame type to ${frameType.displayName}...")

            // Clear previous messages
            _uiMessage.value = null

            val result = frameTypeRepository.changeFrameType(frameType)

            result.fold(
                onSuccess = {
                    val message = "Frame type changed to ${frameType.displayName}. Please reboot the vehicle for changes to take effect."
                    _uiMessage.value = message
                    Log.i(TAG, "✅ $message")
                },
                onFailure = { error ->
                    val message = "Failed to change frame type: ${error.message}"
                    _uiMessage.value = message
                    Log.e(TAG, "❌ $message", error)
                }
            )
        }
    }

    /**
     * Clear the reboot required flag after vehicle reboot
     */
    fun acknowledgeReboot() {
        frameTypeRepository.clearRebootRequired()
        _uiMessage.value = "Reboot acknowledged. Re-detecting frame configuration..."

        // Re-detect parameters after reboot acknowledgment
        viewModelScope.launch {
            delay(500)
            detectFrameParameters()
        }
    }

    /**
     * Clear UI message
     */
    fun clearMessage() {
        _uiMessage.value = null
    }

    /**
     * Clear error state
     */
    fun clearError() {
        frameTypeRepository.clearError()
    }
}
