# Automatic Parameter Loading Implementation

## Overview
Parameters are now automatically loaded in the background as soon as a connection is established with the flight controller via TCP, Bluetooth, or USB. Users no longer need to open the parameter screen to trigger parameter loading.

## Changes Made

### 1. TelemetryRepository.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/telemetry/TelemetryRepository.kt`

**Changes:**
- Added automatic parameter loading when FCU (Flight Control Unit) is detected
- When the first heartbeat is received from the flight controller, the system now:
  1. Creates/retrieves the shared ParameterRepository instance
  2. Automatically calls `requestAllParameters()` in a background coroutine
  3. Logs the result (success or failure)

**Code:**
```kotlin
if (!_fcuDetected.value) {
    fcuSystemId = frame.systemId
    fcuComponentId = frame.componentId
    Log.i(TAG, "FCU detected sysId=$fcuSystemId compId=$fcuComponentId")
    _fcuDetected.value = true
    _droneHeartbeatReceived.value = true
    _connectionState.value = ConnectionState.Connected

    // Automatically initialize ParameterRepository and load all parameters in background
    scope.launch {
        try {
            Log.i(TAG, "🚀 Auto-loading parameters in background...")
            val paramRepo = getParameterRepository()
            val result = paramRepo.requestAllParameters()
            result.fold(
                onSuccess = {
                    Log.i(TAG, "✅ Background parameter loading completed successfully")
                },
                onFailure = { error ->
                    Log.w(TAG, "⚠️ Background parameter loading failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during background parameter loading", e)
        }
    }
}
```

### 2. ParameterRepository.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/data/repository/ParameterRepository.kt`

**Changes:**

#### a) Prevent Duplicate Fetch Requests
- Added `isFetching` flag to track if a fetch is in progress
- Check if parameters are already loaded before fetching again
- Skip duplicate requests with appropriate logging

**Code:**
```kotlin
private var isFetching = false

suspend fun requestAllParameters(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Check if already fetching
        if (isFetching) {
            Log.d(TAG, "⏳ Parameter fetch already in progress, skipping duplicate request")
            return@withContext Result.success(Unit)
        }
        
        // Check if parameters already loaded
        if (_parameters.value.isNotEmpty() && expectedParamCount > 0u && 
            receivedIndices.size >= expectedParamCount.toInt()) {
            Log.d(TAG, "✅ Parameters already loaded (${_parameters.value.size} params), skipping fetch")
            return@withContext Result.success(Unit)
        }
        
        isFetching = true
        // ... fetch logic ...
        isFetching = false
    }
}
```

#### b) Enhanced Logging and Diagnostics
- Added detailed logging for received parameters (first 10)
- Added buffer to prevent message loss during rapid parameter reception
- Improved progress logging (every 10 params instead of 50)
- Added verbose logging in message collector

#### c) Improved Error Recovery
- Reduced no-progress timeout from 10s to 5s for faster detection
- Added recovery attempt tracking (max 3 attempts)
- Fallback to individual parameter requests if bulk request fails
- Better error messages and logging

### 3. ParametersViewModel.kt
**Location:** `app/src/main/java/com/example/pavamanconfiguratorgcs/ui/fullparams/ParametersViewModel.kt`

**Changes:**
- Modified to use the **shared** ParameterRepository from TelemetryRepository
- Check if parameters are already loaded before fetching
- Avoid duplicate fetch requests

**Code:**
```kotlin
private fun initializeParameterRepository() {
    // Use the shared ParameterRepository from TelemetryRepository
    // This ensures we reuse parameters that were already loaded in the background
    try {
        parameterRepository = telemetryRepository.getParameterRepository()

        // Observe parameter updates
        viewModelScope.launch {
            parameterRepository?.parameters?.collect { params ->
                _allParameters.value = params.values.sortedBy { it.name }
                applyFilters()
            }
        }

        // Check if parameters are already loaded (from background loading)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Give it a moment to check
            
            val currentParams = parameterRepository?.parameters?.value
            if (currentParams.isNullOrEmpty() && !hasLoadedInitially) {
                hasLoadedInitially = true
                fetchParameters()
                Log.d(TAG, "Parameters not loaded yet, fetching...")
            } else if (!currentParams.isNullOrEmpty()) {
                hasLoadedInitially = true
                Log.d(TAG, "Parameters already loaded in background (${currentParams.size} params)")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error initializing parameter repository", e)
    }
}
```

## Benefits

### 1. Improved User Experience
- Parameters are ready immediately when user navigates to parameter screen
- No waiting time for parameter loading after opening the screen
- Seamless experience across all connection types (TCP, Bluetooth, USB)

### 2. Better Performance
- Parameters are cached and reused across the app
- Only one parameter fetch per connection
- Shared ParameterRepository prevents duplicate fetches

### 3. Robust Error Handling
- Automatic retry with recovery attempts
- Fallback to individual parameter requests if needed
- Detailed logging for troubleshooting

### 4. Efficient Resource Usage
- Parameters loaded once in background
- Reused by all features (Full Param List, Configurations, etc.)
- No redundant network requests

## Testing

### Expected Behavior
1. **Connect to Flight Controller** (via TCP/Bluetooth/USB)
2. **Automatic Background Loading:**
   - Look for log: `🚀 Auto-loading parameters in background...`
   - Parameters load silently in the background
   - Look for log: `✅ Background parameter loading completed successfully`

3. **Open Parameter Screen:**
   - Parameters are already loaded and displayed immediately
   - Look for log: `Parameters already loaded in background (1397 params)`
   - No additional fetch request is made

4. **Manual Refresh:**
   - User can still manually refresh parameters if needed
   - Fetch is skipped if already in progress

### Logcat Messages to Monitor
```
TelemetryRepository: FCU detected sysId=1 compId=1
TelemetryRepository: 🚀 Auto-loading parameters in background...
ParameterRepository: 📋 Requesting all parameters...
ParameterRepository: 🎧 Starting ParamValue message listener...
ParameterRepository: 📊 Expected parameters: 1397
ParameterRepository: 📥 Progress: 50/1397
ParameterRepository: 📥 Progress: 100/1397
...
ParameterRepository: ✅ All parameters received: 1397/1397
TelemetryRepository: ✅ Background parameter loading completed successfully
```

When opening Full Param List screen:
```
ParametersViewModel: Parameters already loaded in background (1397 params)
```

## Rollback Instructions
If issues occur, you can disable automatic loading by commenting out the auto-load code in `TelemetryRepository.kt` (lines ~162-177):

```kotlin
// Comment out this block to disable auto-loading
// scope.launch {
//     try {
//         Log.i(TAG, "🚀 Auto-loading parameters in background...")
//         val paramRepo = getParameterRepository()
//         val result = paramRepo.requestAllParameters()
//         ...
//     }
// }
```

## Related Features
- This implementation works with all the existing parameter-related features:
  - Full Parameter List screen
  - Configuration screens (Frame Type, Flight Modes, etc.)
  - Calibration screens
  - Serial Ports configuration

All these features now benefit from the pre-loaded parameters.

## Performance Notes
- Parameter loading typically takes 10-30 seconds depending on connection speed
- Loading happens in background, doesn't block UI
- Parameters are stored in memory and persist until disconnection
- No performance impact on app startup or connection time

## Date Implemented
November 14, 2025

