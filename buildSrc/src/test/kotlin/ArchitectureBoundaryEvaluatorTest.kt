import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureBoundaryEvaluatorTest {
    @Test
    fun `readium imports outside adapter are rejected`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/reader/ReaderController.kt",
            "import org.readium.r2.shared.publication.Publication"
        )

        assertEquals(listOf("reader.readium-import"), violations.map { it.rule })
    }

    @Test
    fun `connection cannot import feature-owned storage`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/connection/ConnectionCoordinator.kt",
            "import com.secondpasslibrary.reader.reader.persistence.LocalReaderStateStore"
        )

        assertEquals(listOf("connection.feature-storage"), violations.map { it.rule })
    }

    @Test
    fun `library cannot import another feature account store`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/library/LibraryController.kt",
            "import com.secondpasslibrary.reader.home.projection.HomeProjectionStore"
        )

        assertEquals(listOf("library.lateral-import"), violations.map { it.rule })
    }

    @Test
    fun `app cannot import raw authenticated transport`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/library/LibraryRemote.kt",
            "import io.ktor.client.HttpClient"
        )

        assertEquals(listOf("transport.app-import"), violations.map { it.rule })
    }

    @Test
    fun `SDK capabilities cannot expose raw authenticated responses`() {
        val violations = evaluate(
            "spl-client/src/main/kotlin/com/secondpasslibrary/client/internal/library/Books.kt",
            "import io.ktor.client.statement.HttpResponse"
        )

        assertEquals(listOf("transport.raw-response"), violations.map { it.rule })
    }

    @Test
    fun `CFI protocol declarations stay in the Readium adapter`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/reader/domain/ReaderLocation.kt",
            "val method = CfiRuntimeMethod.RESOLVE_CONTENT"
        )

        assertEquals(listOf("reader.cfi-protocol"), violations.map { it.rule })
    }

    @Test
    fun `valid adapter source produces no violations`() {
        val violations = evaluate(
            "app/src/main/kotlin/com/secondpasslibrary/reader/reader/readium/cfi/Adapter.kt",
            "import org.readium.r2.shared.publication.Publication\nval method = CfiRuntimeMethod.PARSE"
        )

        assertTrue(violations.isEmpty())
    }

    private fun evaluate(path: String, content: String): List<ArchitectureViolation> =
        ArchitectureBoundaryEvaluator.evaluate(
            listOf(ArchitectureSource(path, content, ArchitectureSourceKind.PRODUCTION))
        )
}
