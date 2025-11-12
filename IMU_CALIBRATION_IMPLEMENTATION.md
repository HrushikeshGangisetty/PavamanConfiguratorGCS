# IMU Calibration Implementation

## Overview
IMU (Inertial Measurement Unit) Calibration has been successfully implemented following the ArduPilot/MissionPlanner protocol for accelerometer calibration.

## Files Created

### 1. IMUCalibrationState.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/ui/configurations/`

**Contents:**
- `AccelCalibrationPosition` enum: Defines the 6 required positions for calibration
  - LEVEL (1)
  - LEFT (2)
  - RIGHT (3)
  - NOSEDOWN (4)
  - NOSEUP (5)
  - BACK (6)
- `IMUCalibrationState` sealed class: Manages calibration states
  - Idle
  - Initiating
  - AwaitingUserInput
  - ProcessingPosition
  - Success
  - Failed
  - Cancelled
- `IMUCalibrationUiState` data class: UI state management

### 2. IMUCalibrationViewModel.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/ui/configurations/`

**Key Features:**
- Implements MissionPlanner protocol for IMU calibration
- Sends `MAV_CMD_PREFLIGHT_CALIBRATION` with param5=1 to start calibration
- Listens to STATUSTEXT messages from the flight controller
- Sends `MAV_CMD_ACCELCAL_VEHICLE_POS` with position values when user confirms placement
- Handles all 6 positions sequentially
- Connection state monitoring
- Automatic success/failure detection from flight controller messages

**Protocol Flow:**
1. Send start calibration command (MAV_CMD_PREFLIGHT_CALIBRATION)
2. Subscribe to STATUSTEXT messages
3. Parse position requests from FC
4. User places drone in requested position
5. Send position confirmation (MAV_CMD_ACCELCAL_VEHICLE_POS)
6. Repeat for all 6 positions
7. Handle success/failure response

### 3. IMUCalibrationScreen.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/ui/configurations/`

**UI Components:**
- Modern Material 3 design matching app theme
- Header with back navigation
- Progress indicator showing position X of 6
- Main content card with state-specific displays:
  - Idle: Connection status + 6 positions info
  - Initiating: Loading animation
  - AwaitingUserInput: Drone orientation icon + instructions
  - ProcessingPosition: Processing animation
  - Success/Failed/Cancelled: Result displays
- Status text display
- Action buttons (Start/Cancel/Done/Reset)
- Cancel confirmation dialog

## Navigation Integration

### Updated Files:

#### AppNavigation.kt
- Added `IMUCalibration` screen route
- Created composable route with ViewModel factory
- Wired up navigation callbacks

#### ConfigurationsScreen.kt
- Added "IMU Calibration" to configuration menu
- Added navigation callback parameter
- Linked menu item to navigation action

## How to Use

1. **Navigate to Configurations**
   - From Home screen, tap "Configurations"

2. **Select IMU Calibration**
   - Tap "IMU Calibration" from the configurations menu

3. **Start Calibration**
   - Ensure drone is connected (connection status shown)
   - Tap "Start Calibration"

4. **Follow Instructions**
   - The screen will display which position to place the drone in
   - Place drone in the requested orientation
   - Tap "Click when Done" when drone is stable

5. **Complete All 6 Positions**
   - Level
   - Left side
   - Right side
   - Nose down
   - Nose up
   - On back

6. **Calibration Complete**
   - Success or failure message will be displayed
   - Tap "Start New Calibration" to calibrate again

## Technical Details

### MAVLink Commands Used:
- **MAV_CMD_PREFLIGHT_CALIBRATION (241)**: Start calibration
  - param5=1 for accelerometer calibration
- **MAV_CMD_ACCELCAL_VEHICLE_POS (42429)**: Confirm position
  - param1 = position enum value (1-6)

### Message Handling:
- **STATUSTEXT**: Flight controller status messages
  - Position requests
  - Success/failure notifications
  - Error messages

### State Management:
- Uses Kotlin StateFlow for reactive UI updates
- ViewModel survives configuration changes
- Proper cleanup on ViewModel disposal

## Color Scheme
Matches existing app design:
- Background: `Color(0xFF535350)` - Dark gray
- Cards: `Color(0xFF3A3A38)` - Darker gray
- Accent cards: `Color(0xFF2A2A28)` - Darkest gray
- Success: Green
- Error: Red
- Warning: Orange
- Primary: MaterialTheme.colorScheme.primary

## Dependencies
No new dependencies required. Uses existing:
- Jetpack Compose
- Material 3
- MAVLink library
- Coroutines & Flow

## Testing Checklist
- [ ] Connect to real flight controller
- [ ] Start calibration and verify STATUSTEXT messages received
- [ ] Complete all 6 positions
- [ ] Verify calibration success
- [ ] Test cancel functionality
- [ ] Test back navigation during calibration (shows cancel dialog)
- [ ] Test reset after success/failure
- [ ] Verify connection status handling

## Notes
- The implementation follows the exact same pattern as your previous project
- Uses the STATUSTEXT message parsing approach
- Handles all edge cases (connection loss, cancellation, etc.)
- Modern, intuitive UI with clear visual feedback
- Fully integrated with existing app architecture

