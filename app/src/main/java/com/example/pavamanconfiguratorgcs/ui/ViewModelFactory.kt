package com.example.pavamanconfiguratorgcs.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.pavamanconfiguratorgcs.PavamanApplication
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository

class ViewModelFactory(
    private val telemetryRepository: TelemetryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(SharedViewModel::class.java) -> {
                SharedViewModel(telemetryRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

// Extension function to get the repository from the application
fun CreationExtras.application(): PavamanApplication {
    return this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PavamanApplication
}

