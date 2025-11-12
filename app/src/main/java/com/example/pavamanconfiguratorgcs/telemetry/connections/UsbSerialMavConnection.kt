package com.example.pavamanconfiguratorgcs.telemetry.connections

import android.hardware.usb.UsbDeviceConnection
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

    private var bufferedConnection: BufferedMavConnection? = null

    @Throws(IOException::class)
    override fun connect() {
        if (bufferedConnection != null) {
            close()
        }

        try {
            // Open the USB serial port
            port.open(connection)
            port.setParameters(baudRate, dataBits, stopBits, parity)

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
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            bufferedConnection?.close()
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Safe to ignore exceptions during close
        }
        bufferedConnection = null

        try {
            port.close()
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Safe to ignore
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
            val bytesRead = port.read(buffer, READ_TIMEOUT_MS)
            return if (bytesRead > 0) buffer[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val tempBuffer = ByteArray(len)
            val bytesRead = port.read(tempBuffer, READ_TIMEOUT_MS)
            if (bytesRead > 0) {
                System.arraycopy(tempBuffer, 0, b, off, bytesRead)
            }
            return bytesRead
        }
    }

    /**
     * OutputStream wrapper for UsbSerialPort to adapt write() method to OutputStream interface
     */
    private class UsbSerialOutputStream(private val port: UsbSerialPort) : OutputStream() {
        private val WRITE_TIMEOUT_MS = 1000

        override fun write(b: Int) {
            port.write(byteArrayOf(b.toByte()), WRITE_TIMEOUT_MS)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val tempBuffer = if (off == 0 && len == b.size) {
                b
            } else {
                b.copyOfRange(off, off + len)
            }
            port.write(tempBuffer, WRITE_TIMEOUT_MS)
        }
    }
}
