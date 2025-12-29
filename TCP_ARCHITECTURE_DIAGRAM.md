# TCP Connection Architecture Diagram

## Component Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERFACE                               │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │           ConnectionScreen (View Layer)                     │   │
│  │                                                              │   │
│  │  ┌──────────────────────────────────────────────────────┐  │   │
│  │  │  TCP/IP Tab                                          │  │   │
│  │  │  ┌────────────────────────────────────────────────┐  │  │   │
│  │  │  │ Mode Selector: [Client ✓] [Server]           │  │  │   │
│  │  │  └────────────────────────────────────────────────┘  │  │   │
│  │  │  ┌────────────────────────────────────────────────┐  │  │   │
│  │  │  │ IP Address: [10.0.2.2              ]          │  │  │   │
│  │  │  └────────────────────────────────────────────────┘  │  │   │
│  │  │  ┌────────────────────────────────────────────────┐  │  │   │
│  │  │  │ Port: [5762                           ]       │  │  │   │
│  │  │  └────────────────────────────────────────────────┘  │  │   │
│  │  └──────────────────────────────────────────────────────┘  │   │
│  │                                                              │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ User Actions
                               │ (Select mode, Enter details, Connect)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       VIEWMODEL LAYER                                │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │           ConnectionViewModel                               │   │
│  │                                                              │   │
│  │  State:                                                      │   │
│  │  • tcpMode: StateFlow<TcpMode> = CLIENT                    │   │
│  │  • ipAddress: StateFlow<String> = "10.0.2.2"              │   │
│  │  • port: StateFlow<String> = "5762"                        │   │
│  │                                                              │   │
│  │  Functions:                                                  │   │
│  │  • onTcpModeChange(mode: TcpMode)                          │   │
│  │  • onIpAddressChange(ip: String)                           │   │
│  │  • onPortChange(port: String)                              │   │
│  │  • connect()                                                │   │
│  │                                                              │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ Connection Request
                               │ (Based on mode)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       SERVICE LAYER                                  │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │               MavConnectionProvider (Interface)              │  │
│  │               + createConnection(): CoroutinesMavConnection  │  │
│  └───────────────────┬──────────────────────┬──────────────────┘  │
│                      │                      │                       │
│         ┌────────────▼──────────┐  ┌───────▼──────────────┐       │
│         │ TcpConnectionProvider │  │ TcpServerConnection  │       │
│         │      (CLIENT)         │  │    Provider          │       │
│         │                       │  │    (SERVER)          │       │
│         │  host: String         │  │  port: Int           │       │
│         │  port: Int            │  │                      │       │
│         │                       │  │                      │       │
│         │  createConnection()   │  │  createConnection()  │       │
│         │  ↓                    │  │  ↓                   │       │
│         │  TcpClientMav         │  │  TcpServerMav        │       │
│         │  Connection           │  │  Connection          │       │
│         └───────────────────────┘  └──────────────────────┘       │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │                       TcpMode Enum                           │  │
│  │                  { CLIENT, SERVER }                          │  │
│  └─────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ Connection Instance
                               │ (CoroutinesMavConnection)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         MODEL LAYER                                  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │              TelemetryRepository                            │   │
│  │                                                              │   │
│  │  • connection: CoroutinesMavConnection?                     │   │
│  │  • connectionState: StateFlow<ConnectionState>              │   │
│  │  • mavFrame: Flow<MavFrame>                                 │   │
│  │                                                              │   │
│  │  Functions:                                                  │   │
│  │  • connect(provider: MavConnectionProvider)                 │   │
│  │  • disconnect()                                             │   │
│  │  • sendCommand(...)                                         │   │
│  │                                                              │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ MAVLink Communication
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      NETWORK LAYER                                   │
│                                                                      │
│         CLIENT MODE                   SERVER MODE                   │
│  ┌─────────────────────┐        ┌────────────────────────┐         │
│  │   GCS (This App)    │        │    GCS (This App)      │         │
│  │   TCP Client        │        │    TCP Server          │         │
│  │   ┌───────────┐     │        │    ┌───────────┐       │         │
│  │   │ Connect   │─────┼────────┼───►│  Listen   │       │         │
│  │   │ to Server │     │        │    │  on Port  │       │         │
│  │   └───────────┘     │        │    └─────┬─────┘       │         │
│  └──────────┬──────────┘        └──────────┼─────────────┘         │
│             │                               │                        │
│             │ MAVLink                       │ MAVLink                │
│             │ Protocol                      │ Protocol               │
│             ▼                               ▼                        │
│  ┌─────────────────────┐        ┌────────────────────────┐         │
│  │   SITL/Drone        │        │     SITL/Drone         │         │
│  │   TCP Server        │        │     TCP Client         │         │
│  │   (e.g., 127.0.0.1  │        │     (Connects to       │         │
│  │    port 5760)       │        │      GCS IP:Port)      │         │
│  └─────────────────────┘        └────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
```

## Connection Flow Sequence

### Client Mode Flow
```
User                ConnectionScreen      ConnectionViewModel    TcpConnectionProvider    TelemetryRepository
  │                       │                       │                       │                        │
  ├─ Select Client ──────►│                       │                       │                        │
  │                       ├─ onTcpModeChange() ──►│                       │                        │
  │                       │                       ├─ Update tcpMode       │                        │
  │                       │                       │                       │                        │
  ├─ Enter IP ───────────►│                       │                       │                        │
  │                       ├─ onIpAddressChange()─►│                       │                        │
  │                       │                       │                       │                        │
  ├─ Enter Port ─────────►│                       │                       │                        │
  │                       ├─ onPortChange() ─────►│                       │                        │
  │                       │                       │                       │                        │
  ├─ Click Connect ──────►│                       │                       │                        │
  │                       ├─ connect() ──────────►│                       │                        │
  │                       │                       ├─ Create Provider ────►│                        │
  │                       │                       │                       ├─ TcpClientMavConnection│
  │                       │                       │                       │   (host, port)         │
  │                       │                       ├─ connect(provider)───┼───────────────────────►│
  │                       │                       │                       │                        ├─ tryConnect()
  │                       │                       │                       │                        ├─ Wait for heartbeat
  │                       │                       │                       │                        │
  │◄──── Navigate ────────┼───────────────────────┼───────────────────────┼────────────────────────┤
  │      to Home          │                       │                       │                        │
