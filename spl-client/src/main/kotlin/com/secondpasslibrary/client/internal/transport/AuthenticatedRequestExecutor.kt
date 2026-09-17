package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SplClientException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.serialization.json.Json

internal class AuthenticatedRequestExecutor(
    private val httpClient: HttpClient,
    apiBaseUrl: String,
    private val credential: BearerCredential,
    @PublishedApi internal val json: Json
) {
    private val apiBaseUrl = requireAbsoluteHttpUrl(apiBaseUrl, "authenticated connection")

    internal suspend inline fun <reified T> getDecoded(
        path: String,
        parameters: List<Pair<String, String>> = emptyList(),
        context: String
    ): T {
        val response = getResponse(path, parameters)
        requireAuthenticatedSuccess(response.status)
        return decode(response, context)
    }

    internal inline fun <reified T> decode(response: AuthenticatedResponse, context: String): T =
        json.decodeProtocolBody(response.body, context)

    suspend fun getResponse(
        path: String,
        parameters: List<Pair<String, String>> = emptyList()
    ): AuthenticatedResponse = execute {
        httpClient.get(resolveApiUrl(apiBaseUrl, path)) {
            parameters.forEach { (name, value) -> parameter(name, value) }
            credential.useSecret { token -> header(HttpHeaders.Authorization, "Bearer $token") }
        }
    }

    suspend fun downloadAuthorizedReference(url: String, destination: OutputStream) {
        val response = executeRaw {
            httpClient.get(requireSameOriginHttpUrl(url, apiBaseUrl, "book download")) {
                credential.useSecret { token -> header(HttpHeaders.Authorization, "Bearer $token") }
            }
        }
        requireAuthenticatedSuccess(response.status)
        try {
            response.bodyAsChannel().toInputStream().use { input -> input.copyTo(destination) }
        } catch (failure: IOException) {
            throw SplClientException.ServerUnreachable(failure)
        }
    }

    suspend fun post(
        path: String,
        body: String,
        idempotencyKey: String? = null
    ): AuthenticatedResponse = mutationRequest(HttpMethod.Post, path, body, idempotencyKey)

    suspend fun patch(path: String, body: String): AuthenticatedResponse =
        mutationRequest(HttpMethod.Patch, path, body)

    suspend fun put(path: String, body: String): AuthenticatedResponse =
        mutationRequest(HttpMethod.Put, path, body)

    suspend fun delete(path: String): AuthenticatedResponse =
        mutationRequest(HttpMethod.Delete, path)

    private suspend fun mutationRequest(
        method: HttpMethod,
        path: String,
        body: String? = null,
        idempotencyKey: String? = null
    ): AuthenticatedResponse = execute {
        httpClient.request(resolveApiUrl(apiBaseUrl, path)) {
            this.method = method
            idempotencyKey?.let { header("Idempotency-Key", it) }
            credential.useSecret { token -> header(HttpHeaders.Authorization, "Bearer $token") }
            body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
    }

    private suspend fun execute(block: suspend () -> HttpResponse): AuthenticatedResponse = try {
        val response = executeRaw(block)
        AuthenticatedResponse(response.status, response.bodyAsText())
    } catch (failure: IOException) {
        throw SplClientException.ServerUnreachable(failure)
    }

    private suspend fun executeRaw(block: suspend () -> HttpResponse): HttpResponse = try {
        block()
    } catch (failure: IOException) {
        throw SplClientException.ServerUnreachable(failure)
    }
}

internal data class AuthenticatedResponse(val status: io.ktor.http.HttpStatusCode, val body: String)
