package com.example.pavamanconfiguratorgcs.data.models

/**
 * Flight mode definitions for different firmware types.
 */
enum class FirmwareType {
    ARDUPILOT_COPTER,
    ARDUPILOT_PLANE,
    ARDUPILOT_ROVER,
    PX4,
    UNKNOWN
}

/**
 * Flight mode data class.
 */
data class FlightMode(
    val key: Int,
    val value: String
)

/**
 * Flight mode configuration for a specific slot.
 */
data class FlightModeSlot(
    val slot: Int,
    val mode: Int,
    val simpleEnabled: Boolean = false,
    val superSimpleEnabled: Boolean = false
)

/**
 * Complete flight mode configuration state.
 */
data class FlightModeConfiguration(
    val slots: List<FlightModeSlot> = List(6) { FlightModeSlot(it + 1, 0) },
    val currentModeIndex: Int = 0,
    val switchChannel: Int = 5,
    val switchPwm: Int = 1500
)

/**
 * Get available flight modes for firmware type.
 */
object FlightModeProvider {

    fun getModesForFirmware(firmwareType: FirmwareType): List<FlightMode> {
        return when (firmwareType) {
            FirmwareType.ARDUPILOT_COPTER -> getCopterModes()
            FirmwareType.ARDUPILOT_PLANE -> getPlaneModes()
            FirmwareType.ARDUPILOT_ROVER -> getRoverModes()
            FirmwareType.PX4 -> getPX4Modes()
            FirmwareType.UNKNOWN -> getCopterModes() // Default to copter
        }
    }

    private fun getCopterModes(): List<FlightMode> {
        return listOf(
            FlightMode(0, "Stabilize"),
            FlightMode(1, "Acro"),
            FlightMode(2, "Alt Hold"),
            FlightMode(3, "Auto"),
            FlightMode(4, "Guided"),
            FlightMode(5, "Loiter"),
            FlightMode(6, "RTL"),
            FlightMode(7, "Circle"),
            FlightMode(8, "Position"),
            FlightMode(9, "Land"),
            FlightMode(11, "Drift"),
            FlightMode(13, "Sport"),
            FlightMode(14, "Flip"),
            FlightMode(15, "AutoTune"),
            FlightMode(16, "PosHold"),
            FlightMode(17, "Brake"),
            FlightMode(18, "Throw"),
            FlightMode(19, "Avoid ADSB"),
            FlightMode(20, "Guided NoGPS"),
            FlightMode(21, "Smart RTL"),
            FlightMode(22, "FlowHold"),
            FlightMode(23, "Follow"),
            FlightMode(24, "ZigZag"),
            FlightMode(25, "SystemID"),
            FlightMode(26, "AutoRotate"),
            FlightMode(27, "Auto RTL")
        )
    }

    private fun getPlaneModes(): List<FlightMode> {
        return listOf(
            FlightMode(0, "Manual"),
            FlightMode(1, "Circle"),
            FlightMode(2, "Stabilize"),
            FlightMode(3, "Training"),
            FlightMode(4, "Acro"),
            FlightMode(5, "Fly By Wire A"),
            FlightMode(6, "Fly By Wire B"),
            FlightMode(7, "Cruise"),
            FlightMode(8, "Autotune"),
            FlightMode(10, "Auto"),
            FlightMode(11, "RTL"),
            FlightMode(12, "Loiter"),
            FlightMode(14, "Avoid ADSB"),
            FlightMode(15, "Guided"),
            FlightMode(17, "QStabilize"),
            FlightMode(18, "QHover"),
            FlightMode(19, "QLoiter"),
            FlightMode(20, "QLand"),
            FlightMode(21, "QRTL"),
            FlightMode(22, "QAutotune"),
            FlightMode(23, "QAcro")
        )
    }

    private fun getRoverModes(): List<FlightMode> {
        return listOf(
            FlightMode(0, "Manual"),
            FlightMode(1, "Acro"),
            FlightMode(3, "Steering"),
            FlightMode(4, "Hold"),
            FlightMode(5, "Loiter"),
            FlightMode(6, "Follow"),
            FlightMode(7, "Simple"),
            FlightMode(10, "Auto"),
            FlightMode(11, "RTL"),
            FlightMode(12, "Smart RTL"),
            FlightMode(15, "Guided")
        )
    }

    private fun getPX4Modes(): List<FlightMode> {
        return listOf(
            FlightMode(0, "Manual"),
            FlightMode(1, "Altitude"),
            FlightMode(2, "Position"),
            FlightMode(3, "Mission"),
            FlightMode(4, "Hold"),
            FlightMode(5, "Return"),
            FlightMode(6, "Acro"),
            FlightMode(7, "Offboard"),
            FlightMode(8, "Stabilized"),
            FlightMode(9, "Rattitude"),
            FlightMode(10, "Takeoff"),
            FlightMode(11, "Land"),
            FlightMode(12, "Follow Target")
        )
    }

    /**
     * Determine firmware type from autopilot type.
     */
    fun determineFirmwareType(autopilot: Int, vehicleType: Int): FirmwareType {
        // MAV_AUTOPILOT values
        val MAV_AUTOPILOT_ARDUPILOTMEGA = 3
        val MAV_AUTOPILOT_PX4 = 12

        // MAV_TYPE values
        val MAV_TYPE_FIXED_WING = 1
        val MAV_TYPE_QUADROTOR = 2
        val MAV_TYPE_GROUND_ROVER = 10

        return when (autopilot) {
            MAV_AUTOPILOT_ARDUPILOTMEGA -> {
                when (vehicleType) {
                    MAV_TYPE_FIXED_WING -> FirmwareType.ARDUPILOT_PLANE
                    MAV_TYPE_QUADROTOR -> FirmwareType.ARDUPILOT_COPTER
                    MAV_TYPE_GROUND_ROVER -> FirmwareType.ARDUPILOT_ROVER
                    else -> FirmwareType.ARDUPILOT_COPTER // Default to copter
                }
            }
            MAV_AUTOPILOT_PX4 -> FirmwareType.PX4
            else -> FirmwareType.UNKNOWN
        }
    }
}