```

### Server Mode Flow
```
User                ConnectionScreen      ConnectionViewModel    TcpServerConnection    TelemetryRepository
  │                       │                       │                Provider                   │
  ├─ Select Server ──────►│                       │                       │                    │
  │                       ├─ onTcpModeChange() ──►│                       │                    │
  │                       │                       ├─ Update tcpMode       │                    │
  │                       │                       │   (IP field hidden)   │                    │
  │                       │                       │                       │                    │
  ├─ Enter Port ─────────►│                       │                       │                    │
  │                       ├─ onPortChange() ─────►│                       │                    │
  │                       │                       │                       │                    │
  ├─ Click Connect ──────►│                       │                       │                    │
  │                       ├─ connect() ──────────►│                       │                    │
  │                       │                       ├─ Create Provider ────►│                    │
  │                       │                       │                       ├─ TcpServerMavConn │
  │                       │                       │                       │   (port)           │
  │                       │                       ├─ connect(provider)───┼────────────────────►│
  │                       │                       │                       │                    ├─ Start listening
  │                       │                       │                       │                    ├─ Wait for client
  │                       │                       │                       │                    ├─ Accept connection
  │                       │                       │                       │                    ├─ Wait for heartbeat
  │◄──── Navigate ────────┼───────────────────────┼───────────────────────┼────────────────────┤
  │      to Home          │                       │                       │                    │
```

## Key Design Decisions

1. **TcpMode Enum Location**: Placed in `telemetry.connections` package (service layer)
   - ✅ Proper layer separation
   - ✅ Reusable across ViewModel and View layers
   - ✅ Domain concept, not UI concept

2. **ConnectionProvider Pattern**: Interface-based abstraction
   - ✅ Allows easy addition of new connection types
   - ✅ Decouples ViewModel from concrete implementations
   - ✅ Follows Dependency Inversion Principle

3. **State Management**: Kotlin StateFlow
   - ✅ Reactive UI updates
   - ✅ Lifecycle-aware
   - ✅ Type-safe

4. **UI Adaptability**: Dynamic field visibility
   - ✅ Only show IP field in Client mode
   - ✅ Clear validation rules
   - ✅ Guided user experience

## Comparison: Before vs After

### Before (TCP Client Only)
```
ConnectionViewModel
  ├─ ipAddress: StateFlow<String>
  ├─ port: StateFlow<String>
  └─ connect()
       └─ TcpConnectionProvider(ip, port)

UI: Always shows IP + Port fields
```

### After (TCP Client + Server)
```
ConnectionViewModel
  ├─ tcpMode: StateFlow<TcpMode>
  ├─ ipAddress: StateFlow<String>
  ├─ port: StateFlow<String>
  ├─ onTcpModeChange(mode)
  └─ connect()
       ├─ CLIENT → TcpConnectionProvider(ip, port)
       └─ SERVER → TcpServerConnectionProvider(port)

UI: Dynamically shows fields based on mode
    CLIENT: IP + Port
    SERVER: Port only
```

## Benefits of This Design

1. **Flexibility**: Supports both common TCP patterns
2. **Extensibility**: Easy to add more connection types
3. **Maintainability**: Clear separation of concerns
4. **Testability**: Each component can be tested independently
5. **User-Friendly**: Intuitive interface with smart defaults
6. **Type-Safe**: Compile-time checking of connection types
7. **Scalable**: Architecture supports future enhancements

## Future Enhancements Path

```
Current Implementation
       │
       ├── Connection History
       │   └── Save recent connections
       │
       ├── Multi-Connection Support
       │   └── Connect to multiple drones
       │
       ├── Network Discovery
       │   └── Auto-detect MAVLink devices
       │
       ├── Connection Bridge
       │   └── Route USB ↔ TCP traffic
       │
       └── Advanced Settings
           ├── Timeout configuration
           ├── Retry logic
           └── Connection profiles
```
