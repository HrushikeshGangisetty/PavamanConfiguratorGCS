package com.example.pavamanconfiguratorgcs.ui

import androidx.lifecycle.ViewModel
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * SharedViewModel that holds the TelemetryRepository and exposes connection state
 * across all screens in the app. This follows MVVM architecture pattern.
 */
class SharedViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    // Expose repository's connection state
    val connectionState: StateFlow<TelemetryRepository.ConnectionState> =
        telemetryRepository.connectionState

    val droneHeartbeatReceived: StateFlow<Boolean> =
        telemetryRepository.droneHeartbeatReceived

    val fcuDetected: StateFlow<Boolean> =
        telemetryRepository.fcuDetected

    // Provide access to repository for ViewModels that need it
    fun getTelemetryRepository(): TelemetryRepository = telemetryRepository

    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed when ViewModel is destroyed
        telemetryRepository.disconnect()
    }
}

