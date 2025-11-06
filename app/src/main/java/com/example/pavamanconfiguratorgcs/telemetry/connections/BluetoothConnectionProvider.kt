package com.example.pavamanconfiguratorgcs.telemetry.connections

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine

@SuppressLint("MissingPermission")
class BluetoothConnectionProvider(
    private val device: BluetoothDevice
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return BluetoothMavConnection(device).asCoroutine()
    }
}

