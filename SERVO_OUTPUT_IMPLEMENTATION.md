# Servo Output Implementation Guide

## Overview
This document describes the complete implementation of the Servo Output feature for the Pavaman Configurator GCS Android application. The feature allows real-time monitoring and control of servo/motor outputs via MAVLink protocol.

## Features Implemented

### 1. Real-time Servo Monitoring
- **SERVO_OUTPUT_RAW Message Handling**: Subscribes to MAVLink message ID 36 to receive live PWM values
- **16 Channel Support**: Monitors all 16 servo/motor channels simultaneously
- **Live PWM Display**: Visual progress bars showing current PWM values (1000-2000µs range)
- **Timestamp Tracking**: Records when each update was received

### 2. Servo Configuration
- **Function Assignment**: Dropdown menus to assign functions (Motor1-8, Servo, Gimbal, etc.)
- **Reverse Setting**: Checkbox to reverse servo direction
- **PWM Range Configuration**: Min/Trim/Max values adjustable via spinners (800-2200µs)
- **Parameter Synchronization**: Reads SERVOx_FUNCTION, SERVOx_REVERSED, SERVOx_MIN/TRIM/MAX parameters

### 3. Safety Features
- **Armed State Detection**: Monitors vehicle armed status via HEARTBEAT messages
- **Safety Warnings**: Red banner displayed when vehicle is armed
- **Command Blocking**: Prevents servo test commands while armed
- **PWM Range Validation**: Enforces safe PWM limits (800-2200µs)

### 4. User Interface
- **Table Layout**: Matches Mission Planner style with Position, Reverse, Function, Min, Trim, Max columns
- **Dark Theme**: Consistent with app's dark color scheme (#3A3A38 background)
- **Visual PWM Indicators**: Green gradient bars showing live servo output levels
- **Scrollable List**: Supports viewing all 16+ channels in a scrollable view

## Architecture

### Data Layer

#### Models (`ServoModels.kt`)
```kotlin
data class ServoChannel(
    val channelIndex: Int,      // 1-based channel number
    val pwm: Int,               // Current PWM in microseconds
    val function: ServoFunction,
    val reverse: Boolean,
    val minPwm: Int,
    val trimPwm: Int,
    val maxPwm: Int,
    val timestampUsec: Long
)

enum class ServoFunction {
    DISABLED, MOTOR1-8, SERVO, GIMBAL_PITCH, etc.
}

data class VehicleState(
    val isArmed: Boolean,
    val mode: String,
    val systemStatus: Int
)
```

#### Repository (`ServoRepository.kt`)
Responsibilities:
- Subscribe to MAVLink messages via `TelemetryRepository`
- Parse `SERVO_OUTPUT_RAW` messages and update channel states
- Parse `HEARTBEAT` messages for vehicle state
- Parse `PARAM_VALUE` messages for servo configuration
- Send `MAV_CMD_DO_SET_SERVO` commands
- Request servo parameters from vehicle

Key Methods:
- `servoChannels: StateFlow<List<ServoChannel>>` - Observable list of all channels
- `vehicleState: StateFlow<VehicleState>` - Current vehicle safety state
- `setServoPwm(servoNumber: Int, pwmUs: Int)` - Send servo test command
- `requestServoParameters()` - Request all SERVOx parameters
- `canSendServoCommands(): Boolean` - Safety check

### Presentation Layer

#### ViewModel (`ServoOutputViewModel.kt`)
Responsibilities:
- Expose repository data to UI via StateFlows
- Handle user interactions (function changes, PWM adjustments)
- Validate user inputs
- Display error/success messages

Key Properties:
- `servoChannels: StateFlow<List<ServoChannel>>`
- `vehicleState: StateFlow<VehicleState>`
- `uiMessage: StateFlow<String?>`
- `testModeEnabled: StateFlow<Boolean>`

Key Methods:
- `updateChannelFunction/Reverse/Min/Trim/Max()` - Update servo settings
- `testServo(channelIndex: Int, pwmValue: Int)` - Send test command
- `refreshParameters()` - Request fresh data from vehicle

