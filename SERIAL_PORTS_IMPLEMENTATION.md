# Serial Ports Configuration - Implementation Guide

## Overview

This document describes the complete implementation of the **Serial Ports Configuration** feature for the Pavaman Configurator GCS Android app. This feature replicates Mission Planner's "Serial Ports" UI and functionality, allowing users to configure serial port parameters (baud rate, protocol, and options) on ArduPilot flight controllers via MAVLink.

## Architecture

### Component Structure

```
app/src/main/java/com/example/pavamanconfiguratorgcs/
├── data/
│   ├── models/
│   │   └── SerialPortModels.kt          # Data models for serial ports
│   └── repository/
│       └── SerialPortRepository.kt      # Repository for serial port operations
├── ui/
│   └── configurations/
│       ├── SerialPortsScreen.kt         # UI components
│       └── SerialPortsViewModel.kt      # ViewModel for state management
└── navigation/
    └── AppNavigation.kt                  # Navigation routing (updated)
```

### Key Components

#### 1. **SerialPortModels.kt**
Defines data models:
- `SerialPortConfig`: Represents a single serial port with its parameters
- `DropdownOption`: Options for dropdowns (baud rates, protocols)
- `BitFlag`: Individual bits in options bitmask
- Predefined lists: `baudOptions`, `protocolOptions`, `commonSerialOptionFlags`

#### 2. **SerialPortRepository.kt**
Handles MAVLink parameter operations:
- Discovers SERIAL* parameters from the flight controller
- Groups parameters by port number (SERIAL0, SERIAL1, etc.)
- Sends PARAM_SET messages to change values
- Manages loading/error states

#### 3. **SerialPortsViewModel.kt**
Manages UI state and user actions:
- Exposes StateFlows for UI observation
- Handles user interactions (change baud, protocol, options)
- Shows dialogs (bitmask editor, reboot confirmation)
- Provides feedback messages

#### 4. **SerialPortsScreen.kt**
Compose UI implementation:
- Main screen with list of discovered ports
- Per-port cards with dropdowns for baud/protocol
- Bitmask editor dialog with checkboxes
- Reboot confirmation dialog

## How It Works

### 1. Parameter Discovery

When the screen loads, the system:
1. Requests all parameters from the flight controller (if not already loaded)
2. Filters parameters starting with "SERIAL"
3. Groups by port number using regex: `SERIAL(\d+)_`
4. Creates `SerialPortConfig` objects for each port

**Example parameters discovered:**
```
SERIAL0_BAUD = 115
SERIAL0_PROTOCOL = 2
SERIAL0_OPTIONS = 0
SERIAL1_BAUD = 57
SERIAL1_PROTOCOL = 1
SERIAL1_OPTIONS = 0
```

### 2. UI Display

Each serial port is displayed as a card showing:
- **Port name**: "Serial Port 1 (TX=TELEM1, RX=TELEM1)"
- **Baud Rate dropdown**: Shows current value, allows selection
- **Protocol dropdown**: Shows current protocol type
- **Options button**: Opens bitmask editor

### 3. Changing Parameters

When user changes a value:
1. ViewModel calls repository method (e.g., `changeBaudRate()`)
2. Repository sends MAVLink `PARAM_SET` message
3. Waits for `PARAM_VALUE` confirmation
4. Updates local state on success
5. Shows reboot reminder dialog

### 4. Bitmask Editing

Options parameters use bitmasks. The editor:
1. Shows checkboxes for each bit flag
2. User selects desired options
3. Calculates integer bitmask: `sum(1 << bit for checked bits)`
4. Sends the computed value via PARAM_SET

**Example flags:**
- Bit 0: InvertRX
- Bit 1: InvertTX
- Bit 2: HalfDuplex
- Bit 3: SwapTXRX

### 5. Applying Changes

Serial port changes require a reboot to take effect:
1. After successful parameter write, show reboot dialog
2. User can acknowledge or dismiss
3. For safety, we recommend manual power-cycle (not automatic reboot)

## Baud Rate Mapping

ArduPilot uses scaled values for baud rates:

| Value | Actual Baud Rate |
|-------|------------------|
| 1     | 1200             |
| 2     | 2400             |
| 9     | 9600             |
| 19    | 19200            |
| 38    | 38400            |
| 57    | 57600            |
| 115   | 115200           |
| 230   | 230400           |
| 460   | 460800           |
| 921   | 921600           |

## Protocol Types

Common ArduPilot serial protocols:

