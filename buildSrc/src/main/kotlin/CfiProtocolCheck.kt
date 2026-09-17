import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.GradleException

/** Checks committed wire declarations without Node or runtime generation. */
internal class CfiProtocolCheck(manifest: File) {
    private val protocol = JsonSlurper().parse(manifest).objectMap()
    private val methods = protocol.getValue("methods").objectMap().toSortedMap()
    private val types = protocol.getValue("types").objectMap().toSortedMap()
    private val envelope = protocol.getValue("envelope").objectMap()
    val version: String = protocol.getValue("runtimeVersion").toString()

    fun check(kotlin: File, typescript: File, entrypoint: File, runtime: File, adapter: File) {
        verify(kotlin.readText(), kotlinDeclarations(), kotlin.path)
        verify(typescript.readText(), typescriptDeclarations(), typescript.path)
        checkKotlinArguments(adapter.readText())
        val entry = entrypoint.readText()
        methods.forEach { (name, value) ->
            val expected = value.objectMap().arguments().map { it[0] }
            val pattern = Regex("\\[P\\.METHOD_${symbol(name)}]:\\s*\\(([^)]*)\\)\\s*=>")
            val actual = pattern.find(entry)?.groupValues?.get(1)
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
            if (actual != expected) fail("TypeScript entrypoint $name arguments drifted")
        }
        val asset = runtime.readText().replace("\r\n", "\n")
        if (!asset.contains("// CFI-Protocol-Version: $version\n")) {
            fail("Generated runtime protocol version mismatch")
        }
        constants().forEach { (name, value) ->
            val literal = Regex("\\b$name:\\s*([^,}\\s]+)").find(asset)?.groupValues?.get(1)
            val matches = if (value is Number) {
                literal?.toBigDecimalOrNull()?.compareTo(value.toString().toBigDecimal()) == 0
            } else {
                literal == quote(value)
            }
            if (!matches) fail("Generated runtime protocol constant $name drifted")
        }
    }

    private fun checkKotlinArguments(adapter: String) {
        val calls = Regex("method = CfiRuntimeMethod\\.(\\w+),([\\s\\S]*?)\\)\\.mapValue")
            .findAll(adapter).toList()
        if (calls.isEmpty()) fail("Kotlin CFI method declarations are missing")
        calls.forEach { call ->
            val method = methods.entries.singleOrNull { symbol(it.key) == call.groupValues[1] }
                ?: fail("Unknown Kotlin CFI method")
            val expected = method.value.objectMap().arguments().map { symbol(it[0]) }.sorted()
            val actual = Regex("P\\.ARG_(\\w+) to").findAll(call.groupValues[2])
                .map { it.groupValues[1] }.sorted().toList()
            if (actual != expected) fail("Kotlin CFI method ${method.key} arguments drifted")
        }
    }

    private fun constants(): Map<String, Any> = buildMap {
        put("RUNTIME_VERSION", version)
        put("GLOBAL", protocol.getValue("global"))
        putAll(protocol.getValue("limits").objectMap())
        envelope.forEach { (name, value) -> put("FIELD_${symbol(name)}", value) }
        types.values.forEach { fields ->
            fields.objectMap().keys.forEach { field ->
                val name = field.removeSuffix("?")
                put("FIELD_${symbol(name)}", name)
            }
        }
        protocol.strings("errors").forEach { put("ERROR_$it", it) }
        protocol.strings("kinds").forEach { put("KIND_${symbol(it)}", it) }
        methods.forEach { (name, value) ->
            put("METHOD_${symbol(name)}", name)
            value.objectMap().arguments().forEach { put("ARG_${symbol(it[0])}", it[0]) }
        }
    }.toSortedMap()

    internal fun kotlinDeclarations(): String = buildString {
        append("// GENERATED from tools/reader-cfi-runtime/protocol.json; do not edit.\n")
        append("package com.secondpasslibrary.reader.reader.readium.cfi\n\ninternal object CfiProtocol {\n")
        constants().forEach { (name, value) -> append("    const val $name = ${quote(value)}\n") }
        append("}\n\n")
        append("internal enum class CfiRuntimeMethod(val wireName: String, vararg val argumentNames: String) {\n")
        append(methods.entries.joinToString(",\n") { (name, value) ->
            val values = listOf("CfiProtocol.METHOD_${symbol(name)}") +
                value.objectMap().arguments().map { "CfiProtocol.ARG_${symbol(it[0])}" }
            "    ${symbol(name)}(\n" + values.joinToString(",\n") { "        $it" } + "\n    )"
        })
        append("\n}\n")
    }

    internal fun typescriptDeclarations(): String = buildString {
        append("// GENERATED from protocol.json; do not edit.\nexport const Protocol = {\n")
        append(constants().entries.joinToString(",\n") { (key, value) -> "  $key: ${quote(value)}" })
        append("\n} as const;\n")
        append("export type TargetKind = ${protocol.strings("kinds").joinToString(" | ", transform = ::quote)};\n")
        append("export type RuntimeErrorCode = ${protocol.strings("errors").joinToString(" | ", transform = ::quote)};\n")
        append("export type RuntimeResult<T> =\n")
        append("  | { readonly ${envelope["ok"]}: true; readonly ${envelope["value"]}: T }\n")
        append("  | { readonly ${envelope["ok"]}: false; readonly ${envelope["error"]}: { readonly ${envelope["code"]}: RuntimeErrorCode } };\n")
        types.forEach { (name, fields) ->
            append("export interface $name {\n")
            fields.objectMap().toSortedMap().forEach { (field, type) -> append("  readonly $field: $type;\n") }
            append("}\n")
        }
        append("export interface RuntimeFacade {\n")
        methods.forEach { (name, value) ->
            val method = value.objectMap()
            val arguments = method.arguments().joinToString(", ") { "${it[0]}: ${it[1]}" }
            val result = if (name == "runtimeVersion") method["result"] else "RuntimeResult<${method["result"]}>"
            append("  [Protocol.METHOD_${symbol(name)}]($arguments): $result;\n")
        }
        append("}\n")
    }

    private fun verify(actual: String, expected: String, label: String) {
        if (actual.replace("\r\n", "\n") != expected) fail("Stale CFI protocol declarations: $label")
    }

    private fun fail(message: String): Nothing =
        throw GradleException("$message. Run npm run build in tools/reader-cfi-runtime.")

    private fun symbol(name: String): String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

    private fun quote(value: Any): String = JsonOutput.toJson(value)
}

@Suppress("UNCHECKED_CAST")
private fun Any.objectMap(): Map<String, Any> = this as Map<String, Any>

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.arguments(): List<List<String>> = getValue("arguments") as List<List<String>>

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.strings(key: String): List<String> = getValue(key) as List<String>
