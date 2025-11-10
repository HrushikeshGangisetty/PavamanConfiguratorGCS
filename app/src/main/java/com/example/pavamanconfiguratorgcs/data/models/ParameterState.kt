package com.example.pavamanconfiguratorgcs.data.models

/**
 * UI state for parameter loading progress
 */
sealed class LoadingProgress {
    object Idle : LoadingProgress()
    data class Loading(val current: Int, val total: Int) : LoadingProgress() {
        val percentage: Int get() = if (total > 0) (current * 100) / total else 0
    }
    object Complete : LoadingProgress()
    data class Error(val message: String) : LoadingProgress()
}

/**
 * UI state for parameter editing
 */
sealed class EditState {
    object Idle : EditState()
    data class Editing(val paramName: String) : EditState()
    data class Saving(val paramName: String) : EditState()
    data class BatchSaving(val current: Int, val total: Int) : EditState()
    data class Success(val message: String) : EditState()
    data class Error(val message: String) : EditState()
}

