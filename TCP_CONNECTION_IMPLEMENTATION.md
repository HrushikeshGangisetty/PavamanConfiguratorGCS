# TCP Connection Implementation

This document describes the TCP connection implementation in the Pavaman Configurator GCS, following the roadmap for TCP & USB connections.

## Overview

The TCP connection implementation supports both **Client** and **Server** modes, allowing flexible connectivity options for drone communication.

## Architecture

The implementation follows the **MSSV (Model-Service-ViewModel-View)** architecture pattern:

### 1. Service Layer (Connection Providers)

#### TcpConnectionProvider (Client Mode)
- **File**: `app/src/main/java/.../telemetry/connections/TcpConnectionProvider.kt`
- **Purpose**: Connects to a remote MAVLink server (e.g., SITL simulator, telemetry module)
- **Usage**: Requires host IP address and port number
- **Example**: Connecting to `127.0.0.1:5760`

```kotlin
val provider = TcpConnectionProvider("127.0.0.1", 5760)
```

#### TcpServerConnectionProvider (Server Mode)
- **File**: `app/src/main/java/.../telemetry/connections/TcpServerConnectionProvider.kt`
- **Purpose**: Listens for incoming MAVLink connections from drones
- **Usage**: Requires only a port number to listen on
- **Example**: Listening on port `5762`

```kotlin
val provider = TcpServerConnectionProvider(5762)
```

### 2. Model Layer (TelemetryRepository)

The `TelemetryRepository` manages the connection lifecycle:
- Accepts any `MavConnectionProvider` implementation
- Handles connection state management
- Manages MAVLink packet routing
- Monitors heartbeat messages

### 3. ViewModel Layer (ConnectionViewModel)

The `ConnectionViewModel` handles:
- TCP mode selection (Client/Server)
- IP address and port configuration
- Connection initiation based on selected mode
- State management for UI

### 4. View Layer (ConnectionScreen)

The `ConnectionScreen` provides:
- TCP mode selector using FilterChip components
- IP address field (Client mode only)
- Port configuration field
- Dynamic UI based on selected mode

## Usage

### TCP Client Mode

1. Select **TCP/IP** tab in the Connection Screen
2. Choose **Client** mode
3. Enter the IP address of the MAVLink server (e.g., `127.0.0.1` for localhost)
4. Enter the port number (default: `5762`)
5. Click **Connect**

**Use Cases**:
- Connecting to SITL (Software In The Loop) simulators
- Connecting to networked telemetry modules
- Connecting to remote drone systems

### TCP Server Mode

1. Select **TCP/IP** tab in the Connection Screen
2. Choose **Server** mode
3. Enter the port number to listen on (default: `5762`)
4. Click **Connect** to start listening
5. Wait for the drone to connect

**Use Cases**:
- When the GCS acts as a server
- When connecting with simulators configured in client mode
- For advanced testing scenarios

## Implementation Details

### Connection Flow

1. User selects TCP mode and configures parameters
2. `ConnectionViewModel` validates inputs
3. On Connect, appropriate `ConnectionProvider` is instantiated
4. `TelemetryRepository.connect()` is called with the provider
5. Connection is established using the MAVLink library
6. Heartbeat monitoring begins
7. Connection state updates reflect in the UI

### Library Used

The implementation uses the `divpundir/mavlink` library:
- **Package**: `com.divpundir.mavlink:connection-tcp:1.2.8`
- **Dialect**: ArdupilotmegaDialect
- **Adapter**: Coroutines adapter for Android

### Key Features

- **Automatic heartbeat detection**: Waits for FCU heartbeat after connection
- **Connection state monitoring**: Real-time connection status updates
- **Timeout handling**: 10-second connection timeout with error feedback
- **Clean architecture**: Separation of concerns following MSSV pattern
- **Type safety**: Kotlin type system ensures correct usage

## Testing

### TCP Client Mode Testing

1. Start a MAVLink server (e.g., SITL simulator):
   ```bash
   sim_vehicle.py -v ArduCopter --console --map -L CMAC
   ```
2. Note the TCP port (typically 5760, 5762, or 5763)
3. In the app, select TCP Client mode
4. Enter `10.0.2.2` (Android emulator localhost) or actual IP
5. Enter the port number
6. Connect and verify heartbeat

### TCP Server Mode Testing

1. In the app, select TCP Server mode
2. Enter port `5762`
3. Click Connect (app starts listening)
4. Start a MAVLink client to connect to the app's IP and port
5. Verify connection and heartbeat detection

## Roadmap Compliance

This implementation follows the roadmap specified in the issue:

✅ **Phase 1**: USB (Serial) Connection - Already implemented
✅ **Phase 2**: TCP Connection - Now implemented with both Client and Server modes
✅ **Phase 3**: Packet Handling - Handled by TelemetryRepository using Rx-like Flow API

### Roadmap Key Points Implemented:

1. **Connection String Pattern**: Abstracted through ConnectionProvider classes
2. **Port Factory Pattern**: Replaced with Provider pattern (more Kotlin-idiomatic)
3. **MavlinkV2Connection**: Using `CoroutinesMavConnection` from the library
4. **Observable Pattern**: Using Kotlin Flow instead of Rx.NET observables
5. **Clean Architecture**: Following MSSV pattern as specified

## Future Enhancements

Possible improvements for future iterations:

- [ ] Add connection history/favorites
- [ ] Support multiple simultaneous TCP connections
- [ ] Add network discovery for MAVLink devices
- [ ] Implement connection retry logic
- [ ] Add TCP connection statistics and monitoring
- [ ] Support for MAVLink routing between USB and TCP

## References

- [MAVLink Protocol Documentation](https://mavlink.io/en/)
- [divpundir/mavlink Library](https://github.com/divyanshupundir/mavlink-kotlin)
- [ArduPilot SITL](https://ardupilot.org/dev/docs/sitl-simulator-software-in-the-loop.html)
