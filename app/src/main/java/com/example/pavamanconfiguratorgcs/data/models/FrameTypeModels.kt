package com.example.pavamanconfiguratorgcs.data.models

/**
 * Supported frame types (restricted to X-model variants only)
 */
enum class FrameType(
    val displayName: String,
    val motorCount: Int,
    val description: String
) {
    QUAD_X(
        displayName = "Quad X",
        motorCount = 4,
        description = "Quadcopter X Frame"
    ),
    HEXA_X(
        displayName = "Hexa X",
        motorCount = 6,
        description = "Hexacopter X Frame"
    ),
    OCTA_X(
        displayName = "Octa X",
        motorCount = 8,
        description = "Octacopter X Frame"
    );

    /**
     * Get motor positions for UI display (1-based indexing)
     */
    fun getMotorIndices(): List<Int> = (1..motorCount).toList()

    companion object {
        /**
         * Get frame type from motor count (defaults to QUAD_X if unknown)
         */
        fun fromMotorCount(count: Int): FrameType? {
            return entries.find { it.motorCount == count }
        }

        /**
         * Get all available frame types
         */
        fun getAllFrameTypes(): List<FrameType> = entries
    }
}

/**
 * Frame parameter scheme used by the autopilot
 */
enum class FrameParamScheme {
    LEGACY_FRAME,           // Uses single FRAME parameter (older ArduPilot)
    CLASS_TYPE,             // Uses FRAME_CLASS + FRAME_TYPE (newer ArduPilot)
    UNKNOWN
}

/**
 * Frame configuration state
 */
data class FrameConfig(
    val currentFrameType: FrameType?,
    val paramScheme: FrameParamScheme,
    val frameParamValue: Float? = null,           // Value of FRAME parameter (if legacy)
    val frameClassValue: Float? = null,           // Value of FRAME_CLASS parameter
    val frameTypeValue: Float? = null,            // Value of FRAME_TYPE parameter
    val rebootRequired: Boolean = false,
    val isDetected: Boolean = false
) {
    /**
     * Check if frame configuration is valid and detected
     */
    val isValid: Boolean
        get() = isDetected && currentFrameType != null

    /**
     * Get human-readable status
     */
    fun getStatusDescription(): String {
        return when {
            !isDetected -> "Frame parameters not detected"
            currentFrameType == null -> "Unknown frame type"
            rebootRequired -> "${currentFrameType.displayName} (Reboot Required)"
            else -> currentFrameType.displayName
        }
    }
}

/**
 * Frame type mapping for ArduPilot FRAME parameter (legacy scheme)
 * These are typical ArduPilot Copter FRAME values
 */
object LegacyFrameMapping {
    const val FRAME_QUAD_X = 1
    const val FRAME_HEXA_X = 4
    const val FRAME_OCTA_X = 10

    fun frameTypeToValue(frameType: FrameType): Int {
        return when (frameType) {
            FrameType.QUAD_X -> FRAME_QUAD_X
            FrameType.HEXA_X -> FRAME_HEXA_X
            FrameType.OCTA_X -> FRAME_OCTA_X
        }
    }

    fun valueToFrameType(value: Float): FrameType? {
        return when (value.toInt()) {
            FRAME_QUAD_X -> FrameType.QUAD_X
            FRAME_HEXA_X -> FrameType.HEXA_X
            FRAME_OCTA_X -> FrameType.OCTA_X
            else -> null
        }
    }
}

/**
 * Frame type mapping for ArduPilot FRAME_CLASS + FRAME_TYPE parameters (newer scheme)
 */
object ClassTypeFrameMapping {
    // FRAME_CLASS values (ArduPilot standard)
    const val CLASS_QUAD = 1
    const val CLASS_HEXA = 2  // Fixed: was 3, should be 2
    const val CLASS_OCTA = 3  // Fixed: was 5, should be 3

