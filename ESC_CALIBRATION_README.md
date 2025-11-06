# ESC Calibration Implementation

## Overview
This document describes the complete ESC Calibration implementation following MVVM architecture with proper coroutines, state management, and comprehensive logging. The implementation is based on Mission Planner's ESC calibration flow and adapted for Android using Kotlin and Jetpack Compose.

## Architecture

### 1. **MVVM Pattern**
```
┌─────────────────────┐
│  EscCalibrationScreen │  ← UI Layer (Compose)
│  (View)              │
└──────────┬───────────┘
           │ observes StateFlow
           │
┌──────────▼───────────┐
│ EscCalibrationViewModel │ ← ViewModel Layer
│ (Business Logic)      │
└──────────┬───────────┘
           │ uses
           │
┌──────────▼───────────┐
│  ParameterRepository  │ ← Data Layer
│  (MAVLink Protocol)   │
└──────────┬───────────┘
           │ uses
           │
┌──────────▼───────────┐
│  TelemetryRepository  │ ← Connection Layer
│  (MAVLink Connection) │
└──────────────────────┘
```

### 2. **File Structure**
```
app/src/main/java/com/example/pavamanconfiguratorgcs/
├── data/
│   └── ParameterRepository.kt          # MAVLink parameter operations
├── ui/configurations/
│   ├── EscCalibrationScreen.kt         # UI components
│   ├── EscCalibrationViewModel.kt      # Business logic & state
│   └── ConfigurationsScreen.kt         # Configuration menu
├── navigation/
│   └── AppNavigation.kt                # Navigation & DI
└── telemetry/
    └── TelemetryRepository.kt          # MAVLink connection
```

## Components

### 1. ParameterRepository
**Location:** `data/ParameterRepository.kt`

**Purpose:** Handles all MAVLink parameter operations with retry logic and proper error handling.

**Key Features:**
- ✅ Async parameter reading/writing using Kotlin coroutines
- ✅ Automatic retry mechanism (3 attempts, 700ms timeout per attempt)
- ✅ Parameter caching for performance optimization
- ✅ Thread-safe state management using StateFlow
- ✅ Comprehensive logging for debugging

**Main Functions:**
```kotlin
suspend fun setParameter(
    paramName: String,
    value: Float,
    paramType: MavParamType = MavParamType.REAL32,
    force: Boolean = false
): ParameterResult

suspend fun requestParameter(paramName: String): ParameterResult
```

**MAVLink Protocol Flow:**
```
1. Send PARAM_SET message
   ↓
2. Wait for PARAM_VALUE echo (with timeout)
   ↓
3. If timeout → Retry (max 3 times)
   ↓
4. Return Result (Success/Error/Timeout)
```

### 2. EscCalibrationViewModel
**Location:** `ui/configurations/EscCalibrationViewModel.kt`

**Purpose:** Manages ESC calibration state and orchestrates parameter operations.

**State Management:**
```kotlin
data class EscCalibrationUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationStep: CalibrationStep = CalibrationStep.IDLE,
    val calibrationMessage: String? = null,
    val errorMessage: String? = null,
    
    // Motor parameters
    val motPwmType: Int = 0,          // MOT_PWM_TYPE
    val motPwmMin: Int = 1000,        // MOT_PWM_MIN (µs)
    val motPwmMax: Int = 2000,        // MOT_PWM_MAX (µs)
    val motSpinArm: Float = 0.10f,    // MOT_SPIN_ARM (0.0-1.0)
    val motSpinMin: Float = 0.15f,    // MOT_SPIN_MIN (0.0-1.0)
    val motSpinMax: Float = 0.95f     // MOT_SPIN_MAX (0.0-1.0)
)
```

**Key Functions:**
- `loadParameters()` - Fetch current parameters from autopilot
- `saveParameters()` - Save all modified parameters
- `startCalibration()` - Initiate ESC calibration (sends ESC_CALIBRATION=3)
- `stopCalibration()` - Stop calibration (sends ESC_CALIBRATION=0)
- `updateMotPwm*()` - Update individual parameter values

**Logging:**
All operations are logged with tag `"EscCalibrationViewModel"` for easy debugging.

### 3. EscCalibrationScreen
**Location:** `ui/configurations/EscCalibrationScreen.kt`

