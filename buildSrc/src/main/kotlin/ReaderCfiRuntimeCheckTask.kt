import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ReaderCfiRuntimeCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceInputs: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedRuntime: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val protocolInputs: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val repository = repositoryDirectory.get().asFile
        CfiProtocolCheck(repository.resolve("tools/reader-cfi-runtime/protocol.json")).check(
            repository.resolve("app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/CfiProtocol.kt"),
            repository.resolve("tools/reader-cfi-runtime/src/protocol.generated.ts"),
            repository.resolve("tools/reader-cfi-runtime/src/runtime.ts"),
            generatedRuntime.get().asFile,
            repository.resolve("app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/ReadiumCfiJavascriptRuntime.kt")
        )
        val digest = MessageDigest.getInstance("SHA-256")
        sourceInputs.files.filter { it.isFile }
            .sortedBy { it.relativeTo(repository).invariantSeparatorsPath }
            .forEach { file ->
                digest.update(file.relativeTo(repository).invariantSeparatorsPath.toByteArray())
                digest.update(0.toByte())
                digest.update(file.readText().replace("\r\n", "\n").toByteArray())
                digest.update(0.toByte())
            }
        val expected = digest.digest().toHex()
        val runtime = generatedRuntime.get().asFile
        val embedded = runtime.useLines { lines ->
            lines.take(GENERATED_HEADER_LINES).firstNotNullOfOrNull { line ->
                SOURCE_DIGEST.matchEntire(line)?.groupValues?.get(1)
            }
        }
        if (embedded != expected) {
            throw GradleException(
                "Reader CFI runtime is stale: source digest $expected does not match " +
                    "${embedded ?: "the missing generated header"}. Run npm run build in " +
                    "tools/reader-cfi-runtime."
            )
        }
        logger.lifecycle("Reader CFI runtime source digest matches the generated asset.")
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and BYTE_MASK) }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val GENERATED_HEADER_LINES = 4
        val SOURCE_DIGEST = Regex("// Source-SHA256: ([0-9A-F]{64})")
    }
}
