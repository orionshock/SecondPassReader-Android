package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.KtorAuthenticatedSecondPassClient
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.CurrentUserWire
import com.secondpasslibrary.client.internal.transport.PairingConsumeWire
import com.secondpasslibrary.client.internal.transport.PairingCreateWire
import com.secondpasslibrary.client.internal.transport.PairingDiscoveryWire
import com.secondpasslibrary.client.internal.transport.PairingRequestWire
import com.secondpasslibrary.client.internal.transport.PairingStatusWire
import com.secondpasslibrary.client.internal.transport.ServerInfoWire
import com.secondpasslibrary.client.internal.transport.WellKnownWire
import com.secondpasslibrary.client.internal.transport.discoveryValue
import com.secondpasslibrary.client.internal.transport.invalidResponse
import com.secondpasslibrary.client.internal.transport.requireAbsoluteHttpUrl
import com.secondpasslibrary.client.internal.transport.requireClientSessionRevocationSuccess
import com.secondpasslibrary.client.internal.transport.requireConsumeSuccess
import com.secondpasslibrary.client.internal.transport.requireDiscoveryBearer
import com.secondpasslibrary.client.internal.transport.requireDiscoverySuccess
import com.secondpasslibrary.client.internal.transport.requirePairingCreateSuccess
import com.secondpasslibrary.client.internal.transport.requirePairingStatusSuccess
import com.secondpasslibrary.client.internal.transport.required
import com.secondpasslibrary.client.internal.transport.resolveApiUrl
import com.secondpasslibrary.client.internal.transport.splProtocolJson
import com.secondpasslibrary.client.internal.transport.toModel
import com.secondpasslibrary.client.internal.transport.toPairingConsumption
import com.secondpasslibrary.client.internal.transport.toPairingRequest
import com.secondpasslibrary.client.internal.transport.toPairingStatus
import com.secondpasslibrary.client.internal.transport.validateClientIdentity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException

class KtorSecondPassClient internal constructor(private val httpClient: HttpClient) :
    SecondPassClient,
    ClientSessionRevocationClient,
    AuthenticatedSecondPassClientFactory {
    constructor() : this(defaultHttpClient())

    private val json = splProtocolJson

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

    override fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient =
        KtorAuthenticatedSecondPassClient(httpClient, apiBaseUrl, credential)

    override suspend fun revokeCurrentClientSession(
        apiBaseUrl: String,
        credential: BearerCredential,
        clientSessionId: String
    ) {
        val requests = AuthenticatedRequestExecutor(httpClient, apiBaseUrl, credential, json)
        val response =
            requests.delete(
                "accounts/me/client-sessions/${clientSessionId.encodeURLPathPart()}/"
            )
        requireClientSessionRevocationSuccess(response)
    }

    private suspend inline fun <reified T> authenticatedGet(
        apiBaseUrl: String,
        path: String,
        credential: BearerCredential
    ): T {
        val requests = AuthenticatedRequestExecutor(httpClient, apiBaseUrl, credential, json)
        return requests.getDecoded(path, context = "authenticated context")
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