#### UI (`ServoOutputScreen.kt`)
Composable Components:
- `ServoOutputScreen` - Main screen with header, list, and safety warnings
- `ServoChannelRow` - Individual row showing one channel's state
- `NumberSpinner` - Custom spinner with up/down buttons for PWM values

UI Features:
- Real-time data updates using `collectAsStateWithLifecycle()`
- Snackbar notifications for errors/confirmations
- Armed state warning banner
- Responsive table layout

## MAVLink Protocol Details

### Incoming Messages

#### SERVO_OUTPUT_RAW (ID 36)
Received periodically (typically 10-50Hz) containing PWM values:
```
Fields:
- time_usec: Timestamp in microseconds
- port: Output port (0 = main, 1 = aux)
- servo1_raw...servo16_raw: PWM values in microseconds (uint16)
```

#### HEARTBEAT (ID 0)
Used for vehicle state monitoring:
```
Fields:
- base_mode: Includes MAV_MODE_FLAG_SAFETY_ARMED bit
- system_status: MAV_STATE enum value
```

#### PARAM_VALUE (ID 22)
Servo configuration parameters:
```
Parameters monitored:
- SERVOx_FUNCTION (x=1..16): Function assignment (33-40 = Motors 1-8)
- SERVOx_REVERSED (x=1..16): 0=normal, 1=reversed
- SERVOx_MIN/TRIM/MAX (x=1..16): PWM range in microseconds
```

### Outgoing Messages

#### COMMAND_LONG (ID 76) - MAV_CMD_DO_SET_SERVO (183)
Send servo test commands:
```
command = 183 (MAV_CMD_DO_SET_SERVO)
param1 = servo_number (1..16)
param2 = pwm_us (800..2200)
param3-7 = 0 (unused)
```

#### PARAM_REQUEST_READ (ID 20)
Request specific parameter:
```
param_id = "SERVOx_FUNCTION" (or other param name)
param_index = -1 (use param_id instead)
```

## Integration Points

### Application Setup
1. **PavamanApplication.kt**: ServoRepository initialized as singleton
2. **ViewModelFactory.kt**: ServoOutputViewModel factory registration
3. **AppNavigation.kt**: ServoOutput route and composable added
4. **ConfigurationsScreen.kt**: Navigation button to Servo Output screen

### Dependencies
- `com.divpundir.mavlink` - MAVLink protocol library
- `androidx.lifecycle` - ViewModel and StateFlow
- `androidx.compose` - UI framework
- `kotlinx.coroutines` - Asynchronous operations

## Usage Instructions

### For Developers

1. **To add new servo functions**:
   Edit `ServoFunction` enum in `ServoModels.kt`

2. **To add parameter editing**:
   Implement `PARAM_SET` message sending in `ServoRepository.kt`

3. **To customize PWM ranges**:
   Modify `NumberSpinner` range parameter in `ServoOutputScreen.kt`

### For Users

1. **View Live Servo Outputs**:
   - Navigate: Home → Configurations → Servo Output
   - Green bars show real-time PWM values
   - Values update automatically when connected to vehicle

2. **Configure Servo Functions**:
   - Tap Function dropdown for each channel
   - Select Motor1-8 for motor outputs
   - Select Disabled for unused channels

3. **Adjust PWM Ranges**:
   - Use ▲/▼ buttons to adjust Min/Trim/Max values
   - Values change in 50µs increments
   - Range: 800-2200µs

4. **Test Servos** (Future Enhancement):
   - Only when vehicle is disarmed
   - Test mode toggle enables manual PWM control
   - Confirmation required for safety

## Safety Considerations

### Armed State Protection
- Repository monitors HEARTBEAT.base_mode for armed bit
- UI displays warning banner when armed
- `canSendServoCommands()` returns false when armed
- Test commands blocked at ViewModel level

### PWM Validation
- Minimum: 800µs (prevents under-drive)
- Maximum: 2200µs (prevents over-drive)
- Typical range: 1000-2000µs
- Validation at both ViewModel and Repository levels

