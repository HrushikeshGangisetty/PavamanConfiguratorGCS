import java.net.URL
import org.json.JSONObject

/**
 * Test script to verify ArduPilot metadata endpoint and data structure
 */
fun main() {
    println("=== TESTING ARDUPILOT METADATA ENDPOINT ===\n")

    val url = "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json"

    try {
        println("📥 Downloading from: $url")
        val jsonString = URL(url).readText()
        println("✅ Downloaded ${jsonString.length} bytes\n")

        val jsonObject = JSONObject(jsonString)

        // Count parameters
        val totalParams = jsonObject.length()
        println("📊 Total parameters: $totalParams\n")

        // Test specific known parameters
        val testParams = listOf("WPNAV_SPEED", "BATT_CAPACITY", "ANGLE_MAX", "RTL_ALT", "PILOT_SPEED_UP")

        println("=== TESTING KNOWN PARAMETERS ===\n")

        testParams.forEach { paramName ->
            println("Parameter: $paramName")
            val paramObj = jsonObject.optJSONObject(paramName)

            if (paramObj != null) {
                println("  ✅ Found in JSON")
                println("  DisplayName: ${paramObj.optString("DisplayName", "MISSING")}")
                println("  Description: ${paramObj.optString("Description", "MISSING").take(60)}...")
                println("  Units: ${paramObj.optString("Units", "MISSING")}")
                println("  Range: ${paramObj.optString("Range", "MISSING")}")
                println("  Default: ${paramObj.optString("Default", "MISSING")}")
                println("  RebootRequired: ${paramObj.optString("RebootRequired", "MISSING")}")
            } else {
                println("  ❌ NOT FOUND in JSON")
            }
            println()
        }

        // Count coverage
        var hasDisplayName = 0
        var hasDescription = 0
        var hasUnits = 0
        var hasRange = 0
        var hasDefault = 0
        var hasRebootRequired = 0

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val paramName = keys.next()
            val paramObj = jsonObject.optJSONObject(paramName) ?: continue

            if (paramObj.optString("DisplayName", "").isNotEmpty()) hasDisplayName++
            if (paramObj.optString("Description", "").isNotEmpty()) hasDescription++
            if (paramObj.optString("Units", "").isNotEmpty()) hasUnits++
            if (paramObj.optString("Range", "").isNotEmpty()) hasRange++
            if (paramObj.optString("Default", "").isNotEmpty()) hasDefault++
            if (paramObj.optString("RebootRequired", "").isNotEmpty()) hasRebootRequired++
        }

        println("=== METADATA COVERAGE ===")
        println("Display Names:    $hasDisplayName / $totalParams (${hasDisplayName * 100 / totalParams}%)")
        println("Descriptions:     $hasDescription / $totalParams (${hasDescription * 100 / totalParams}%)")
        println("Units:            $hasUnits / $totalParams (${hasUnits * 100 / totalParams}%)")
        println("Ranges:           $hasRange / $totalParams (${hasRange * 100 / totalParams}%)")
        println("Default Values:   $hasDefault / $totalParams (${hasDefault * 100 / totalParams}%)")
        println("Reboot Required:  $hasRebootRequired / $totalParams (${hasRebootRequired * 100 / totalParams}%)")

        println("\n✅ ENDPOINT IS WORKING AND RETURNING VALID DATA")

    } catch (e: Exception) {
        println("❌ ERROR: ${e.message}")
        e.printStackTrace()
    }
}

