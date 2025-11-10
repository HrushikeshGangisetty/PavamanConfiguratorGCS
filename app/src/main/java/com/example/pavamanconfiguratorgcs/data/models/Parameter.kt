package com.example.pavamanconfiguratorgcs.data.models

import com.divpundir.mavlink.definitions.common.MavParamType
import com.divpundir.mavlink.api.MavEnumValue
import java.util.Locale

/**
 * Represents a single MAVLink parameter from the flight controller
 */
data class Parameter(
    val name: String,
    val value: Float,
    val type: MavEnumValue<MavParamType>,
    val index: UShort,
    val description: String = "",
    val units: String = "",
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val defaultValue: Float? = null, // Factory default from ArduPilot
    val displayName: String = name,    // Human-readable name
    val originalValue: Float = value, // Value when first loaded from FC
    val isDirty: Boolean = false,
    val isReadOnly: Boolean = false,
    val rebootRequired: Boolean = false  // Reboot required after change
) {

    /**
     * Get parameter group/prefix (e.g., "WPNAV" from "WPNAV_SPEED")
     */
    val group: String
        get() {
            val underscore = name.indexOf('_')
            return if (underscore > 0) name.substring(0, underscore) else "Other"
        }

    /**
     * Format value as string based on parameter type
     */
    fun getValueAsString(): String {
        return when (type.value) {
            MavParamType.UINT8.value,
            MavParamType.INT8.value,
            MavParamType.UINT16.value,
            MavParamType.INT16.value,
            MavParamType.UINT32.value,
            MavParamType.INT32.value -> value.toInt().toString()
            else -> String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Parse string to float value
     */
    fun parseValue(valueString: String): Float? {
        return try {
            valueString.toFloat()
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Validate new value against parameter constraints
     */
    fun validate(newValue: Float): ValidationResult {
        if (isReadOnly) {
            return ValidationResult.Error("Parameter is read-only")
        }

        minValue?.let { min ->
            if (newValue < min) {
                return ValidationResult.Error("Value must be >= $min")
            }
        }

        maxValue?.let { max ->
            if (newValue > max) {
                return ValidationResult.Error("Value must be <= $max")
            }
        }

        // Type-specific validation
        when (type.value) {
            MavParamType.UINT8.value -> {
                if (newValue < 0 || newValue > 255) {
                    return ValidationResult.Error("Value must be 0-255")
                }
            }
            MavParamType.INT8.value -> {
                if (newValue < -128 || newValue > 127) {
                    return ValidationResult.Error("Value must be -128 to 127")
                }
            }
            MavParamType.UINT16.value -> {
                if (newValue < 0 || newValue > 65535) {
                    return ValidationResult.Error("Value must be 0-65535")
                }
            }
            MavParamType.INT16.value -> {
                if (newValue < -32768 || newValue > 32767) {
                    return ValidationResult.Error("Value must be -32768 to 32767")
                }
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Create copy with new value
     */
    fun withValue(newValue: Float): Parameter {
        return copy(
            value = newValue,
            isDirty = newValue != originalValue
        )
    }

    /**
     * Reset to original value
     */
    fun reset(): Parameter {
        return copy(value = originalValue, isDirty = false)
    }

    /**
     * Get type name for display
     */
    fun getTypeName(): String {
        return when (type.value) {
            MavParamType.UINT8.value -> "UInt8"
            MavParamType.INT8.value -> "Int8"
            MavParamType.UINT16.value -> "UInt16"
            MavParamType.INT16.value -> "Int16"
            MavParamType.UINT32.value -> "UInt32"
            MavParamType.INT32.value -> "Int32"
            MavParamType.REAL32.value -> "Float"
            else -> "Unknown"
        }
    }

    /**
     * Get detailed info for display
     */
    fun getDetailedInfo(): String {
        val parts = mutableListOf<String>()

        if (description.isNotEmpty()) {
            parts.add(description)
        }

        if (units.isNotEmpty()) {
            parts.add("Units: $units")
        }

        if (minValue != null || maxValue != null) {
            parts.add("Range: ${minValue ?: "∞"} - ${maxValue ?: "∞"}")
        }

        if (defaultValue != null) {
            parts.add("Default: $defaultValue")
        }

        if (rebootRequired) {
            parts.add("⚠️ Reboot required after change")
        }

        return parts.joinToString("\n")
    }
}

/**
 * Validation result for parameter value changes
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    fun isValid() = this is Valid
    fun errorMessage() = (this as? Error)?.message
}
