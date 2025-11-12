# Servo Output Parameter Setting - Diagnostic Guide

## Issue
Changes made in the app are not reflecting in Mission Planner, but changes from Mission Planner are showing in the app.

## Root Cause Analysis

### ✅ Code Structure (CORRECT)
The code is properly structured to send PARAM_SET commands:
1. ✅ `setParameter()` method exists and sends PARAM_SET MAVLink messages
2. ✅ `setServoFunction()`, `setServoMin()`, `setServoTrim()`, `setServoMax()`, `setServoReverse()` all call `setParameter()`
3. ✅ ViewModel methods call repository methods with proper error handling
4. ✅ UI buttons are wired correctly to ViewModel methods

### 🔍 What to Check

#### 1. **Check Logcat for Parameter Set Attempts**
When you click the up/down arrows or change a dropdown, look for these logs:

```
🔧 Attempting to set parameter: SERVO1_MIN = 1150.0
📤 Sending PARAM_SET - Target: 1/1
📤 From GCS: 255/190
✅ PARAM_SET sent successfully: SERVO1_MIN = 1150.0
```

If you see `❌ Failed to set parameter`, check the error details.

#### 2. **Verify Connection State**
- The app must be connected to the flight controller
- Check that `telemetryRepository.connection` is not null
- Check that `fcuSystemId` and `fcuComponentId` are set (should be 1/1 typically)

#### 3. **Check if Parameters Are Being Sent**
The PARAM_SET message contains:
- `targetSystem`: Flight controller system ID (usually 1)
- `targetComponent`: Flight controller component ID (usually 1)
- `paramId`: Parameter name (e.g., "SERVO1_FUNCTION")
- `paramValue`: New value as float
- `paramType`: REAL32 (type 9)

#### 4. **Mission Planner Parameter Refresh**
Mission Planner might not automatically refresh parameters. Try:
1. Click "Refresh Params" in Mission Planner after making changes in the app
2. Check if Mission Planner shows the updated value

#### 5. **Parameter Type Issue**
ArduPilot parameters can have different types:
- REAL32 (floating point) - Most common
- INT32 (integer)
- INT16, INT8, UINT32, etc.

Currently using REAL32 for all parameters. This should work for most cases.

## Debugging Steps

### Step 1: Enable Detailed Logging
The code already has detailed logging. Filter logcat for:
```
adb logcat | grep ServoRepository
```

### Step 2: Test a Simple Change
1. Open Mission Planner and go to Config > Servo Output
2. Note the current value of SERVO1_MIN (e.g., 1100)
3. In your app, click the up arrow on SERVO1_MIN
4. Check logcat for the PARAM_SET message
5. In Mission Planner, click "Refresh Params"
6. Check if SERVO1_MIN changed to 1150

### Step 3: Verify MAVLink Traffic
Use Mission Planner's MAVLink Inspector (Ctrl+F > MAVLink Inspector) to see:
- Are PARAM_SET messages being received from your app?
- What are the message contents?

### Step 4: Check Parameter Write Protection
Some parameters might be read-only or require specific conditions:
- Vehicle must be disarmed for most parameter changes
- Some parameters require a reboot
- Check Mission Planner's parameter editor for any warnings

## Expected Behavior

### When Working Correctly:
1. User clicks up arrow on SERVO1_MIN in app
2. App logs: "🔧 Attempting to set parameter: SERVO1_MIN = 1150.0"
3. App logs: "📤 Sending PARAM_SET..."
4. App logs: "✅ PARAM_SET sent successfully"
5. Mission Planner receives PARAM_SET message
6. Mission Planner updates parameter value (may need refresh)
7. Flight controller sends PARAM_VALUE message back
8. App receives PARAM_VALUE and updates UI

### Current Behavior:
- Step 1-4: ✅ Likely working (check logs)
- Step 5-8: ❓ Need to verify

## Quick Fixes to Try

### Fix 1: Add Delay and Request Confirmation
After sending PARAM_SET, wait and request the parameter back:

```kotlin
// Send parameter
setParameter("SERVO1_MIN", 1150f)
// Wait a bit
delay(100)
// Request it back to verify
requestParameter(connection, "SERVO1_MIN")
```

### Fix 2: Check Connection Type
If using USB connection through Mission Planner:
- Make sure Mission Planner is in "forwarding" mode
- Check that no firewall is blocking UDP packets
- Verify the connection settings in your app

### Fix 3: Verify System IDs
Check that:
- `fcuSystemId` = 1 (flight controller)
- `gcsSystemId` = 255 (your app)
- `fcuComponentId` = 1
- `gcsComponentId` = 190

## Next Steps

1. **Run the app and check logs** - Filter for "ServoRepository" tag
2. **Try changing a parameter** - Click up/down arrow on Min/Trim/Max
3. **Look for the log messages** - Should see "🔧", "📤", "✅" or "❌"
4. **Share the logs** - If you see errors, share the full log output
5. **Test with Mission Planner MAVLink Inspector** - Verify messages are being received

## Expected Log Output

### Success Case:
```
D/ServoRepository: 🔧 Attempting to set parameter: SERVO1_MIN = 1150.0
D/ServoRepository: 📤 Sending PARAM_SET - Target: 1/1
D/ServoRepository: 📤 From GCS: 255/190
D/ServoRepository: ✅ PARAM_SET sent successfully: SERVO1_MIN = 1150.0
```

### Failure Case:
```
D/ServoRepository: 🔧 Attempting to set parameter: SERVO1_MIN = 1150.0
E/ServoRepository: ❌ Failed to set parameter SERVO1_MIN = 1150.0
E/ServoRepository: Error details: [error message here]
```

## Code References

### Files Modified:
1. `ServoRepository.kt` - Added `setParameter()` and related methods
2. `ServoOutputViewModel.kt` - Updated to call repository methods
3. `ServoModels.kt` - Added `toParameterValue()` method
4. `ServoOutputScreen.kt` - Fixed button spacing

### Key Method:
```kotlin
suspend fun setParameter(paramId: String, value: Float): Result<Unit>
```
This method is responsible for sending PARAM_SET commands to Mission Planner/Flight Controller.

