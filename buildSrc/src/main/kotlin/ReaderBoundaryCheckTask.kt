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
            val isReaderFeature = READER_FEATURE_PATH in path
            val isReadiumAdapter = READIUM_ADAPTER_PATH in path
            val isConnectionFeature = CONNECTION_FEATURE_PATH in path
            val isLibraryFeature = LIBRARY_FEATURE_PATH in path
            val isConnectionPresentation = isConnectionFeature &&
                ("@Composable" in source || file.name == "ConnectionUiState.kt")
            if (isConnectionPresentation && PAIRING_PROTOCOL_MARKERS.any { it in source }) {
                violations += "$path: Connection presentation exposes pairing protocol details"
            }
            if (!isReadiumAdapter && "import org.readium." in source) {
                violations += "$path: Readium import outside the Reader adapter"
            }
            if (!isReadiumAdapter && "import android.webkit." in source) {
                violations += "$path: WebView type outside the Reader adapter"
            }
            if (!isReaderFeature && "reader.cfi.EpubCfi" in source) {
                violations += "$path: app-owned CFI contract referenced outside the Reader feature"
            }
            if (isReadiumAdapter && REFLECTION_MARKERS.any { it in source }) {
                violations += "$path: reflection is forbidden at the Readium boundary"
            }
            if (isConnectionFeature && CONNECTION_LATERAL_IMPORTS.any { it in source }) {
                violations += "$path: Connection imports feature-owned account storage"
            }
            if (isLibraryFeature && LIBRARY_LATERAL_IMPORTS.any { it in source }) {
                violations += "$path: Library imports another feature's account storage"
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
        logger.lifecycle("Reader dependency and renderer boundaries passed.")
    }

    private companion object {
        const val READER_FEATURE_PATH = "/com/secondpasslibrary/reader/reader/"
        const val READIUM_ADAPTER_PATH = "${READER_FEATURE_PATH}readium/"
        const val CONNECTION_FEATURE_PATH = "/com/secondpasslibrary/reader/connection/"
        const val LIBRARY_FEATURE_PATH = "/com/secondpasslibrary/reader/library/"
        val PAIRING_PROTOCOL_MARKERS = listOf(
            "PairingRequest", "PairingStatus", "PairingConsumption", "pollUrl", "consumeUrl"
        )
        val CONNECTION_LATERAL_IMPORTS = listOf(
            "import com.secondpasslibrary.reader.home.",
            "import com.secondpasslibrary.reader.reader."
        )
        val LIBRARY_LATERAL_IMPORTS = listOf(
            "import com.secondpasslibrary.reader.home.",
            "import com.secondpasslibrary.reader.reader."
        )
        val REFLECTION_MARKERS =
            listOf("java.lang.reflect", "getDeclaredField(", "getDeclaredMethod(")
        val FORBIDDEN_MOVEMENT_CALLS = listOf("scrollIntoView(", "window.scrollTo(")
    }
}
