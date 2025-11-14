# USB Connection Fix Report

## Issues Found and Fixed

### 1. **Critical: Missing connect() call in UsbSerialMavConnection**
**Problem:** The `UsbSerialMavConnection` was created but never actually opened the USB port. The `connect()` method existed but was never called by the connection provider.

**Fix:** Modified the flow so that when `tryConnect()` is called on the connection, it properly opens and initializes the USB serial port.

### 2. **Missing Error Handling**
**Problem:** USB operations had minimal error handling and logging, making it impossible to debug connection failures.

**Fix:** Added comprehensive error handling with try-catch blocks and detailed logging throughout:
- Port opening
- Parameter setting
- Buffer operations
- Read/Write operations

### 3. **No Port State Tracking**
**Problem:** The code didn't track whether the USB port was already open, potentially causing conflicts.

**Fix:** Added `isPortOpen` flag to track port state and properly close/reopen if needed.

### 4. **Missing Buffer Purging**
**Problem:** Stale data in USB buffers could interfere with MAVLink packet synchronization.

**Fix:** Added buffer purging after opening the port (with error handling for devices that don't support it).

### 5. **Poor Error Messages**
**Problem:** Generic error messages made it hard to diagnose USB connection issues.

**Fix:** Added specific error messages for:
- USB driver not found
- Permission denied
- Port open failures
- Read/Write errors

### 6. **Missing Logging**
**Problem:** No logging to track USB connection lifecycle and troubleshoot issues.

**Fix:** Added comprehensive logging at TAG "UsbSerialMavConnection" and "ConnectionViewModel" for:
- Connection attempts
- Port configuration
- Data transfer
- Errors and warnings

## Files Modified

1. **UsbSerialMavConnection.kt**
   - Added proper `connect()` implementation with error handling
   - Added port state tracking
   - Added buffer purging
   - Added comprehensive logging
   - Improved error messages in InputStream/OutputStream wrappers

2. **ConnectionViewModel.kt**
   - Enhanced `connectToUsb()` method with better error handling
   - Added detailed logging for USB device properties
   - Added validation checks for driver and connection
   - Proper error propagation to UI

3. **TelemetryRepository.kt**
   - Added `setConnectionError()` method for better error state management

## How USB Connection Works Now

```
1. User selects USB device in UI
2. ConnectionViewModel.discoverUsbDevices() finds available USB serial devices
3. User clicks Connect
4. ConnectionViewModel.connect() checks USB permissions
   - If no permission: requests permission via Android dialog
   - If has permission: proceeds to connectToUsb()
5. connectToUsb() opens USB device connection
6. Creates UsbSerialConnectionProvider with driver, connection, and baud rate
7. TelemetryRepository.connect() calls provider.createConnection()
8. UsbSerialConnectionProvider creates UsbSerialMavConnection
9. tryConnect() calls connect() on UsbSerialMavConnection
10. connect() method:
    - Opens USB serial port
    - Sets parameters (baud, data bits, stop bits, parity)
    - Purges buffers
    - Creates InputStream/OutputStream wrappers
    - Creates BufferedMavConnection for MAVLink protocol
11. MAVLink connection is now active and ready for communication
```

## Testing Checklist

### Before Testing
- [ ] USB device (flight controller or telemetry radio) connected via USB cable
- [ ] USB cable supports data transfer (not charge-only)
- [ ] Device drivers properly installed on Android device

### Test Steps
1. **Device Discovery**
   - Open app and go to Connection screen
   - Select "USB" tab
   - Click "Refresh" or "Discover Devices"
   - Verify: USB devices appear in list with correct name/VID/PID
   - **Log filter:** `ConnectionViewModel`

2. **Permission Request**
   - Select a USB device from list
   - Click "Connect"
   - Verify: Permission dialog appears (if first time)
   - Grant permission
   - **Log filter:** `ConnectionViewModel`

3. **Connection Establishment**
   - After permission granted, connection should proceed automatically
   - Verify: Connection status changes to "Connecting" → "Verifying Heartbeat"
   - **Log filters:** `UsbSerialMavConnection`, `TelemetryRepository`

4. **Heartbeat Detection**
   - Wait up to 5 seconds
   - Verify: Connection status changes to "HeartbeatVerified"
   - Verify: App navigates to Home screen
   - **Log filter:** `TelemetryRepository`

5. **Data Transfer**
   - On Home screen, verify telemetry data is updating
   - Check battery voltage, GPS, attitude indicators
   - **Log filter:** `TelemetryRepository`

### Common Issues and Solutions

#### Issue: "No USB devices found"
**Causes:**
- USB cable is charge-only (no data pins)
- USB device not recognized by Android
- Missing USB OTG support on Android device

**Solutions:**
- Try different USB cable (must support data)
- Check device compatibility
- Enable OTG in Android settings

#### Issue: "Failed to open USB device"
**Causes:**
- Permission denied
- Device already in use by another app
- USB connection lost

**Solutions:**
- Restart app and grant permission
- Close other apps using USB
- Reconnect USB cable
- Check logs for specific error

#### Issue: "No heartbeat received from drone"
**Causes:**
- Wrong baud rate selected
- Flight controller not sending MAVLink
- USB serial adapter issue
- Wrong serial port on flight controller

**Solutions:**
- Try different baud rates (115200, 57600, 921600)
- Verify flight controller MAVLink is enabled on correct serial port
- Check flight controller documentation
- Test with another GCS (Mission Planner, QGC) to verify MAVLink output

## Logging Guide

To see USB connection logs, use Android Studio Logcat with these filters:

```
# All USB-related logs
tag:UsbSerialMavConnection | tag:ConnectionViewModel | tag:TelemetryRepository

# Connection establishment only
tag:UsbSerialMavConnection | tag:ConnectionViewModel level:debug

# Errors only
level:error

# Specific USB device logs
tag:UsbSerialMavConnection
```

### Key Log Messages

**Success indicators:**
```
ConnectionViewModel: Found X USB serial devices
ConnectionViewModel: USB permission granted for [device]
ConnectionViewModel: USB device opened successfully
UsbSerialMavConnection: Opening USB serial port with baud rate: 115200
UsbSerialMavConnection: USB port opened successfully
UsbSerialMavConnection: USB port parameters set
UsbSerialMavConnection: USB MAVLink connection initialized successfully
TelemetryRepository: MAVLink connection established
TelemetryRepository: FCU detected sysId=1 compId=1
TelemetryRepository: Drone heartbeat verified
```

**Error indicators:**
```
ConnectionViewModel: USB driver not found for device X
ConnectionViewModel: Failed to open USB device - openDevice returned null
UsbSerialMavConnection: Failed to open USB connection
TelemetryRepository: Timeout waiting for drone heartbeat
```

## Configuration

### Default Settings
- **Baud Rate:** 115200 (configurable in UI)
- **Data Bits:** 8
- **Stop Bits:** 1
- **Parity:** None
- **Read Timeout:** 1000ms
- **Write Timeout:** 1000ms
- **Heartbeat Timeout:** 5000ms

### Supported Baud Rates
The app supports standard baud rates:
- 9600
- 19200
- 38400
- 57600
- 115200 (default)
- 230400
- 460800
- 921600

## Technical Details

### USB Serial Library
Using `usb-serial-for-android` v3.9.0
- Supports most FTDI, CP210x, CH340, PL2303 chips
- Automatic driver detection
- No root required

### MAVLink Protocol
- Protocol: MAVLink v2
- Dialect: ArduPilotMega
- System ID: 255 (GCS)
- Component ID: 190
- Heartbeat interval: 1 second

### Architecture
```
UI Layer (ConnectionScreen)
    ↓
ViewModel (ConnectionViewModel)
    ↓
Repository (TelemetryRepository)
    ↓
Connection Provider (UsbSerialConnectionProvider)
    ↓
MAVLink Connection (UsbSerialMavConnection)
    ↓
USB Serial Port (usb-serial-for-android)
    ↓
Hardware (Flight Controller / Telemetry Radio)
```

## Summary

All USB connection issues have been identified and fixed:
✅ Proper port opening and initialization
✅ Comprehensive error handling
✅ Detailed logging for debugging
✅ State tracking and validation
✅ Buffer management
✅ Proper resource cleanup

The USB connection should now work reliably. If issues persist, check the logs using the filters above to identify the specific problem.

