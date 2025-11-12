package com.example.pavamanconfiguratorgcs.data.models

/**
 * Represents a single servo channel's state
 */
data class ServoChannel(
    val channelIndex: Int,      // 1-based channel number (1..16)
    val pwm: Int,               // PWM in microseconds (1000..2000), or 0 if unused
    val function: ServoFunction = ServoFunction.DISABLED,
    val reverse: Boolean = false,
    val minPwm: Int = 1100,
    val trimPwm: Int = 1500,
    val maxPwm: Int = 1900,
    val timestampUsec: Long = 0L
) {
    val isActive: Boolean get() = pwm > 0
    val isMotor: Boolean get() = function.isMotor
}

/**
 * Snapshot of all servo outputs at a given time
 */
data class ServoSnapshot(
    val port: Int,
    val channels: List<ServoChannel>,
    val timestampUsec: Long
)

/**
 * Servo/Motor function assignments
 */
enum class ServoFunction(val displayName: String, val isMotor: Boolean = false) {
    DISABLED("Disabled", false),
    MOTOR1("Motor1", true),
    MOTOR2("Motor2", true),
    MOTOR3("Motor3", true),
    MOTOR4("Motor4", true),
    MOTOR5("Motor5", true),
    MOTOR6("Motor6", true),
    MOTOR7("Motor7", true),
    MOTOR8("Motor8", true),
    SERVO("Servo", false),
    GIMBAL_PITCH("Gimbal Pitch", false),
    GIMBAL_ROLL("Gimbal Roll", false),
    GIMBAL_YAW("Gimbal Yaw", false),
    PARACHUTE("Parachute", false);

    /**
     * Convert ServoFunction to ArduPilot parameter value
     */
    fun toParameterValue(): Int {
        return when (this) {
            DISABLED -> 0
            MOTOR1 -> 33
            MOTOR2 -> 34
            MOTOR3 -> 35
            MOTOR4 -> 36
            MOTOR5 -> 37
            MOTOR6 -> 38
            MOTOR7 -> 39
            MOTOR8 -> 40
            else -> 0  // Default for other functions
        }
    }

    companion object {
        fun fromIndex(index: Int): ServoFunction {
            return when (index) {
                0 -> DISABLED
                in 33..40 -> entries[index - 32] // Motor 1-8 (ArduPilot convention)
                else -> SERVO
            }
        }

        fun getMotorFunctions(): List<ServoFunction> {
            return listOf(MOTOR1, MOTOR2, MOTOR3, MOTOR4, MOTOR5, MOTOR6, MOTOR7, MOTOR8)
        }

        fun getAllFunctions(): List<ServoFunction> {
            return entries.toList()
        }
    }
}

/**
 * Vehicle state for safety checks
 */
data class VehicleState(
    val isArmed: Boolean = false,
    val mode: String = "UNKNOWN",
    val isFlying: Boolean = false,
    val systemStatus: Int = 0
)