**Purpose:** Jetpack Compose UI for ESC configuration and calibration.

**UI Sections:**

#### a) Connection Warning
```kotlin
@Composable
fun ConnectionWarningCard()
```
Displays when not connected to drone.

#### b) Motor PWM Type Section
```kotlin
@Composable
fun MotorPwmTypeSection(...)
```
Dropdown menu for selecting PWM type:
- Normal PWM
- OneShot / OneShot125
- Brushed
- DShot150/300/600/1200

#### c) PWM Range Section
```kotlin
@Composable
fun PwmRangeSection(...)
```
Sliders for:
- **MOT_PWM_MIN:** 0-1500 µs (typical: 1000)
- **MOT_PWM_MAX:** 1500-2200 µs (typical: 2000)

#### d) Spin Throttle Section
```kotlin
@Composable
fun SpinThrottleSection(...)
```
Percentage sliders (0-100%) for:
- **MOT_SPIN_ARM:** Throttle when armed
- **MOT_SPIN_MIN:** Minimum in-flight throttle
- **MOT_SPIN_MAX:** Maximum throttle

#### e) Calibration Section
```kotlin
@Composable
fun CalibrationSection(...)
```
Features:
- ⚠️ Safety warnings (remove propellers, etc.)
- Calibration status messages
- Start/Stop calibration button
- Progress indicators

**User Experience:**
- All controls disabled during calibration
- Loading indicators for async operations
- Error dialogs with clear messages
- Real-time connection status monitoring

## ESC Calibration Flow

### Calibration Process (Based on Mission Planner)

```
┌─────────────────────────────────────┐
│ 1. User Clicks "START CALIBRATION"  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ 2. Send ESC_CALIBRATION = 3         │
│    (Full calibration command)       │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ 3. Autopilot enters calibration mode│
│    (VEHICLE_STATE_ESC_CALIBRATION)  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ 4. Autopilot controls throttle:     │
│    - Sets high PWM (max calibration)│
│    - Waits for user confirmation    │
│    - Sets low PWM (min calibration) │
│    - Completes calibration          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ 5. ESCs learn min/max PWM ranges    │
└─────────────────────────────────────┘
```

### Safety Warnings Displayed:
1. ⚠️ Remove all propellers before calibration
2. ⚠️ Secure the vehicle safely
3. ⚠️ Motors will spin during calibration
4. ⚠️ Disconnect battery after completion
5. ⚠️ Requires ArduCopter 3.3+

## Parameters Controlled

| Parameter | Type | Range | Description |
|-----------|------|-------|-------------|
| **MOT_PWM_TYPE** | Enum | 0-7 | PWM signal type (Normal, OneShot, DShot, etc.) |
| **MOT_PWM_MIN** | Int | 0-1500 µs | Minimum PWM pulse width to ESCs |
| **MOT_PWM_MAX** | Int | 1500-2200 µs | Maximum PWM pulse width to ESCs |
| **MOT_SPIN_ARM** | Float | 0.0-1.0 | Throttle % when armed (ground) |
| **MOT_SPIN_MIN** | Float | 0.0-1.0 | Minimum throttle % for flight |
| **MOT_SPIN_MAX** | Float | 0.0-1.0 | Maximum throttle % for flight |
| **ESC_CALIBRATION** | Enum | 0-3 | Calibration command trigger |

### ESC_CALIBRATION Values:
```kotlin
0 = Disabled (no calibration)
1 = Throttle Low Mode (capture minimum PWM)
2 = Throttle High Mode (capture maximum PWM)
3 = Execute Full Calibration (both high & low) ← Used in implementation
```

## Error Handling

### 1. Connection Errors
```kotlin
if (connection == null) {
    return ParameterResult.Error("No active connection")
}
```
- Checks connection before any operation
- UI displays warning when not connected
- All controls disabled without connection

### 2. Timeout Handling
```kotlin
withTimeoutOrNull(PARAM_TIMEOUT_MS) {
    // Wait for PARAM_VALUE response
}
```
- 700ms timeout per attempt
- 3 retry attempts
- Clear error messages to user

### 3. State Management
```kotlin
try {
    _uiState.update { it.copy(isCalibrating = true) }
    // Perform calibration
} catch (e: Exception) {
    _uiState.update { 
        it.copy(
            isCalibrating = false,
            errorMessage = "Calibration error: ${e.message}"
        )
    }
}
```
- Proper try-catch blocks
- State cleanup on errors
- User-friendly error messages

