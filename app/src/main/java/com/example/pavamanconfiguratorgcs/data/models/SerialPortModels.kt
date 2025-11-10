package com.example.pavamanconfiguratorgcs.data.models

/**
 * Represents a single serial port configuration
 */
data class SerialPortConfig(
    val portNumber: Int,
    val baudParamName: String,
    val protocolParamName: String,
    val optionsParamName: String?,
    val currentBaud: Float,
    val currentProtocol: Float,
    val currentOptions: Float?,
    val baudMetadata: ParameterMetadata? = null,
    val protocolMetadata: ParameterMetadata? = null,
    val optionsMetadata: ParameterMetadata? = null,
    val txPin: String = "",  // Optional TX pin description
    val rxPin: String = ""   // Optional RX pin description
) {
    val displayName: String
        get() = if (txPin.isNotEmpty()) {
            "Serial Port $portNumber (TX=$txPin, RX=$rxPin)"
        } else {
            "Serial Port $portNumber"
        }

    fun getBaudDisplayValue(): String {
        return baudOptions.find { it.value == currentBaud.toInt() }?.label
            ?: currentBaud.toInt().toString()
    }

    fun getProtocolDisplayValue(): String {
        // Try metadata first - values is a Map<String, String> where key is the numeric value as string
        val metadataLabel = protocolMetadata?.values?.entries?.firstOrNull {
            it.key.toIntOrNull() == currentProtocol.toInt()
        }?.value

        return metadataLabel
            ?: protocolOptions.find { it.value == currentProtocol.toInt() }?.label
            ?: "Unknown (${currentProtocol.toInt()})"
    }
}

/**
 * Represents an option for dropdown selection
 */
data class DropdownOption(
    val value: Int,
    val label: String
)

/**
 * Common baud rate options for ArduPilot serial ports
 */
val baudOptions = listOf(
    DropdownOption(1, "1200"),
    DropdownOption(2, "2400"),
    DropdownOption(4, "4800"),
    DropdownOption(9, "9600"),
    DropdownOption(19, "19200"),
    DropdownOption(38, "38400"),
    DropdownOption(57, "57600"),
    DropdownOption(111, "111100"),
    DropdownOption(115, "115200"),
    DropdownOption(230, "230400"),
    DropdownOption(256, "256000"),
    DropdownOption(460, "460800"),
    DropdownOption(500, "500000"),
    DropdownOption(921, "921600"),
    DropdownOption(1500, "1500000"),
    DropdownOption(2000, "2000000")
)

/**
 * Common protocol options for ArduPilot serial ports
 * Based on AP_SerialManager protocol definitions
 */
val protocolOptions = listOf(
    DropdownOption(-1, "None"),
    DropdownOption(0, "MAVLink1"),
    DropdownOption(1, "MAVLink2"),
    DropdownOption(2, "Frsky D"),
    DropdownOption(3, "Frsky SPort"),
    DropdownOption(4, "GPS"),
    DropdownOption(5, "GPS2"),
    DropdownOption(7, "Alexmos Gimbal Serial"),
    DropdownOption(8, "SToRM32 Gimbal Serial"),
    DropdownOption(9, "Rangefinder"),
    DropdownOption(10, "FrSky SPort Passthrough (OpenTX)"),
    DropdownOption(11, "Lidar360"),
    DropdownOption(13, "Beacon"),
    DropdownOption(14, "Volz servo out"),
    DropdownOption(15, "SBus servo out"),
    DropdownOption(16, "ESC Telemetry"),
    DropdownOption(17, "Devo Telemetry"),
    DropdownOption(18, "OpticalFlow"),
    DropdownOption(19, "RobotisServo"),
    DropdownOption(20, "NMEA Output"),
    DropdownOption(21, "WindVane"),
    DropdownOption(22, "SLCAN"),
    DropdownOption(23, "RCIN"),
    DropdownOption(24, "EFI Serial"),
    DropdownOption(25, "LTM"),
    DropdownOption(26, "RunCam"),
    DropdownOption(27, "HottTelem"),
    DropdownOption(28, "Scripting"),
    DropdownOption(29, "CRSF"),
    DropdownOption(30, "Generator"),
    DropdownOption(31, "Winch"),
    DropdownOption(32, "MSP"),
    DropdownOption(33, "DJI FPV"),
    DropdownOption(34, "AirSpeed"),
    DropdownOption(35, "ADSB"),
    DropdownOption(36, "AHRS"),
    DropdownOption(37, "SmartAudio"),
    DropdownOption(38, "FETtecOneWire"),
    DropdownOption(39, "Torqeedo"),
    DropdownOption(40, "AIS"),
    DropdownOption(41, "CoDevESC"),
    DropdownOption(42, "DisplayPort"),
    DropdownOption(43, "MAVLink High Latency")
)

/**
 * Bitmask flag for serial options
 */
data class BitFlag(
    val bit: Int,
    val label: String,
    val description: String = ""
)

/**
 * Common serial port option flags
 */
val commonSerialOptionFlags = listOf(
    BitFlag(0, "InvertRX", "Invert RX signal"),
    BitFlag(1, "InvertTX", "Invert TX signal"),
    BitFlag(2, "HalfDuplex", "Half duplex mode"),
    BitFlag(3, "SwapTXRX", "Swap TX and RX pins"),
    BitFlag(4, "PulldownRX", "Pulldown RX"),
    BitFlag(5, "PullupRX", "Pullup RX"),
    BitFlag(6, "PulldownTX", "Pulldown TX"),
    BitFlag(7, "PullupTX", "Pullup TX"),
    BitFlag(8, "NoDelayTX", "No delay on TX"),
    BitFlag(9, "ManualRCTX", "Manual RC TX")
)
