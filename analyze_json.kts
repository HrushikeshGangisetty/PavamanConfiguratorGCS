import org.json.JSONObject
import java.net.URL

fun main() {
    println("=== Downloading ArduPilot JSON ===")

    val url = "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json"
    val jsonString = URL(url).readText()

    println("Downloaded ${jsonString.length} bytes")
    println("\n=== First 2000 characters ===")
    println(jsonString.take(2000))

    println("\n\n=== JSON Structure Analysis ===")
    val jsonObject = JSONObject(jsonString)

    val keys = jsonObject.keys().asSequence().toList().take(10)
    println("First 10 top-level keys: $keys")

    // Examine first key
    val firstKey = keys.firstOrNull()
    if (firstKey != null) {
        println("\n=== First key: '$firstKey' ===")
        val firstObj = jsonObject.optJSONObject(firstKey)
        if (firstObj != null) {
            val innerKeys = firstObj.keys().asSequence().toList().take(5)
            println("Keys inside: $innerKeys")

            // Check first inner key
            val firstInner = innerKeys.firstOrNull()
            if (firstInner != null) {
                val innerObj = firstObj.optJSONObject(firstInner)
                if (innerObj != null) {
                    println("\n=== Object at ['$firstKey']['$firstInner'] ===")
                    val fields = innerObj.keys().asSequence().toList()
                    println("Fields: $fields")
                    fields.forEach { field ->
                        val value = innerObj.opt(field)
                        println("  $field: ${value.toString().take(100)}")
                    }
                }
            }
        }
    }

    // Check second key
    if (keys.size > 1) {
        val secondKey = keys[1]
        println("\n=== Second key: '$secondKey' ===")
        val secondObj = jsonObject.optJSONObject(secondKey)
        if (secondObj != null) {
            val innerKeys = secondObj.keys().asSequence().toList().take(5)
            println("Keys inside: $innerKeys")

            val firstInner = innerKeys.firstOrNull()
            if (firstInner != null) {
                val innerObj = secondObj.optJSONObject(firstInner)
                if (innerObj != null) {
                    println("\n=== Object at ['$secondKey']['$firstInner'] ===")
                    val fields = innerObj.keys().asSequence().toList()
                    println("Fields: $fields")
                    fields.forEach { field ->
                        val value = innerObj.opt(field)
                        println("  $field: ${value.toString().take(100)}")
                    }
                }
            }
        }
    }
}

main()

