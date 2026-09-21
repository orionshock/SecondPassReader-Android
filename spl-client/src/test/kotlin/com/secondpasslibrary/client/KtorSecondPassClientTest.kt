package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorSecondPassClientTest {
    @Test
    fun `authenticated transport failure publishes endpoint hint without credential`() =
        runBlocking {
            val root = client { throw IOException("unreachable") }
            val hint = async(start = CoroutineStart.UNDISPATCHED) {
                root.authenticatedAccessFailures.first()
            }

            assertThrows(SplClientException.ServerUnreachable::class.java) {
                runBlocking {
                    root.loadAuthenticatedContext(
                        "https://library.example",
                        BearerCredential.restore("spl_secret")
                    )
                }
            }

            assertEquals(
                AuthenticatedAccessFailure("https://library.example"),
                hint.await()
            )
            root.close()
        }

    @Test
    fun `root close releases the shared transport once and invalidates authenticated children`() {
        var requests = 0
        val transport = HttpClient(
            MockEngine {
                requests += 1
                jsonResponse("""{"results":[]}""")
            }
        ) { expectSuccess = false }
        val root = KtorSecondPassClient(transport)
        val authenticated = root.authenticated(
            "https://library.example",
            BearerCredential.restore("spl_secret")
        )
        val transportJob = checkNotNull(transport.coroutineContext[Job])

        val result = runBlocking {
            authenticated.marginalia.sessions.recent(
                RecentReadingOptions(limit = 10, includeClosed = true)
            )
        }

        assertTrue(result.isEmpty())
        assertEquals(1, requests)
        assertTrue(transportJob.isActive)
        assertFalse(authenticated is AutoCloseable)

        root.close()
        root.close()
        runBlocking { transportJob.join() }

        assertTrue(transportJob.isCompleted)
        assertThrows(CancellationException::class.java) {
            runBlocking {
                authenticated.marginalia.sessions.recent(
                    RecentReadingOptions(limit = 10, includeClosed = true)
                )
            }
        }
    }

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

        assertEquals("https://library.example", server.serverOrigin.value)
        assertEquals(SERVER_ID, server.serverId)
        assertEquals("Second Pass Library", server.name)
        assertEquals("Books", server.description)
        assertEquals("0.1.0", server.version)
        assertEquals("2026-07-19", server.releaseDate)
        assertEquals("https://library.example", server.libraryBaseUrl)
        assertEquals("0.1", server.discoveryVersion)
        assertEquals(
            "https://library.example/api/v1/client-api/login-requests/",
            server.loginRequestUrl
        )
        assertEquals("Bearer", server.tokenType)
        assertEquals(
            listOf("/.well-known/secondpass", "/api/v1/client-api/discovery/"),
            requests.map {
                it.url.encodedPath
            }
        )
    }

    @Test
    fun `discovery derives the API root from the Library URL`() = runBlocking {
        listOf(
            "https://example.com" to "https://example.com",
            "https://example.com:8443" to "https://example.com:8443",
            "http://192.168.1.10:8080" to "http://192.168.1.10:8080"
        ).forEach { (input, baseUrl) ->
            val requests = mutableListOf<String>()
            val client = client { request ->
                requests += request.url.toString()
                when (request.url.encodedPath) {
                    "/.well-known/secondpass" -> jsonResponse(WELL_KNOWN)
                    "/api/v1/client-api/discovery/" -> jsonResponse(PAIRING_DISCOVERY)
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }

            assertEquals(baseUrl, client.discoverServer(input).libraryBaseUrl)
            assertEquals(
                listOf(
                    "$baseUrl/.well-known/secondpass",
                    "$baseUrl/api/v1/client-api/discovery/"
                ),
                requests
            )
        }
    }

    @Test
    fun `malformed well-known response is not accepted as an SPL server`() {
        val client = client { jsonResponse("""{"server_name":"Library"}""") }

        assertThrows(SplClientException.NotSecondPassServer::class.java) {
            runBlocking { client.discoverServer("https://library.example") }
        }
    }

    @Test
    fun `missing well-known endpoint is not accepted as an SPL server`() {
        val client = client { respondError(HttpStatusCode.NotFound) }

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
    fun `missing server identity fails discovery without URL fallback`() {
        val withoutServerId =
            WELL_KNOWN.replace("\"server_id\":\"$SERVER_ID\",", "")
        val client = client { jsonResponse(withoutServerId) }

        assertThrows(SplClientException.NotSecondPassServer::class.java) {
            runBlocking { client.discoverServer("https://$SERVER_ID.example") }
        }
    }

    @Test
    fun `malformed server identity fails discovery`() {
        val malformedServerId = WELL_KNOWN.replace(SERVER_ID, "library.example")
        val client = client { jsonResponse(malformedServerId) }

        assertThrows(SplClientException.NotSecondPassServer::class.java) {
            runBlocking { client.discoverServer("https://library.example") }
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
                    """
                    {
                      "server_id":"$SERVER_ID",
                      "server_urls":["https://library.example"],
                      "server_name":"Library",
                      "reading_client_base_url":null
                    }
                    """.trimIndent()
                )

                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val context =
            client.loadAuthenticatedContext(
                "https://library.example",
                BearerCredential.restore("spl_secret")
            )

        assertEquals("reader", context.currentUser.displayName)
        assertEquals("Common Room", context.currentUser.groups.single().name)
        assertNull(context.serverInfo.readingClientBaseUrl)
        assertEquals(SERVER_ID, context.serverInfo.serverId)
        assertEquals(listOf("https://library.example"), context.serverInfo.serverUrls)
        assertEquals(listOf("Bearer spl_secret", "Bearer spl_secret"), authorizationValues)
        assertEquals(
            "BearerCredential([redacted])",
            BearerCredential.restore("spl_secret").toString()
        )
    }

    @Test
    fun `authenticated server URLs retain server order and duplicates`() = runBlocking {
        val urls = listOf(
            "https://library.example:8443",
            "http://192.168.1.10:8080",
            "https://library.example:8443"
        )
        val client = client { request ->
            when (request.url.encodedPath) {
                "/api/v1/accounts/me/" -> jsonResponse("""{"username":"reader"}""")

                "/api/v1/server/info/" -> jsonResponse(
                    """{"server_id":"$SERVER_ID","server_urls":["${urls[0]}","${urls[1]}","${urls[2]}"]}"""
                )

                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val context = client.loadAuthenticatedContext(
            "https://library.example",
            BearerCredential.restore("spl_secret")
        )

        assertEquals(urls, context.serverInfo.serverUrls)
    }

    @Test
    fun `authenticated 401 becomes a revoked credential error`() {
        val client = client { respondError(HttpStatusCode.Unauthorized) }

        assertThrows(SplClientException.AuthenticationRejected::class.java) {
            runBlocking {
                client.loadAuthenticatedContext(
                    "https://library.example",
                    BearerCredential.restore("spl_secret")
                )
            }
        }
    }

    @Test
    fun `client bearer logout revokes its exact session without a request body`() = runBlocking {
        var captured: HttpRequestData? = null
        val client = client { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }

        client.revokeCurrentClientSession(
            "https://library.example",
            BearerCredential.restore("spl_secret"),
            "session-1"
        )

        assertEquals(HttpMethod.Delete, captured?.method)
        assertEquals(
            "/api/v1/accounts/me/client-sessions/session-1/",
            captured?.url?.encodedPath
        )
        assertEquals("Bearer spl_secret", captured?.headers?.get(HttpHeaders.Authorization))
        assertTrue(captured?.body is OutgoingContent.NoContent)
    }

    @Test
    fun `client bearer logout preserves session not-found distinctly`() {
        val client = client { respondError(HttpStatusCode.NotFound) }

        assertThrows(SplClientException.ClientSessionNotFound::class.java) {
            runBlocking {
                client.revokeCurrentClientSession(
                    "https://library.example",
                    BearerCredential.restore("spl_secret"),
                    "another-session"
                )
            }
        }
    }

    @Test
    fun `client bearer logout treats rejected authority as terminally invalid`() {
        listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden).forEach { status ->
            val client = client { respondError(status) }
            assertThrows(SplClientException.AuthenticationRejected::class.java) {
                runBlocking {
                    client.revokeCurrentClientSession(
                        "https://library.example",
                        BearerCredential.restore("spl_secret"),
                        "session-1"
                    )
                }
            }
        }
    }

    @Test
    fun `client bearer logout does not classify unrelated failures as not-found`() {
        listOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Conflict,
            HttpStatusCode.InternalServerError
        ).forEach { status ->
            val client = client { respondError(status) }
            assertThrows(SplClientException.ClientSessionRevocationFailed::class.java) {
                runBlocking {
                    client.revokeCurrentClientSession(
                        "https://library.example",
                        BearerCredential.restore("spl_secret"),
                        "session-1"
                    )
                }
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
        serverId = SERVER_ID,
        libraryBaseUrl = "https://library.example",
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
        const val SERVER_ID = "a6722b5a-7982-4778-8c74-39be4241a654"
        const val WELL_KNOWN =
            """{"server_id":"$SERVER_ID","server_name":"Second Pass Library","server_description":"Books","server_version":"0.1.0","server_release_date":"2026-07-19"}"""
        const val PAIRING_DISCOVERY =
            """{"discovery_version":"0.1","server_name":"Second Pass Library","server_description":"Books","login_request_endpoint":"https://library.example/api/v1/client-api/login-requests/","poll_endpoint_template":"https://library.example/api/v1/client-api/login-requests/%7Bid%7D/poll/","consume_endpoint_template":"https://library.example/api/v1/client-api/login-requests/%7Bid%7D/poll/","token_type":"Bearer"}"""
        const val LOGIN_REQUEST =
            """{"id":"request-1","code":"ABCD-EFGH","authorize_url":"https://library.example/authorize","poll_url":"https://library.example/api/v1/poll/","consume_url":"https://library.example/api/v1/consume/","expires_at":"2026-08-16T20:00:00Z","interval":3}"""
        const val CONSUMED_WITH_TOKEN =
            """{"status":"consumed","access_token":"spl_secret","token_type":"Bearer","client_session":{"id":"session-1","name":"Tablet","client_type":"second-pass-android-client"}}"""
    }
}
