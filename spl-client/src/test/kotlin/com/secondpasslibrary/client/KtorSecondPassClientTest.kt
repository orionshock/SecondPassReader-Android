package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorSecondPassClientTest {
    @Test
    fun `discovery uses well-known identity and server-provided pairing endpoint`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = client { request ->
            requests += request
            when (request.url.encodedPath) {
                "/.well-known/secondpass" -> jsonResponse(WELL_KNOWN)
                "/api/v1/client-api/discovery/" -> jsonResponse(PAIRING_DISCOVERY)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val server = client.discoverServer("https://library.example/some/random/path")

        assertEquals("Second Pass Library", server.name)
        assertEquals("https://library.example/api/v1/", server.apiBaseUrl)
        assertEquals(
            "https://library.example/api/v1/client-api/login-requests/",
            server.loginRequestUrl
        )
        assertEquals(
            listOf("/.well-known/secondpass", "/api/v1/client-api/discovery/"),
            requests.map {
                it.url.encodedPath
            }
        )
    }

    @Test
    fun `malformed well-known response is not accepted as an SPL server`() {
        val client = client { jsonResponse("""{"server_name":"Library"}""") }

        assertThrows(SplClientException.NotSecondPassServer::class.java) {
            runBlocking { client.discoverServer("https://library.example") }
        }
    }

    @Test
    fun `pairing maps concrete URLs and bounded interval`() = runBlocking {
        var requestBody = ""
        val client = client { request ->
            requestBody =
                (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            jsonResponse(LOGIN_REQUEST, HttpStatusCode.Created)
        }

        val pairing = client.beginPairing(discoveredServer(), " Tablet ")

        assertEquals("ABCD-EFGH", pairing.code)
        assertEquals(3, pairing.intervalSeconds)
        assertTrue(requestBody.contains("second-pass-android-client"))
        assertTrue(requestBody.contains("Tablet"))
    }

    @Test
    fun `poll rejects a token in the status response`() {
        val client =
            client { jsonResponse("""{"status":"approved","access_token":"wrong-place"}""") }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking { client.checkPairing(pairingRequest()) }
        }
    }

    @Test
    fun `consume distinguishes a credential from an already consumed approval`() = runBlocking {
        val issued = client {
            jsonResponse(CONSUMED_WITH_TOKEN)
        }.consumeApprovedPairing(pairingRequest())
        val repeated = client {
            jsonResponse("""{"status":"consumed"}""")
        }.consumeApprovedPairing(pairingRequest())

        assertTrue(issued is PairingConsumption.CredentialIssued)
        assertEquals(PairingConsumption.AlreadyConsumed, repeated)
        assertFalse(issued.toString().contains("spl_secret"))
    }

    @Test
    fun `consume transport failure is explicitly ambiguous`() {
        val client = client { throw IOException("connection reset") }

        assertThrows(SplClientException.AmbiguousConsumeFailure::class.java) {
            runBlocking { client.consumeApprovedPairing(pairingRequest()) }
        }
    }

    @Test
    fun `consume rejects lifecycle statuses that do not carry the one-time result`() {
        val client = client { jsonResponse("""{"status":"pending"}""") }

        assertThrows(SplClientException.AmbiguousConsumeFailure::class.java) {
            runBlocking { client.consumeApprovedPairing(pairingRequest()) }
        }
    }

    @Test
    fun `authenticated context maps sparse user and nullable server fields`() = runBlocking {
        val authorizationValues = mutableListOf<String?>()
        val client = client { request ->
            authorizationValues += request.headers[HttpHeaders.Authorization]
            when (request.url.encodedPath) {
                "/api/v1/accounts/me/" -> jsonResponse(
                    """{"username":"reader","groups":[{"name":"Common Room","is_public_group":true}]}"""
                )

                "/api/v1/server/info/" -> jsonResponse(
                    """{"server_name":"Library","reading_client_base_url":null}"""
                )

                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val context =
            client.loadAuthenticatedContext(
                "https://library.example/api/v1/",
                BearerCredential.restore("spl_secret")
            )

        assertEquals("reader", context.currentUser.displayName)
        assertEquals("Common Room", context.currentUser.groups.single().name)
        assertNull(context.serverInfo.readingClientBaseUrl)
        assertEquals(listOf("Bearer spl_secret", "Bearer spl_secret"), authorizationValues)
        assertEquals(
            "BearerCredential([redacted])",
            BearerCredential.restore("spl_secret").toString()
        )
    }

    @Test
    fun `authenticated 401 becomes a revoked credential error`() {
        val client = client { respondError(HttpStatusCode.Unauthorized) }

        assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking {
                client.loadAuthenticatedContext(
                    "https://library.example/api/v1/",
                    BearerCredential.restore("spl_secret")
                )
            }
        }
    }

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): KtorSecondPassClient = KtorSecondPassClient(
        HttpClient(MockEngine { request -> handler(request) }) {
            expectSuccess =
                false
        }
    )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun discoveredServer() = DiscoveredServer(
        serverOrigin = ServerOrigin.fromUserInput("https://library.example"),
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        name = "Library",
        description = "",
        version = "1",
        releaseDate = "",
        discoveryVersion = "0.1",
        loginRequestUrl = "https://library.example/api/v1/client-api/login-requests/",
        tokenType = "Bearer"
    )

    private fun pairingRequest() = PairingRequest(
        code = "ABCD-EFGH",
        authorizeUrl = "https://library.example/authorize",
        pollUrl = "https://library.example/api/v1/poll/",
        consumeUrl = "https://library.example/api/v1/consume/",
        expiresAt = "2026-08-16T20:00:00Z",
        intervalSeconds = 3
    )

    private companion object {
        const val WELL_KNOWN =
            """{"server_name":"Second Pass Library","server_description":"Books","server_version":"0.1.0","server_release_date":"2026-07-19","api_base_url":"https://library.example/api/v1/"}"""
        const val PAIRING_DISCOVERY =
            """{"discovery_version":"0.1","server_name":"Second Pass Library","server_description":"Books","api_base_url":"https://library.example/api/v1/","login_request_endpoint":"https://library.example/api/v1/client-api/login-requests/","poll_endpoint_template":"https://library.example/api/v1/client-api/login-requests/%7Bid%7D/poll/","consume_endpoint_template":"https://library.example/api/v1/client-api/login-requests/%7Bid%7D/poll/","token_type":"Bearer","server_base_url":"https://library.example/"}"""
        const val LOGIN_REQUEST =
            """{"id":"request-1","code":"ABCD-EFGH","authorize_url":"https://library.example/authorize","poll_url":"https://library.example/api/v1/poll/","consume_url":"https://library.example/api/v1/consume/","expires_at":"2026-08-16T20:00:00Z","interval":3}"""
        const val CONSUMED_WITH_TOKEN =
            """{"status":"consumed","access_token":"spl_secret","token_type":"Bearer","client_session":{"id":"session-1","name":"Tablet","client_type":"second-pass-android-client"}}"""
    }
}
