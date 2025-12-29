package com.example.pavamanconfiguratorgcs.telemetry.connections

/**
 * TCP Connection Mode
 * 
 * Defines the mode for TCP MAVLink connections.
 * Part of the connection service layer.
 */
enum class TcpMode {
    /**
     * Client mode: Connects to a remote MAVLink server.
     * Requires host IP address and port number.
     */
    CLIENT,
    
    /**
     * Server mode: Listens for incoming MAVLink connections.
     * Requires only a port number to listen on.
     */
    SERVER
}