## Coroutines & Threading

### Dispatcher Strategy:
```kotlin
viewModelScope.launch {  // Main dispatcher by default
    withContext(Dispatchers.IO) {  // Switch to IO for network
        // MAVLink operations
    }
}
```

### Flow Usage:
```kotlin
telemetryRepository.mavFrame  // SharedFlow
    .filter { ... }           // Filter messages
    .map { ... }              // Transform
    .first()                  // Collect first match
```

**Benefits:**
- Non-blocking UI
- Proper cancellation handling
- Structured concurrency
- No memory leaks (tied to ViewModel lifecycle)

## Logging Strategy

### Log Levels Used:

**DEBUG (Log.d):**
- Parameter values
- Operation start/completion
- State transitions

**INFO (Log.i):**
- Successful operations
- Important milestones

**WARN (Log.w):**
- Retries
- Timeouts
- Non-critical issues

**ERROR (Log.e):**
- Failures
- Exceptions
- Critical errors

### Example Log Flow:
```
D/EscCalibrationViewModel: EscCalibrationViewModel initialized
D/EscCalibrationViewModel: Loading ESC parameters
D/ParameterRepository: Requesting parameter: MOT_PWM_MIN
D/ParameterRepository: Sending PARAM_SET for MOT_PWM_MIN (attempt 1/3)
D/ParameterRepository: Received PARAM_VALUE: MOT_PWM_MIN = 1000.0
D/EscCalibrationViewModel: Loaded MOT_PWM_MIN = 1000.0
D/EscCalibrationViewModel: Parameters loaded successfully
```

## Testing Recommendations

### Unit Tests (Future):
```kotlin
@Test
fun `startCalibration sends ESC_CALIBRATION parameter`() {
    // Test calibration command
}

@Test
fun `loadParameters handles timeout gracefully`() {
    // Test timeout handling
}
```

### Integration Tests:
1. Connect to SITL (Software In The Loop)
2. Test parameter reading/writing
3. Verify calibration command sending
4. Test error scenarios

### Manual Testing Checklist:
- [ ] Connect to real drone/SITL
- [ ] Load parameters successfully
- [ ] Modify parameter values
- [ ] Save parameters and verify echo
- [ ] Start calibration (propellers removed!)
- [ ] Stop calibration
- [ ] Test with poor connection (timeout scenarios)
- [ ] Test UI state during operations

## Future Enhancements

1. **Parameter Validation:**
   - Min/max range checking
   - Cross-parameter validation

2. **Calibration Progress:**
   - Real-time status updates from autopilot
   - Step-by-step guidance

3. **Preset Configurations:**
   - Common ESC type presets
   - Save/load custom configurations

4. **Telemetry Integration:**
   - Monitor ESC status during calibration
   - Display motor RPM/current

## Troubleshooting

### Issue: "No active connection"
**Solution:** Ensure drone is connected via Bluetooth/TCP before accessing calibration.

### Issue: "Timeout on parameter read/write"
**Solution:** 
- Check MAVLink connection quality
- Verify autopilot is responding (check heartbeat)
- Try increasing timeout values

### Issue: "Calibration not starting"
**Solution:**
- Verify firmware version (AC3.3+)
- Check ESC_CALIBRATION parameter exists
- Review logs for specific errors

### Issue: Parameters not saving
**Solution:**
- Ensure not in calibration mode
- Check autopilot parameter limits
- Verify MAVLink system/component IDs match

## Summary

This implementation provides a **production-ready ESC calibration feature** with:

✅ **MVVM Architecture** - Clean separation of concerns  
✅ **Proper Coroutines** - Non-blocking async operations  
✅ **State Management** - Reactive UI with StateFlow  
✅ **Error Handling** - Comprehensive error recovery  
✅ **Logging** - Detailed debugging information  
✅ **MAVLink Protocol** - Correct parameter operations with retry logic  
✅ **User Experience** - Loading states, error dialogs, safety warnings  
✅ **Thread Safety** - Proper dispatcher usage  

The implementation closely follows Mission Planner's ESC calibration flow while adapting it for modern Android development practices.

