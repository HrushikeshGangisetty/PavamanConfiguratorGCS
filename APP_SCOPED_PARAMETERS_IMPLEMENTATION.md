# App-Scoped Parameter Loading Implementation

## Summary
Modified the parameter loading mechanism to fetch parameters once at the app scope level instead of reloading every time the Full Parameters screen is opened.

## Changes Made

### 1. AppNavigation.kt
**Location:** `navigation/AppNavigation.kt`

**Changes:**
- Created `parametersViewModel` at the `AppNavigation` composable scope (app-level)
- This ViewModel is now instantiated once when the app starts and persists across screen navigations
- Removed per-screen ViewModel creation for the Full Parameters screen
- The ViewModel is now shared across all navigations to the Full Parameters screen

**Key Code:**
```kotlin
// Create app-scoped ParametersViewModel - created once and reused
val parametersViewModel: ParametersViewModel = viewModel(
    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ParametersViewModel(telemetryRepository) as T
        }
    }
)
```

### 2. ParametersViewModel.kt
**Location:** `ui/fullparams/ParametersViewModel.kt`

**Changes:**
- Added `hasLoadedInitially` flag to track if parameters have been fetched
- Modified `initializeParameterRepository()` to auto-fetch parameters once on first initialization
- Added `isParametersLoaded` StateFlow to expose the loaded state to the UI
- Parameters are now fetched automatically when the ViewModel is first created and connected

**Key Code:**
```kotlin
private var hasLoadedInitially = false

// Track if parameters have been loaded (at least once)
val isParametersLoaded: StateFlow<Boolean> = _allParameters
    .map { it.isNotEmpty() }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

// Auto-fetch parameters once on initialization
if (!hasLoadedInitially) {
    hasLoadedInitially = true
    fetchParameters()
    Log.d(TAG, "Auto-fetching parameters on first initialization")
}
```

### 3. ParametersScreen.kt
**Location:** `ui/fullparams/ParametersScreen.kt`

**Changes:**
- Removed `LaunchedEffect(Unit) { viewModel.fetchParameters() }` that was causing reload on every screen open
- Parameters are no longer fetched when navigating to the screen
- Users can still manually refresh using the refresh button in the toolbar

**Removed Code:**
```kotlin
// Auto-load parameters when screen opens
LaunchedEffect(Unit) {
    viewModel.fetchParameters()
}
```

## Benefits

### 1. **Performance Improvement**
- Parameters are loaded only once when the app connects to the flight controller
- No redundant network requests when navigating back and forth
- Faster screen transitions

### 2. **Better User Experience**
- Instant display of parameters when returning to the screen
- No loading delay on subsequent visits
- Preserves search filters and scroll position

### 3. **Data Consistency**
- Same parameter data is shown across all visits
- Pending edits are preserved when navigating away and back
- Manual refresh available if needed

### 4. **Resource Efficiency**
- Reduced network traffic to flight controller
- Lower memory churn from repeated object creation
- Better battery usage on mobile devices

## Usage Flow

1. **App Launch:** User starts the app and connects to flight controller
2. **First Access:** When user first navigates to Full Parameters screen:
   - ViewModel is created at app scope
   - Parameters are automatically fetched from flight controller
   - Loading progress is shown
3. **Subsequent Access:** When user navigates back to Full Parameters:
   - Same ViewModel instance is used
   - Parameters are instantly displayed (no reload)
   - No loading screen shown
4. **Manual Refresh:** User can click the refresh button to reload if needed

## Testing Checklist

- [x] Parameters load automatically on first screen visit
- [x] Parameters persist when navigating away and back
- [x] Search and filters are maintained
- [x] Manual refresh button still works
- [x] Pending edits are preserved
- [x] No compilation errors
- [x] ViewModel properly scoped to navigation lifecycle

## Notes

- The ViewModel lifecycle is tied to the NavController, so it will be cleared when the app is fully closed
- If connection is lost and re-established, parameters will need to be manually refreshed
- The refresh button in the toolbar can always be used to force a reload if needed

