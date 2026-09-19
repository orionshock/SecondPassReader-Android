package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SplClientException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedRequestExecutorTest {
    @Test
    fun `authenticated get attaches bearer and decodes a typed body`() = runBlocking {
        val requests = executor { request ->
            assertEquals("Bearer spl_secret", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/example/", request.url.encodedPath)
            assertEquals("value", request.url.parameters["filter"])
            respondJson("""{"id":"result-1"}""")
        }

        val result = requests.getDecoded<ResultWire>(
            "example/",
            listOf("filter" to "value"),
            "executor test"
        )

        assertEquals("result-1", result.id)
    }

    @Test
    fun `empty-body success remains available to operation status policy`() = runBlocking {
        val response = executor {
            respond("", HttpStatusCode.NoContent)
        }.delete("example/")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.body)
    }

    @Test
    fun `malformed JSON has safe decode context without response data`() {
        val requests = executor { respondJson("{ secret-response") }

        val failure = assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { requests.getDecoded<ResultWire>("example/", context = "executor test") }
        }

        assertEquals("The server returned an invalid executor test response.", failure.message)
        assertFalse(failure.toString().contains("secret-response"))
    }

    @Test
    fun `common authentication failure is classified before decode`() {
        val requests = executor {
            respondJson("""{"access_token":"server-secret"}""", HttpStatusCode.Unauthorized)
        }

        val failure = assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking { requests.getDecoded<ResultWire>("example/", context = "executor test") }
        }

        assertFalse(failure.toString().contains("server-secret"))
        assertFalse(failure.toString().contains("spl_secret"))
    }

    @Test
    fun `transport failure is normalized without credential disclosure`() {
        val requests = executor { throw IOException("spl_secret network detail") }

        val failure = assertThrows(SplClientException.ServerUnreachable::class.java) {
            runBlocking { requests.getDecoded<ResultWire>("example/", context = "executor test") }
        }

        assertEquals("The server could not be reached.", failure.message)
        assertFalse(failure.toString().contains("spl_secret"))
    }

    @Test
    fun `only transport loss emits authenticated reachability hint`() = runBlocking {
        val hints = mutableListOf<String>()
        val unreachable = executor(hints::add) { throw IOException("connection refused") }
        assertThrows(SplClientException.ServerUnreachable::class.java) {
            runBlocking { unreachable.getResponse("example/") }
        }
        assertEquals(listOf("https://library.example/api/v1/"), hints)

        val serverError = executor(hints::add) {
            respond("", HttpStatusCode.InternalServerError)
        }
        assertEquals(HttpStatusCode.InternalServerError, serverError.getResponse("example/").status)
        assertEquals(1, hints.size)
    }

    @Test
    fun `authorized download rejects another origin before transport`() {
        var requestCount = 0
        val requests = executor {
            requestCount += 1
            respond("book")
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                requests.downloadAuthorizedReference(
                    "https://assets.example/book.epub",
                    ByteArrayOutputStream()
                )
            }
        }
        assertEquals(0, requestCount)
    }

    @Test
    fun `operation-specific status remains distinguishable`() = runBlocking {
        val requests = executor {
            respondJson("""{"code":"OPERATION_CONFLICT"}""", HttpStatusCode.Conflict)
        }

        val response = requests.getResponse("example/")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.body.contains("OPERATION_CONFLICT"))
    }

    private fun executor(
        onUnreachable: (String) -> Unit = {},
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData
        ) -> io.ktor.client.request.HttpResponseData
    ): AuthenticatedRequestExecutor = AuthenticatedRequestExecutor(
        HttpClient(MockEngine(handler)) { expectSuccess = false },
        "https://library.example/api/v1/",
        BearerCredential.restore("spl_secret"),
        splProtocolJson,
        onUnreachable
    )

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) = respond(
        body,
        status,
        headersOf(HttpHeaders.ContentType, "application/json")
    )

    @Serializable
    private data class ResultWire(val id: String)
}
