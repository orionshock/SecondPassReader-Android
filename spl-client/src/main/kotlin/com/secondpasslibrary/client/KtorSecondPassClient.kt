package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.CurrentUserWire
import com.secondpasslibrary.client.internal.PairingConsumeWire
import com.secondpasslibrary.client.internal.PairingCreateWire
import com.secondpasslibrary.client.internal.PairingDiscoveryWire
import com.secondpasslibrary.client.internal.PairingRequestWire
import com.secondpasslibrary.client.internal.PairingStatusWire
import com.secondpasslibrary.client.internal.ServerInfoWire
import com.secondpasslibrary.client.internal.WellKnownWire
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
class KtorSecondPassClient internal constructor(private val httpClient: HttpClient) :
    SecondPassClient {
    constructor() : this(defaultHttpClient())

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            exceptionsWithDebugInfo = false
        }

    override suspend fun discoverServer(userInput: String): DiscoveredServer {
        val origin = ServerOrigin.fromUserInput(userInput)
        val wellKnownResponse =
            safeRequest { httpClient.get(origin.endpoint("/.well-known/secondpass")) }
        requireDiscoverySuccess(wellKnownResponse)
        val wellKnown = decode<WellKnownWire>(wellKnownResponse, "Second Pass discovery", true)
        val name = discoveryValue { wellKnown.serverName.required("Second Pass discovery") }
        val advertisedApiBase = discoveryValue {
            requireAbsoluteHttpUrl(wellKnown.apiBaseUrl, "Second Pass discovery")
        }

        val pairingDiscoveryUrl = resolveApiUrl(advertisedApiBase, "client-api/discovery/")
        val pairingResponse = safeRequest { httpClient.get(pairingDiscoveryUrl) }
        requireDiscoverySuccess(pairingResponse)
        val pairing = decode<PairingDiscoveryWire>(pairingResponse, "client API discovery", true)
        val apiBaseUrl = discoveryValue {
            requireAbsoluteHttpUrl(pairing.apiBaseUrl, "client API discovery")
        }
        val serverBaseUrl = discoveryValue {
            requireAbsoluteHttpUrl(pairing.serverBaseUrl, "client API discovery")
        }
        val loginRequestUrl = discoveryValue {
            requireAbsoluteHttpUrl(pairing.loginRequestEndpoint, "client API discovery")
        }
        val tokenType = discoveryValue { pairing.tokenType.required("client API discovery") }
        requireDiscoveryBearer(tokenType)
        discoveryValue {
            pairing.discoveryVersion.required("client API discovery")
            pairing.serverName.required("client API discovery")
            pairing.pollEndpointTemplate.required("client API discovery")
            pairing.consumeEndpointTemplate.required("client API discovery")
        }

        return DiscoveredServer(
            serverOrigin = origin,
            serverBaseUrl = serverBaseUrl,
            apiBaseUrl = apiBaseUrl,
            name = name,
            description = wellKnown.serverDescription.orEmpty(),
            version = wellKnown.serverVersion.orEmpty(),
            releaseDate = wellKnown.serverReleaseDate.orEmpty(),
            discoveryVersion = pairing.discoveryVersion.orEmpty(),
            loginRequestUrl = loginRequestUrl,
            tokenType = "Bearer"
        )
    }

    override suspend fun beginPairing(
        server: DiscoveredServer,
        clientName: String,
        clientType: String
    ): PairingRequest {
        val (name, type) = validateClientIdentity(clientName, clientType)
        val body = json.encodeToString(
            PairingCreateWire.serializer(),
            PairingCreateWire(name, type)
        )
        val response =
            safeRequest {
                httpClient.post(server.loginRequestUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        requirePairingCreateSuccess(response)
        val wire = decode<PairingRequestWire>(response, "pairing creation")
        return wire.toPairingRequest()
    }

    override suspend fun checkPairing(request: PairingRequest): PairingStatus {
        val response = safeRequest { httpClient.get(request.pollUrl) }
        requirePairingStatusSuccess(response)
        val wire = decode<PairingStatusWire>(response, "pairing status")
        return wire.toPairingStatus()
    }

    override suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption {
        val response =
            try {
                httpClient.post(request.consumeUrl)
            } catch (failure: IOException) {
                throw SplClientException.AmbiguousConsumeFailure(failure)
            }
        requireConsumeSuccess(response)
        val wire =
            try {
                decode<PairingConsumeWire>(response, "pairing consumption")
            } catch (failure: SplClientException) {
                throw SplClientException.AmbiguousConsumeFailure(failure)
            }
        return wire.toPairingConsumption()
    }

    override suspend fun loadAuthenticatedContext(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedContext = coroutineScope {
        val validatedApiBase = requireAbsoluteHttpUrl(apiBaseUrl, "authenticated connection")
        val user =
            async {
                authenticatedGet<CurrentUserWire>(validatedApiBase, "accounts/me/", credential)
            }
        val server =
            async { authenticatedGet<ServerInfoWire>(validatedApiBase, "server/info/", credential) }
        AuthenticatedContext(user.await().toModel(), server.await().toModel())
    }

    private suspend inline fun <reified T> authenticatedGet(
        apiBaseUrl: String,
        path: String,
        credential: BearerCredential
    ): T {
        val response =
            safeRequest {
                httpClient.get(resolveApiUrl(apiBaseUrl, path)) {
                    credential.useSecret { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        requireAuthenticatedSuccess(response)
        return decode(response, "authenticated context")
    }

    private suspend fun safeRequest(block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (failure: IOException) {
        throw SplClientException.ServerUnreachable(failure)
    }

    private suspend inline fun <reified T> decode(
        response: HttpResponse,
        context: String,
        discovery: Boolean = false
    ): T = try {
        json.decodeFromString<T>(response.body<String>())
    } catch (_: SerializationException) {
        invalidResponse(discovery, context)
    } catch (_: IllegalArgumentException) {
        invalidResponse(discovery, context)
    } catch (_: IOException) {
        invalidResponse(discovery, context)
    }

    companion object {
        private fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }

        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L
    }
}
