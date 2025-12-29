package com.example.pavamanconfiguratorgcs.telemetry.connections

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.connection.tcp.TcpServerMavConnection
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect

/**
 * TCP Server Connection Provider
 * 
 * Creates a TCP server that listens for incoming MAVLink connections.
 * Useful for SITL (Software In The Loop) simulators or when the GCS
 * needs to act as a server waiting for drone connections.
 * 
 * Following MSSV architecture pattern for connection abstraction.
 */
class TcpServerConnectionProvider(
    private val port: Int
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return TcpServerMavConnection(
            port,
            ArdupilotmegaDialect
        ).asCoroutine()
    }
}
