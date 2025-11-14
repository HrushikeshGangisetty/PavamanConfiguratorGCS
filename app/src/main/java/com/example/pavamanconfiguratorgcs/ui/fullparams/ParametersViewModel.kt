package com.example.pavamanconfiguratorgcs.ui.fullparams

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pavamanconfiguratorgcs.data.models.*
import com.example.pavamanconfiguratorgcs.data.repository.ParameterRepository
import com.example.pavamanconfiguratorgcs.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Parameters Screen
 * Manages parameter fetching, editing, and saving
 */
class ParametersViewModel(
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ParametersViewModel"
    }

    private var parameterRepository: ParameterRepository? = null
    private var hasLoadedInitially = false

    // All parameters from FC
    private val _allParameters = MutableStateFlow<List<Parameter>>(emptyList())

    // Filtered parameters for display
    private val _filteredParameters = MutableStateFlow<List<Parameter>>(emptyList())
    val parameters: StateFlow<List<Parameter>> = _filteredParameters.asStateFlow()

    // Pending edits (not yet saved)
    private val _pendingEdits = MutableStateFlow<Map<String, Parameter>>(emptyMap())
    val pendingEdits: StateFlow<Map<String, Parameter>> = _pendingEdits.asStateFlow()

    // Loading progress
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    // Edit state
    private val _editState = MutableStateFlow<EditState>(EditState.Idle)
    val editState: StateFlow<EditState> = _editState.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected group filter
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    // Show only dirty parameters
    private val _showOnlyDirty = MutableStateFlow(false)
    val showOnlyDirty: StateFlow<Boolean> = _showOnlyDirty.asStateFlow()

    // Available parameter groups
    val availableGroups: StateFlow<List<String>> = _allParameters
        .map { params ->
            params.map { it.group }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Has unsaved changes
    val hasUnsavedChanges: StateFlow<Boolean> = _pendingEdits
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Track if parameters have been loaded (at least once)
    val isParametersLoaded: StateFlow<Boolean> = _allParameters
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Initialize parameter repository when connected
        viewModelScope.launch {
            telemetryRepository.connectionState.collect { state ->
                if (state is TelemetryRepository.ConnectionState.HeartbeatVerified) {
                    initializeParameterRepository()
                }
            }
        }
    }

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

            // Observe loading progress
            viewModelScope.launch {
                parameterRepository?.loadingProgress?.collect { progress ->
                    _loadingProgress.value = when {
                        progress.isComplete -> LoadingProgress.Complete
                        progress.errorMessage != null -> LoadingProgress.Error(progress.errorMessage ?: "")
                        progress.total > 0 -> LoadingProgress.Loading(progress.current, progress.total)
                        else -> LoadingProgress.Idle
                    }
                }
            }

            // Check if parameters are already loaded (from background loading)
            viewModelScope.launch {
                // Give it a moment to check the current state
                kotlinx.coroutines.delay(500)

                val currentParams = parameterRepository?.parameters?.value
                if (currentParams.isNullOrEmpty() && !hasLoadedInitially) {
                    // Parameters not loaded yet, fetch them
                    hasLoadedInitially = true
                    fetchParameters()
                    Log.d(TAG, "Parameters not loaded yet, fetching...")
                } else if (!currentParams.isNullOrEmpty()) {
                    // Parameters already loaded from background
                    hasLoadedInitially = true
                    Log.d(TAG, "Parameters already loaded in background (${currentParams.size} params)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing parameter repository", e)
            _editState.value = EditState.Error("Failed to initialize: ${e.message}")
        }
    }

    /**
     * Fetch all parameters from flight controller
     */
    fun fetchParameters() {
        viewModelScope.launch {
            // Clear all pending edits
            _pendingEdits.value = emptyMap()

            // Clear all parameters to force fresh data
            _allParameters.value = emptyList()
            _filteredParameters.value = emptyList()

            // Reset edit state
            _editState.value = EditState.Idle

            // Start loading
            _loadingProgress.value = LoadingProgress.Loading(0, 0)

            // Request fresh parameters from flight controller with forceRefresh=true
            parameterRepository?.requestAllParameters(forceRefresh = true)

            Log.d(TAG, "Refreshing all parameters from flight controller (force refresh)")
        }
    }

    /**
     * Edit parameter locally (not saved yet)
     */
    fun editParameter(paramName: String, newValueString: String) {
        val param = _allParameters.value.find { it.name == paramName }
        if (param == null) {
            _editState.value = EditState.Error("Parameter not found")
            return
        }

        // Parse value
        val newValue = param.parseValue(newValueString)
        if (newValue == null) {
            _editState.value = EditState.Error("Invalid number format")
            return
        }

        // Validate
        val validation = param.validate(newValue)
        if (!validation.isValid()) {
            _editState.value = EditState.Error(validation.errorMessage() ?: "Invalid value")
            return
        }

        // Update parameter
        val updatedParam = param.withValue(newValue)

        // Update in all parameters
        val allParams = _allParameters.value.toMutableList()
        val index = allParams.indexOfFirst { it.name == paramName }
        if (index >= 0) {
            allParams[index] = updatedParam
            _allParameters.value = allParams
        }

        // Add to pending edits
        val pending = _pendingEdits.value.toMutableMap()
        pending[paramName] = updatedParam
        _pendingEdits.value = pending

        _editState.value = EditState.Editing(paramName)
        applyFilters()

        Log.d(TAG, "Parameter edited: $paramName = $newValue")
    }

    /**
     * Save single parameter to flight controller
     */
    fun saveParameter(paramName: String) {
        viewModelScope.launch {
            val param = _pendingEdits.value[paramName]
            if (param == null) {
                _editState.value = EditState.Error("No pending edit for $paramName")
                return@launch
            }

            _editState.value = EditState.Saving(paramName)

            val result = parameterRepository?.setParameter(
                paramName = param.name,
                paramValue = param.value,
                paramType = param.type
            )

            result?.fold(
                onSuccess = {
                    // Remove from pending edits
                    val pending = _pendingEdits.value.toMutableMap()
                    pending.remove(paramName)
                    _pendingEdits.value = pending

                    // Update original value
                    val allParams = _allParameters.value.toMutableList()
                    val index = allParams.indexOfFirst { it.name == paramName }
                    if (index >= 0) {
                        allParams[index] = param.copy(
                            originalValue = param.value,
                            isDirty = false
                        )
                        _allParameters.value = allParams
                    }

                    _editState.value = EditState.Success("$paramName saved successfully")
                    applyFilters()
                },
                onFailure = {
                    _editState.value = EditState.Error("Failed to save: ${it.message}")
                }
            )
        }
    }

    /**
     * Save all pending edits
     */
    fun saveAllPendingEdits() {
        viewModelScope.launch {
            val pending = _pendingEdits.value.values.toList()
            if (pending.isEmpty()) return@launch

            var successCount = 0
            var failCount = 0

            pending.forEachIndexed { index, param ->
                _editState.value = EditState.BatchSaving(index + 1, pending.size)

                val result = parameterRepository?.setParameter(
                    paramName = param.name,
                    paramValue = param.value,
                    paramType = param.type
                )

                if (result?.isSuccess == true) {
                    successCount++

                    // Remove from pending
                    val pendingMap = _pendingEdits.value.toMutableMap()
                    pendingMap.remove(param.name)
                    _pendingEdits.value = pendingMap

                    // Update original value
                    val allParams = _allParameters.value.toMutableList()
                    val idx = allParams.indexOfFirst { it.name == param.name }
                    if (idx >= 0) {
                        allParams[idx] = param.copy(
                            originalValue = param.value,
                            isDirty = false
                        )
                        _allParameters.value = allParams
                    }
                } else {
                    failCount++
                }

                kotlinx.coroutines.delay(100) // Small delay between writes
            }

            _editState.value = if (failCount == 0) {
                EditState.Success("All $successCount parameters saved")
            } else {
                EditState.Error("$failCount parameters failed to save")
            }

            applyFilters()
        }
    }

    /**
     * Discard single edit
     */
    fun discardEdit(paramName: String) {
        // Remove from pending
        val pending = _pendingEdits.value.toMutableMap()
        pending.remove(paramName)
        _pendingEdits.value = pending

        // Reset to original value
        val allParams = _allParameters.value.toMutableList()
        val index = allParams.indexOfFirst { it.name == paramName }
        if (index >= 0) {
            allParams[index] = allParams[index].reset()
            _allParameters.value = allParams
        }

        applyFilters()
    }

    /**
     * Discard all pending edits
     */
    fun discardAllEdits() {
        // Reset all dirty parameters
        val allParams = _allParameters.value.map { if (it.isDirty) it.reset() else it }
        _allParameters.value = allParams

        // Clear pending edits
        _pendingEdits.value = emptyMap()

        _editState.value = EditState.Idle
        applyFilters()
    }

    /**
     * Search parameters
     */
    fun searchParameters(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    /**
     * Filter by group
     */
    fun selectGroup(group: String?) {
        _selectedGroup.value = group
        applyFilters()
    }

    /**
     * Toggle show only dirty
     */
    fun toggleShowOnlyDirty() {
        _showOnlyDirty.value = !_showOnlyDirty.value
        applyFilters()
    }

    /**
     * Apply all filters
     */
    private fun applyFilters() {
        var filtered = _allParameters.value

        // Search filter
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }

        // Group filter
        val group = _selectedGroup.value
        if (group != null) {
            filtered = filtered.filter { it.group == group }
        }

        // Dirty filter
        if (_showOnlyDirty.value) {
            filtered = filtered.filter { it.isDirty }
        }

        _filteredParameters.value = filtered
    }

    /**
     * Clear edit state message
     */
    fun clearEditState() {
        _editState.value = EditState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        parameterRepository?.cleanup()
    }
}
