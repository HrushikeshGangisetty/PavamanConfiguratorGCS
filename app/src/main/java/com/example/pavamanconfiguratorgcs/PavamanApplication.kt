package com.example.pavamanconfiguratorgcs

import android.app.Application
import com.example.pavamanconfiguratorgcs.data.repository.ServoRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository

class PavamanApplication : Application() {
    // Singleton repository for the entire app
    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository()
    }

    val servoRepository: ServoRepository by lazy {
        ServoRepository(telemetryRepository)
    }
}
