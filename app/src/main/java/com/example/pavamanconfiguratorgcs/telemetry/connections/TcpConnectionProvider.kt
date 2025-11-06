package com.example.pavamanconfiguratorgcs.telemetry.connections

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect

class TcpConnectionProvider(
    private val host: String,
    private val port: Int
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return TcpClientMavConnection(
            host,
            port,
            ArdupilotmegaDialect
        ).asCoroutine()
    }
}