| Value | Protocol                          |
|-------|-----------------------------------|
| -1    | None (disabled)                   |
| 0     | MAVLink1                          |
| 1     | MAVLink2                          |
| 2     | Frsky D                           |
| 3     | Frsky SPort                       |
| 4     | GPS                               |
| 10    | FrSky SPort Passthrough (OpenTX)  |
| 23    | RCIN                              |
| 29    | CRSF                              |
| 32    | MSP                               |

## Usage Flow

### Step-by-Step User Flow

1. **Connect to vehicle** → Parameters are loaded
2. **Navigate**: Home → Configurations → Serial Ports
3. **View** discovered serial ports
4. **Select** a port and change baud rate or protocol from dropdown
5. **Edit options** via "Set Options Bitmask" button (if available)
6. **Confirm** the reboot dialog
7. **Power-cycle** the vehicle to apply changes
8. **Reconnect** and verify changes took effect

## Safety Considerations

⚠️ **Important Warnings:**

1. **Don't change the port used for GCS connection** while connected (e.g., changing SERIAL0 protocol from MAVLink to GPS will disconnect you)
2. **Changes require reboot** to take effect
3. **Backup parameters** before making changes
4. **Test on bench** before field use
5. **Verify settings** after reboot

## Testing Checklist

- [ ] Connect to SITL or hardware
- [ ] Navigate to Serial Ports screen
- [ ] Verify ports are discovered and displayed
- [ ] Change baud rate, verify PARAM_SET sent
- [ ] Change protocol, verify PARAM_SET sent
- [ ] Open bitmask editor, set flags, save
- [ ] Reboot vehicle (manually)
- [ ] Reconnect and verify parameters persisted
- [ ] Test with different vehicle types (Copter, Plane, Rover)

## Integration Points

### Dependencies

The Serial Ports feature depends on:
- `ParameterRepository`: For reading/writing parameters
- `TelemetryRepository`: For MAVLink connection
- `PavamanApplication`: For singleton repositories

### Navigation

Added to `AppNavigation.kt`:
```kotlin
Screen.SerialPorts -> "serial_ports"
```

Added to `ConfigurationsScreen.kt`:
```kotlin
ConfigurationItem("Serial Ports", "serial_ports")
```

## Future Enhancements

Potential improvements:
1. **Auto-detect board type** to show correct TX/RX pin labels
2. **Parameter metadata integration** for protocol enums from server
3. **Validation** before allowing dangerous changes
4. **Automatic reboot command** (via MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN) with user consent
5. **Connection warning** if changing the current connection's port
6. **Parameter comparison** to show which values differ from defaults

## Troubleshooting

### No ports discovered
- Ensure parameters are loaded first (navigate to Full Parameters screen)
- Check MAVLink connection is active
- Verify vehicle supports SERIAL* parameters (ArduPilot)

### Parameter changes not saving
- Check MAVLink connection stability
- Verify no parameter write protection is active
- Check logs for PARAM_VALUE confirmation messages

### Changes not taking effect
- Must reboot vehicle after changing serial parameters
- Power-cycle (don't just restart autopilot software)
- Some parameters may have hardware dependencies

## Code Examples

### Adding a new serial option flag

```kotlin
// In SerialPortModels.kt
val commonSerialOptionFlags = listOf(
    // ...existing flags...
    BitFlag(10, "NewOption", "Description of new option")
)
```

### Customizing TX/RX labels per board

```kotlin
// In SerialPortRepository.kt
private fun getTxPinDescription(portNumber: Int): String {
    // Get board type from parameters or connection
    val boardType = detectBoardType()
    
    return when (boardType) {
        "Pixhawk" -> when (portNumber) {
            1 -> "TELEM1"
            2 -> "TELEM2"
            // ...
        }
        "CubeOrange" -> when (portNumber) {
            // ...
        }
        else -> "Port $portNumber"
    }
}
```

## References

- [ArduPilot Serial Parameters Documentation](https://ardupilot.org/copter/docs/parameters.html#serial-parameters)
- [MAVLink Parameter Protocol](https://mavlink.io/en/services/parameter.html)
- [Mission Planner Serial Ports UI](https://ardupilot.org/planner/docs/common-connect-mission-planner-autopilot.html)

---

## Summary

This implementation provides a complete, production-ready Serial Ports configuration feature that:
- ✅ Discovers serial ports dynamically from connected vehicle
- ✅ Displays user-friendly dropdowns for baud/protocol selection
- ✅ Provides bitmask editor for options
- ✅ Sends MAVLink PARAM_SET commands
- ✅ Handles confirmations and errors gracefully
- ✅ Warns about reboot requirement
- ✅ Follows existing app architecture patterns

The feature is fully integrated into the navigation and ready for testing with ArduPilot vehicles (SITL or hardware).