    // FRAME_TYPE values (X configuration)
    // ArduPilot uses 0 for PLUS and 1 for X, but some versions use different mappings
    // Standard mapping: 0=PLUS, 1=X, 2=V, 3=H
    const val TYPE_X = 1

    data class ClassTypeValue(val frameClass: Int, val frameType: Int)

    fun frameTypeToValues(frameType: FrameType): ClassTypeValue {
        return when (frameType) {
            FrameType.QUAD_X -> ClassTypeValue(CLASS_QUAD, TYPE_X)
            FrameType.HEXA_X -> ClassTypeValue(CLASS_HEXA, TYPE_X)
            FrameType.OCTA_X -> ClassTypeValue(CLASS_OCTA, TYPE_X)
        }
    }

    fun valuesToFrameType(frameClass: Float, frameType: Float): FrameType? {
        // Accept both 0 and 1 as X type for compatibility
        // Some ArduPilot versions use 0=X, others use 1=X
        val typeInt = frameType.toInt()
        if (typeInt != 0 && typeInt != 1) return null // Only X-model supported

        return when (frameClass.toInt()) {
            CLASS_QUAD -> FrameType.QUAD_X
            CLASS_HEXA -> FrameType.HEXA_X
            CLASS_OCTA -> FrameType.OCTA_X
            else -> null
        }
    }
}

/**
 * Motor layout mapping for UI display
 */
data class MotorLayout(
    val frameType: FrameType,
    val motors: List<MotorPosition>
)

/**
 * Individual motor position for visual display
 */
data class MotorPosition(
    val motorNumber: Int,       // 1-based motor number
    val servoChannel: Int,      // Corresponding servo output channel
    val label: String,          // Display label (e.g., "M1", "Front-Right")
    val description: String     // Position description
)

/**
 * Predefined motor layouts for supported frame types
 */
object MotorLayouts {

    fun getLayoutForFrame(frameType: FrameType): MotorLayout {
        return when (frameType) {
            FrameType.QUAD_X -> quadXLayout()
            FrameType.HEXA_X -> hexaXLayout()
            FrameType.OCTA_X -> octaXLayout()
        }
    }

    private fun quadXLayout() = MotorLayout(
        frameType = FrameType.QUAD_X,
        motors = listOf(
            MotorPosition(1, 1, "M1", "Front-Right (CW)"),
            MotorPosition(2, 2, "M2", "Rear-Left (CW)"),
            MotorPosition(3, 3, "M3", "Front-Left (CCW)"),
            MotorPosition(4, 4, "M4", "Rear-Right (CCW)")
        )
    )

    private fun hexaXLayout() = MotorLayout(
        frameType = FrameType.HEXA_X,
        motors = listOf(
            MotorPosition(1, 1, "M1", "Front-Right (CW)"),
            MotorPosition(2, 2, "M2", "Rear (CW)"),
            MotorPosition(3, 3, "M3", "Front-Left (CCW)"),
            MotorPosition(4, 4, "M4", "Rear-Left (CCW)"),
            MotorPosition(5, 5, "M5", "Rear-Right (CW)"),
            MotorPosition(6, 6, "M6", "Front (CCW)")
        )
    )

    private fun octaXLayout() = MotorLayout(
        frameType = FrameType.OCTA_X,
        motors = listOf(
            MotorPosition(1, 1, "M1", "Front-Right (CW)"),
            MotorPosition(2, 2, "M2", "Rear (CW)"),
            MotorPosition(3, 3, "M3", "Front-Left (CCW)"),
            MotorPosition(4, 4, "M4", "Rear-Left (CCW)"),
            MotorPosition(5, 5, "M5", "Rear-Right (CW)"),
            MotorPosition(6, 6, "M6", "Front-Left (CW)"),
            MotorPosition(7, 7, "M7", "Rear-Left (CCW)"),
            MotorPosition(8, 8, "M8", "Front-Right (CCW)")
        )
    )
}
