# Parameter Refresh and Motor Test Fixes

## Issues Fixed

### Issue 1: Parameters Not Refreshing in Full Parameter Tab
**Problem**: When clicking "Refresh Parameters", the app showed "Parameters already loaded, skipping fetch" and displayed loading indefinitely.

**Root Cause**: The `requestAllParameters()` method had caching logic that prevented fetching when parameters were already loaded, even when the user explicitly requested a refresh.

**Solution**:
1. Added `forceRefresh: Boolean = false` parameter to `requestAllParameters()`
2. When `forceRefresh=true`, the method bypasses the cache check and fetches fresh data
3. Updated `ParametersViewModel.fetchParameters()` to call `requestAllParameters(forceRefresh = true)`

**Changes Made**:
- `ParameterRepository.kt`: Added `forceRefresh` parameter and logic
- `ParametersViewModel.kt`: Updated `fetchParameters()` to pass `forceRefresh=true`

---

### Issue 2: Motor Test Always Showing 4 Motors
**Problem**: Motor Test screen showed "Could not load frame class/type" and defaulted to 4 motors, even though parameters were loaded in cache.

**Root Cause**: The `requestParameter()` method always sent a MAVLink request to the flight controller, which timed out when parameters were already loaded and cached.

**Solution**:
1. Added `getCachedParameter(paramName: String): Parameter?` method to retrieve parameters from cache
2. Updated `requestParameter()` to check cache first before sending MAVLink request
3. If parameter is found in cache, return immediately (no network call needed)
4. If not in cache, then send MAVLink request as before

**Changes Made**:
- `ParameterRepository.kt`: 
  - Added `getCachedParameter()` method
  - Updated `requestParameter()` to check cache first

---

## How It Works Now

### Parameter Refresh Flow:
1. User clicks "Refresh Parameters" button
2. `ParametersViewModel.fetchParameters()` is called
3. Clears pending edits and displayed parameters
4. Calls `parameterRepository.requestAllParameters(forceRefresh = true)`
5. ParameterRepository bypasses cache check and sends fresh `PARAM_REQUEST_LIST` to flight controller
6. Parameters are fetched and displayed

### Motor Test Flow:
1. Motor Test screen opens and `loadFrameInfo()` is called
2. Calls `parameterRepository.requestParameter("FRAME_CLASS")`
3. **NEW**: ParameterRepository checks cache first
4. If found in cache (which it is if parameters were loaded), returns immediately
5. No timeout, no MAVLink request needed
6. Frame class/type are loaded correctly
7. Motor count is detected based on frame class

---

## Benefits

1. **Faster Motor Test Loading**: No more timeouts when parameters are cached
2. **Reliable Parameter Refresh**: User can always force a fresh fetch when needed
3. **Better Performance**: Reduces unnecessary MAVLink traffic by using cache
4. **Backward Compatible**: Default behavior unchanged (cache still used), only explicit refresh bypasses cache

---

## Testing

After building and running:

1. **Test Parameter Refresh**:
   - Connect to flight controller
   - Wait for parameters to load
   - Go to Full Parameter List tab
   - Click "Refresh Parameters" button
   - Verify parameters reload from flight controller

2. **Test Motor Test**:
   - Connect to flight controller
   - Wait for parameters to load
   - Go to Motor Test screen
   - Verify frame class/type are detected correctly
   - Verify motor count matches your frame configuration

---

## Log Messages to Look For

**Successful Parameter Refresh**:
```
ParameterRepository: 🔄 Force refresh requested - clearing cache and fetching fresh parameters
ParameterRepository: 📋 Requesting all parameters...
ParameterRepository: ✅ PARAM_REQUEST_LIST sent to FC
```

**Successful Motor Test Cache Hit**:
```
ParameterRepository: Parameter FRAME_CLASS found in cache: 1.0
MotorTest: Loaded FRAME_CLASS = 1
MotorTest: Created motor list with 4 motors
```

