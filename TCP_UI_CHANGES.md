# TCP Connection UI Changes

## Overview

This document describes the UI changes made to support both TCP Client and TCP Server modes.

## Changes to Connection Screen

### Before (TCP Client Only)

The original TCP/IP tab had two fields:
- **IP Address**: Text field for entering the remote server IP
- **Port**: Text field for entering the port number

This only supported TCP Client mode where the GCS connects to a remote MAVLink server.

### After (TCP Client + Server Modes)

The enhanced TCP/IP tab now includes:

1. **TCP Mode Selector**
   - Two FilterChip buttons: "Client" and "Server"
   - User can toggle between modes
   - Default: Client mode

2. **IP Address Field (Client Mode Only)**
   - Only visible when Client mode is selected
   - Label: "IP Address"
   - Default value: "10.0.2.2" (Android emulator localhost)

3. **Server Mode Description**
   - Visible when Server mode is selected
   - Shows text: "Server mode: Waiting for drone to connect..."
   - Replaces the IP address field

4. **Port Field (Both Modes)**
   - Always visible
   - Label changes based on mode:
     - Client mode: "Port"
     - Server mode: "Listen Port"
   - Default value: "5762"

### UI Behavior

#### Client Mode
```
┌─────────────────────────────────┐
│   [Client ✓]  [Server]          │ ← Mode selector
├─────────────────────────────────┤
│ IP Address: [10.0.2.2      ]    │ ← IP field shown
│ Port:       [5762          ]    │ ← Port field
└─────────────────────────────────┘
```

#### Server Mode
```
┌─────────────────────────────────┐
│   [Client]  [Server ✓]          │ ← Mode selector
├─────────────────────────────────┤
│ Server mode: Waiting for        │ ← Descriptive text
│ drone to connect...             │
│ Listen Port: [5762         ]    │ ← Port field
└─────────────────────────────────┘
```

## Connection Button Validation

### Client Mode
Connect button is enabled when:
- IP address is not blank
- Port is not blank
- Not currently connecting

### Server Mode
Connect button is enabled when:
- Port is not blank
- Not currently connecting
- (IP address not required)

## User Experience Flow

### Connecting in Client Mode
1. User opens app and navigates to Connection Screen
2. TCP/IP tab is pre-selected
3. Client mode is pre-selected (default)
4. User enters IP address of remote MAVLink server
5. User enters port number (or uses default)
6. User taps Connect button
7. App attempts to connect to remote server
8. On success, navigates to home screen
9. On failure, shows error dialog

### Connecting in Server Mode
1. User opens app and navigates to Connection Screen
2. User selects TCP/IP tab
3. User taps "Server" FilterChip to switch modes
4. IP address field disappears, replaced with description
5. User enters port number to listen on (or uses default)
6. User taps Connect button
7. App starts TCP server and listens for connections
8. When drone connects and sends heartbeat, navigates to home screen
9. On failure or timeout, shows error dialog

## Advantages of This Implementation

1. **Flexibility**: Supports both common TCP usage patterns
2. **Intuitive**: Clear mode selection with visual feedback
3. **Guided**: Only shows relevant fields for selected mode
4. **Safe**: Proper validation prevents invalid configurations
5. **Educational**: Descriptive text helps users understand server mode

## Technical Implementation Details

### UI Components Used

- **FilterChip**: For mode selection (Material 3 component)
  - Toggleable selection state
  - Visual indication of selected mode
  - Equal width using `Modifier.weight(1f)`

- **OutlinedTextField**: For input fields
  - Consistent styling with existing UI
  - White text and labels on dark background
  - Proper color states for focused/unfocused

- **Text**: For descriptive content in server mode
  - Gray color for secondary text
  - Body medium typography

### State Management

- `tcpMode` StateFlow in ViewModel tracks current mode
- UI recomposes automatically when mode changes
- Validation logic adapts based on mode

### Connection Logic

```kotlin
when (_tcpMode.value) {
    TcpMode.CLIENT -> {
        val host = _ipAddress.value
        TcpConnectionProvider(host, portNum)
    }
    TcpMode.SERVER -> {
        TcpServerConnectionProvider(portNum)
    }
}
```

## Accessibility Considerations

- Clear labels for all interactive elements
- Mode selection uses standard Material 3 patterns
- Error messages are displayed in dialogs for visibility
- Screen readers can navigate all elements properly

## Testing Recommendations

### Manual Testing
1. Switch between Client and Server modes - verify UI updates
2. Test Client mode with valid/invalid IPs
3. Test Server mode without IP field
4. Test connection button enable/disable logic
5. Verify error dialogs appear on connection failure
6. Test with real SITL simulator in both modes

### Automated Testing
- Unit tests for ViewModel mode switching logic
- Unit tests for validation logic
- UI tests for mode selection interaction
- UI tests for field visibility based on mode

## Future Enhancements

Possible UI improvements:
- Add tooltip/help text explaining when to use each mode
- Add presets for common configurations
- Add connection history dropdown
- Add advanced options (timeout, retry settings)
- Add network scanning to discover MAVLink devices