### Motor Test Safety
- **Never test motors while armed**
- **Always remove propellers before motor testing**
- **Start with low PWM values (1100µs)**
- **Ensure vehicle is secured**

## Testing & Verification

### Unit Testing Checklist
- [ ] ServoRepository parses SERVO_OUTPUT_RAW correctly
- [ ] Vehicle armed state detection works
- [ ] PWM range validation catches invalid values
- [ ] Parameter parsing handles all SERVOx_* params
- [ ] Command sending formats MAV_CMD_DO_SET_SERVO correctly

### Integration Testing Checklist
- [ ] Connect to real/simulated vehicle
- [ ] Verify servo channels update in real-time
- [ ] Check armed state warning appears when armed
- [ ] Test function dropdown displays all options
- [ ] Verify PWM spinners work correctly
- [ ] Confirm navigation to/from screen works

### End-to-End Testing
1. Connect to vehicle via Bluetooth/TCP
2. Navigate to Servo Output screen
3. Observe live PWM updates on motors 1-4 (if configured)
4. Verify unused channels show 0
5. Arm vehicle → check warning appears
6. Disarm vehicle → check warning disappears

## Known Limitations

1. **Parameter Writing Not Implemented**: 
   Function/Reverse/Min/Trim/Max changes don't persist to vehicle yet.
   TODO: Implement PARAM_SET command sending.

2. **No Confirmation on Parameter Changes**:
   Need to add PARAM_VALUE acknowledgment checking.

3. **Test Mode Incomplete**:
   Manual servo testing UI is present but needs additional safety confirmations.

4. **Frame Type Mapping Not Used**:
   Motor ordering based on FRAME_CLASS/FRAME_TYPE not yet implemented.

## Future Enhancements

### Short Term
- [ ] Implement PARAM_SET for saving configuration changes
- [ ] Add parameter write confirmation (wait for PARAM_VALUE echo)
- [ ] Add "Test All Motors" sequence feature
- [ ] Add PWM value editing via text input (in addition to spinners)

### Medium Term
- [ ] Read FRAME_CLASS/FRAME_TYPE parameters
- [ ] Display motor positions visually based on frame type
- [ ] Add motor ordering for Quad/Hexa/Octa X configurations
- [ ] Add servo output graphing (historical PWM values)

### Long Term
- [ ] Support ACTUATOR_CONTROL_TARGET messages (PX4)
- [ ] Add ESC telemetry display (RPM, voltage, current)
- [ ] Add servo calibration wizard
- [ ] Support UAVCAN/DroneCAN servo configuration

## Troubleshooting

### Servo values show 0
- Check vehicle is connected and heartbeat received
- Verify SERVO_OUTPUT_RAW message rate in telemetry settings
- Check vehicle mode (some modes don't output servo data)

### Armed warning doesn't appear
- Check HEARTBEAT messages are being received
- Verify MAV_MODE_FLAG_SAFETY_ARMED bit parsing
- Check TelemetryRepository connection state

### Parameter changes don't persist
- Parameter writing not yet implemented (see Known Limitations)
- Use Mission Planner or other GCS to modify parameters currently

### Commands not sent
- Verify MAVLink connection is established
- Check `fcuSystemId` and `fcuComponentId` are set
- Enable MAVLink logging to see command packets

## References

- [MAVLink Common Messages](https://mavlink.io/en/messages/common.html)
- [ArduPilot Servo Functions](https://ardupilot.org/copter/docs/parameters.html#servo-parameters)
- [Mission Planner Motor Test](https://ardupilot.org/planner/docs/motor-test.html)
- [divpundir/mavlink-kotlin](https://github.com/divpundir/mavlink-kotlin)

## Change Log

### 2025-11-10 - Initial Implementation
- Created ServoModels data classes
- Implemented ServoRepository with SERVO_OUTPUT_RAW parsing
- Created ServoOutputViewModel
- Built ServoOutputScreen UI matching reference design
- Integrated into navigation system
- Added safety checks for armed state
- Implemented PWM range validation

