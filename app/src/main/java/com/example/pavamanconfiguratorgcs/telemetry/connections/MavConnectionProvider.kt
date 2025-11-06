package com.example.pavamanconfiguratorgcs.telemetry.connections

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection

interface MavConnectionProvider {
    fun createConnection(): CoroutinesMavConnection
}

