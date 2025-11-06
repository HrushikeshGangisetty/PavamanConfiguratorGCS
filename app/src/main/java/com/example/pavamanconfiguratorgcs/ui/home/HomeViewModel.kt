package com.example.pavamanconfiguratorgcs.ui.home

import androidx.lifecycle.ViewModel
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for HomeScreen following MVVM architecture.
 * Exposes only the necessary state to the UI layer.
 */
class HomeViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    val droneHeartbeatReceived: StateFlow<Boolean> =
        telemetryRepository.droneHeartbeatReceived

    val connectionState: StateFlow<TelemetryRepository.ConnectionState> =
        telemetryRepository.connectionState
}

