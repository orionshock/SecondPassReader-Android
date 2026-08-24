import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ColibrioBundleCheckTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val browserBundle: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val canonicalLicense: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packagedLicense: RegularFileProperty

    @get:Input
    abstract val expectedSha256: Property<String>

    @TaskAction
    fun check() {
        val bundle = browserBundle.get().asFile
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bundle.readBytes())
            .joinToString(separator = "") { byte ->
                "%02X".format(byte.toInt() and BYTE_MASK)
            }
        val expected = expectedSha256.get().uppercase()
        if (actualSha256 != expected) {
            throw GradleException(
                "Unexpected Colibrio bundle hash $actualSha256; expected $expected."
            )
        }

        val canonical = canonicalLicense.get().asFile.readText().normalizeLineEndings()
        val packaged = packagedLicense.get().asFile.readText().normalizeLineEndings()
        if (canonical != packaged) {
            throw GradleException(
                "Packaged Colibrio license does not match the audited third-party license."
            )
        }

        logger.lifecycle("Colibrio bundle hash and packaged MIT license passed.")
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}

private fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
