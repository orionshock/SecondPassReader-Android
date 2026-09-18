import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ArchitectureBoundaryCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredJavascript: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val repository = repositoryDirectory.get().asFile
        val sources = buildList {
            productionSources.files.filter { it.isFile }.forEach { file ->
                add(
                    ArchitectureSource(
                        file.relativeTo(repository).invariantSeparatorsPath,
                        file.readText(),
                        ArchitectureSourceKind.PRODUCTION
                    )
                )
            }
            authoredJavascript.files.filter { it.isFile }.forEach { file ->
                add(
                    ArchitectureSource(
                        file.relativeTo(repository).invariantSeparatorsPath,
                        file.readText(),
                        ArchitectureSourceKind.AUTHORED_JAVASCRIPT
                    )
                )
            }
        }
        val violations = ArchitectureBoundaryEvaluator.evaluate(sources)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "Architecture boundary check failed:\n",
                    separator = "\n",
                    transform = ArchitectureViolation::render
                )
            )
        }
        logger.lifecycle("Repository architecture boundaries passed.")
    }
}
