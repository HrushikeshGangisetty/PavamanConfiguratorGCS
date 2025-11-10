package com.example.pavamanconfiguratorgcs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.pavamanconfiguratorgcs.PavamanApplication
import com.example.pavamanconfiguratorgcs.data.repository.ServoRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import com.example.pavamanconfiguratorgcs.ui.configurations.ServoOutputViewModel

class ViewModelFactory(
    private val telemetryRepository: TelemetryRepository,
    private val servoRepository: ServoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(SharedViewModel::class.java) -> {
                SharedViewModel(telemetryRepository) as T
            }
            modelClass.isAssignableFrom(ServoOutputViewModel::class.java) -> {
                ServoOutputViewModel(servoRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

// Extension function to get the repository from the application
fun CreationExtras.application(): PavamanApplication {
    return this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PavamanApplication
}
