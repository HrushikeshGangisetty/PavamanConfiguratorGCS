package com.example.pavamanconfiguratorgcs.data.models

import org.simpleframework.xml.*

/**
 * Parameter metadata from ArduPilot parameter definitions
 */
data class ParameterMetadata(
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val units: String = "",
    val range: String = "",
    val increment: String = "",
    val values: Map<String, String> = emptyMap(),  // For bitmask/enum values
    val readOnly: Boolean = false,
    val rebootRequired: Boolean = false,
    val bitmask: Map<Int, String> = emptyMap(),
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val defaultValue: Float? = null
) {
    companion object {
        fun empty(name: String) = ParameterMetadata(
            name = name,
            displayName = name,
            description = "No description available"
        )
    }
}

/**
 * XML Models for parsing ArduPilot parameter files
 */
@Root(name = "parameters", strict = false)
data class ParametersXml @JvmOverloads constructor(
    @field:ElementList(inline = true, entry = "param", required = false)
    var params: List<ParamXml>? = null
)

@Root(name = "param", strict = false)
data class ParamXml @JvmOverloads constructor(
    @field:Attribute(name = "name", required = false)
    var name: String? = null,

    @field:Attribute(name = "humanName", required = false)
    var humanName: String? = null,

    @field:Attribute(name = "documentation", required = false)
    var documentation: String? = null,

    @field:Element(name = "field", required = false)
    var field: FieldXml? = null,

    @field:ElementList(inline = true, entry = "values", required = false)
    var valuesList: List<ValuesXml>? = null
)

@Root(name = "field", strict = false)
data class FieldXml @JvmOverloads constructor(
    @field:Attribute(name = "name", required = false)
    var name: String? = null,

    @field:Text(required = false)
    var value: String? = null
)

@Root(name = "values", strict = false)
data class ValuesXml @JvmOverloads constructor(
    @field:ElementList(inline = true, entry = "value", required = false)
    var values: List<ValueXml>? = null
)

@Root(name = "value", strict = false)
data class ValueXml @JvmOverloads constructor(
    @field:Attribute(name = "code", required = false)
    var code: String? = null,

    @field:Text(required = false)
    var text: String? = null
)

