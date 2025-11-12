# Serial Ports Feature - Quick Start Guide

## ✅ Implementation Complete!

The Serial Ports configuration feature has been successfully implemented and integrated into your Pavaman Configurator GCS app.

## 📁 Files Created

1. **SerialPortModels.kt** - Data models and option lists
2. **SerialPortRepository.kt** - MAVLink parameter operations
3. **SerialPortsViewModel.kt** - UI state management
4. **SerialPortsScreen.kt** - Compose UI components
5. **Updated files**: `AppNavigation.kt`, `ConfigurationsScreen.kt`, `PavamanApplication.kt`, `TelemetryRepository.kt`

## 🚀 How to Use

### For Users:

1. **Connect to your vehicle**
   - Connect via TCP, UDP, or Bluetooth
   - Wait for parameters to load

2. **Navigate to Serial Ports**
   - Home → Configurations → Serial Ports

3. **View and configure ports**
   - Each discovered serial port is displayed as a card
   - Change baud rate from dropdown (e.g., 115200, 57600)
   - Change protocol from dropdown (e.g., MAVLink2, GPS, CRSF)
   - Click "Set Options Bitmask" to configure advanced options

4. **Apply changes**
   - Changes are sent immediately via MAVLink
   - A reboot dialog will appear
   - **Power-cycle the vehicle** to apply changes

### For Developers:

## 🔧 Testing Steps

1. **Build and run the app**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Connect to SITL** (for safe testing)
   ```bash
   # Start ArduCopter SITL
   sim_vehicle.py -v ArduCopter --console --map
   
   # In your app, connect to: tcp:10.0.2.2:5762 (from Android emulator)
   # Or use actual IP if testing on device
   ```

3. **Test the feature**
   - Navigate: Home → Configurations → Serial Ports
   - Verify ports are discovered (SERIAL0, SERIAL1, etc.)
   - Change a baud rate, verify PARAM_SET in logs
   - Change a protocol, verify PARAM_SET in logs
   - Open bitmask editor, toggle flags, save
   - Restart SITL and verify changes persisted

## 📊 Expected Behavior

### On Load:
- Screen discovers SERIAL* parameters
- Groups them by port number (1, 2, 3, etc.)
- Displays each port with current baud/protocol

### After Changing Value:
- Sends MAVLink PARAM_SET immediately
- Shows "Reboot Required" dialog
- Updates local display after confirmation
- Logs success/failure

### Common Serial Ports (ArduPilot):
- **SERIAL0**: USB connection (usually 115200 baud, MAVLink2)
- **SERIAL1**: TELEM1 port (telemetry radio)
- **SERIAL2**: TELEM2 port (secondary telemetry)
- **SERIAL3**: GPS port (usually GPS protocol)
- **SERIAL4**: GPS2 port (secondary GPS)
- **SERIAL5**: Additional port (varies by board)

## 🐛 Troubleshooting

### "No serial ports found"
**Solution**: 
- Ensure you're connected to the vehicle
- Navigate to Full Parameters screen first to load parameters
- Click "Refresh" button on Serial Ports screen

### Changes not saving
**Solution**:
- Check MAVLink connection is stable
- Look for error messages in logcat
- Verify parameter write isn't locked on vehicle

### Changes not taking effect
**Solution**:
- **Must reboot vehicle** after changing serial parameters
- Power-cycle (unplug/replug battery), don't just restart autopilot
- Reconnect after reboot and verify

## ⚠️ Safety Warnings

1. **DON'T change SERIAL0 protocol** if connected via USB (you'll lose connection)
2. **DON'T change the port you're using for telemetry** while connected
3. **Test on bench first** before field use
4. **Backup parameters** before making changes
5. **Know your hardware** - wrong settings can make ports unusable

## 🎯 Feature Highlights

✅ **Dynamic discovery** - Automatically finds available serial ports  
✅ **User-friendly dropdowns** - Baud rates and protocols shown by name  
✅ **Bitmask editor** - Checkbox interface for advanced options  
✅ **Real-time updates** - Changes sent immediately via MAVLink  
✅ **Safety warnings** - Reboot requirement clearly displayed  
✅ **Error handling** - Graceful failure with user feedback  

## 📝 Code Architecture

```
SerialPortsScreen (UI)
    ↓ user action
SerialPortsViewModel (State Management)
    ↓ calls
SerialPortRepository (Business Logic)
    ↓ uses
ParameterRepository (MAVLink Communication)
    ↓ sends
MAVLink PARAM_SET → Flight Controller
```

## 🔍 Logging

The feature logs extensively for debugging:
- `SerialPortRepository`: Port discovery and parameter changes
- `SerialPortsViewModel`: User actions and state changes
- `ParameterRepository`: MAVLink PARAM_SET/PARAM_VALUE messages

**View logs:**
```bash
adb logcat -s SerialPortRepository SerialPortsViewModel ParameterRepository
```

## 📚 Related Documentation

- See `SERIAL_PORTS_IMPLEMENTATION.md` for detailed technical documentation
- ArduPilot serial parameters: https://ardupilot.org/copter/docs/parameters.html#serial-parameters
- MAVLink parameter protocol: https://mavlink.io/en/services/parameter.html

## 🎉 Next Steps

1. **Test thoroughly** with SITL and hardware
2. **Verify on different boards** (Pixhawk, CubeOrange, etc.)
3. **Test with different firmware** (Copter, Plane, Rover)
4. **Gather user feedback** and iterate
5. **Consider enhancements** (see Future Enhancements in implementation doc)

---

**Implementation Status**: ✅ Complete and Ready for Testing!

All files are created, integrated, and compilation errors are resolved. The feature is ready to build and test.

