import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ReaderBoundaryCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredJavascript: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        productionSources.files.filter { it.isFile }.forEach { file ->
            val path = file.invariantSeparatorsPath
            val source = file.readText()
            val isReadiumAdapter = "/reader/readium/" in path
            if (!isReadiumAdapter && "import org.readium." in source) {
                violations += "$path: Readium import outside the Reader adapter"
            }
            if (!isReadiumAdapter && "import android.webkit." in source) {
                violations += "$path: WebView type outside the Reader adapter"
            }
            if ("/reader/" !in path && "reader.cfi.EpubCfi" in source) {
                violations += "$path: renderer CFI contract leaked outside Reader"
            }
            if ("/reader/" in path) {
                FORBIDDEN_READER_SERVER_CALLS.forEach { call ->
                    if (call in source) {
                        violations += "$path: forbidden Reader server mutation $call"
                    }
                }
            }
            if (isReadiumAdapter && REFLECTION_MARKERS.any { it in source }) {
                violations += "$path: reflection is forbidden at the Readium boundary"
            }
        }
        authoredJavascript.files.filter { it.isFile }.forEach { file ->
            val source = file.readText()
            FORBIDDEN_MOVEMENT_CALLS.forEach { call ->
                if (call in source) {
                    violations += "${file.invariantSeparatorsPath}: forbidden raw movement $call"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.sorted().joinToString(
                    prefix = "Reader boundary check failed:\n",
                    separator = "\n"
                )
            )
        }
        logger.lifecycle("Reader dependency and mutation boundaries passed.")
    }

    private companion object {
        val FORBIDDEN_READER_SERVER_CALLS =
            listOf(".openSession(", ".replaceProgress(", ".synchronizeAnnotations(")
        val REFLECTION_MARKERS =
            listOf("java.lang.reflect", "getDeclaredField(", "getDeclaredMethod(")
        val FORBIDDEN_MOVEMENT_CALLS = listOf("scrollIntoView(", "window.scrollTo(")
    }
}
