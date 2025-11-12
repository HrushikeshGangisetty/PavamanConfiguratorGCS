package com.example.pavamanconfiguratorgcs.telemetry.connections

import android.hardware.usb.UsbDeviceConnection
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.hoho.android.usbserial.driver.UsbSerialDriver

/**
 * UsbSerialConnectionProvider creates USB serial MAVLink connections.
 * Follows the same Provider pattern as TCP and Bluetooth connections.
 */
class UsbSerialConnectionProvider(
    private val driver: UsbSerialDriver,
    private val connection: UsbDeviceConnection,
    private val baudRate: Int
) : MavConnectionProvider {

    override fun createConnection(): CoroutinesMavConnection {
        // Get the first port from the driver (most devices have only one)
        val port = driver.ports[0]

        // Create the USB serial MAVLink connection wrapper
        val usbMavConnection = UsbSerialMavConnection(port, connection, baudRate)

        // Convert to coroutines-based connection
        return usbMavConnection.asCoroutine()
    }
}

