package com.example.pavamanconfiguratorgcs.telemetry.connections

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.connection.BufferedMavConnection
import com.divpundir.mavlink.connection.MavConnection
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect
import com.hoho.android.usbserial.driver.UsbSerialPort
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * UsbSerialMavConnection wraps a USB serial port and implements the MavConnection interface.
 * This allows MAVLink communication over USB serial connections (direct USB or telemetry radios).
 */
class UsbSerialMavConnection(
    private val port: UsbSerialPort,
    private val connection: UsbDeviceConnection,
    private val baudRate: Int,
    private val dataBits: Int = 8,
    private val stopBits: Int = UsbSerialPort.STOPBITS_1,
    private val parity: Int = UsbSerialPort.PARITY_NONE
) : MavConnection {

    companion object {
        private const val TAG = "UsbSerialMavConnection"
    }

    private var bufferedConnection: BufferedMavConnection? = null
    private var isPortOpen = false

    @Throws(IOException::class)
    override fun connect() {
        if (bufferedConnection != null) {
            Log.d(TAG, "Connection already exists, closing first")
            close()
        }

        try {
            Log.d(TAG, "Opening USB serial port with baud rate: $baudRate")

            // Check if port is already open
            if (isPortOpen) {
                Log.w(TAG, "Port already open, closing first")
                try {
                    port.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing already open port", e)
                }
                isPortOpen = false
            }

            // Open the USB serial port
            port.open(connection)
            isPortOpen = true
            Log.d(TAG, "USB port opened successfully")

            // Set serial port parameters
            port.setParameters(baudRate, dataBits, stopBits, parity)
            Log.d(TAG, "USB port parameters set: baud=$baudRate, data=$dataBits, stop=$stopBits, parity=$parity")

            // Purge any stale data
            try {
                port.purgeHwBuffers(true, true)
                Log.d(TAG, "USB buffers purged")
            } catch (e: Exception) {
                Log.w(TAG, "Could not purge buffers (may not be supported)", e)
            }

            // Create InputStream wrapper for the USB serial port
            val inputStream = UsbSerialInputStream(port)

            // Create OutputStream wrapper for the USB serial port
            val outputStream = UsbSerialOutputStream(port)

            // Create buffered MAVLink connection using the streams
            this.bufferedConnection = BufferedMavConnection(
                inputStream.source().buffer(),
                outputStream.sink().buffer(),
                port, // The port is the closeable resource
                ArdupilotmegaDialect
            )

            Log.d(TAG, "USB MAVLink connection initialized successfully")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open USB connection", e)
            close()
            throw IOException("Failed to open USB serial port: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error opening USB connection", e)
            close()
            throw IOException("Unexpected error: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        Log.d(TAG, "Closing USB connection")

        try {
            bufferedConnection?.close()
            Log.d(TAG, "Buffered connection closed")
        } catch (@Suppress("SwallowedException") e: IOException) {
            Log.w(TAG, "Error closing buffered connection", e)
        }
        bufferedConnection = null

        if (isPortOpen) {
            try {
                port.close()
                isPortOpen = false
                Log.d(TAG, "USB port closed")
            } catch (@Suppress("SwallowedException") e: IOException) {
                Log.w(TAG, "Error closing USB port", e)
            }
        }
    }

    @Throws(IOException::class)
    override fun next(): MavFrame<out MavMessage<*>> {
        return bufferedConnection?.next() ?: throw IOException("Connection is not active.")
    }

    @Throws(IOException::class)
    override fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T) {
        bufferedConnection?.sendV1(systemId, componentId, payload)
            ?: throw IOException("Connection is not active.")
    }

    @Throws(IOException::class)
    override fun <T : MavMessage<T>> sendUnsignedV2(systemId: UByte, componentId: UByte, payload: T) {
        bufferedConnection?.sendUnsignedV2(systemId, componentId, payload)
            ?: throw IOException("Connection is not active.")
    }

    @Throws(IOException::class)
    override fun <T : MavMessage<T>> sendSignedV2(
        systemId: UByte,
        componentId: UByte,
        payload: T,
        linkId: UByte,
        timestamp: UInt,
        secretKey: ByteArray
    ) {
        bufferedConnection?.sendSignedV2(systemId, componentId, payload, linkId, timestamp, secretKey)
            ?: throw IOException("Connection is not active.")
    }

    /**
     * InputStream wrapper for UsbSerialPort to adapt read() method to InputStream interface
     */
    private class UsbSerialInputStream(private val port: UsbSerialPort) : InputStream() {
        private val buffer = ByteArray(1)
        private val READ_TIMEOUT_MS = 1000

        override fun read(): Int {
            return try {
                val bytesRead = port.read(buffer, READ_TIMEOUT_MS)
                if (bytesRead > 0) buffer[0].toInt() and 0xFF else -1
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from USB port", e)
                throw IOException("USB read error: ${e.message}", e)
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return try {
                val tempBuffer = ByteArray(len)
                val bytesRead = port.read(tempBuffer, READ_TIMEOUT_MS)
                if (bytesRead > 0) {
                    System.arraycopy(tempBuffer, 0, b, off, bytesRead)
                }
                bytesRead
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from USB port", e)
                throw IOException("USB read error: ${e.message}", e)
            }
        }
    }

    /**
     * OutputStream wrapper for UsbSerialPort to adapt write() method to OutputStream interface
     */
    private class UsbSerialOutputStream(private val port: UsbSerialPort) : OutputStream() {
        private val WRITE_TIMEOUT_MS = 1000

        override fun write(b: Int) {
            try {
                port.write(byteArrayOf(b.toByte()), WRITE_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to USB port", e)
                throw IOException("USB write error: ${e.message}", e)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            try {
                val tempBuffer = if (off == 0 && len == b.size) {
                    b
                } else {
                    b.copyOfRange(off, off + len)
                }
                port.write(tempBuffer, WRITE_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to USB port", e)
                throw IOException("USB write error: ${e.message}", e)
            }
        }
    }
}
