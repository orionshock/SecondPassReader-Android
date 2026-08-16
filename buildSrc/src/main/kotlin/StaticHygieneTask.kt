import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

@DisableCachingByDefault(because = "Validates the current Git working tree")
abstract class StaticHygieneTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val textFiles: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val repository = repositoryDirectory.get().asFile
        val issues = mutableListOf<String>()
        val mojibakePatterns =
            listOf(
                Regex("\u00c3[\u0080-\u00bf]"),
                Regex("\u00c2[\u0080-\u00bf]"),
                Regex("\u00e2\u20ac"),
                Regex("\ufffd")
            )

        textFiles.files.filter { it.isFile }.sorted().forEach { file ->
            val relativePath = file.relativeTo(repository).invariantSeparatorsPath
            val text = decodeUtf8(file.readBytes())
            if (text == null) {
                issues += "$relativePath: invalid UTF-8"
                return@forEach
            }

            if (mojibakePatterns.any { it.containsMatchIn(text) }) {
                issues += "$relativePath: contains a likely mojibake marker"
            }

            text.lineSequence().forEachIndexed { index, line ->
                if (line.endsWith(' ') || line.endsWith('\t')) {
                    issues += "$relativePath:${index + 1}: trailing whitespace"
                }
            }
        }

        val gitDiffCheck =
            ProcessBuilder("git", "diff", "--check")
                .directory(repository)
                .redirectErrorStream(true)
                .start()
        val gitOutput = gitDiffCheck.inputStream.bufferedReader().readText().trim()
        if (gitDiffCheck.waitFor() != 0) {
            issues += "git diff --check failed${if (gitOutput.isEmpty()) "" else ":\n$gitOutput"}"
        }

        if (issues.isNotEmpty()) {
            throw GradleException(issues.joinToString(prefix = "Static hygiene failed:\n", separator = "\n"))
        }

        logger.lifecycle("Static hygiene passed (${textFiles.files.size} text files scanned).")
    }

    private fun decodeUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
}
