package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import java.io.IOException

internal class AuthenticatedRequestExecutor(
    private val httpClient: HttpClient,
    apiBaseUrl: String,
    private val credential: BearerCredential
) {
    private val apiBaseUrl = requireAbsoluteHttpUrl(apiBaseUrl, "authenticated connection")

    suspend fun get(
        path: String,
        parameters: List<Pair<String, String>> = emptyList()
    ): HttpResponse {
        val response =
            try {
                httpClient.get(resolveApiUrl(apiBaseUrl, path)) {
                    parameters.forEach { (name, value) -> parameter(name, value) }
                    credential.useSecret { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            } catch (failure: IOException) {
                throw SplClientException.ServerUnreachable(failure)
            }
        requireAuthenticatedSuccess(response)
        return response
    }

    suspend fun post(path: String, body: String): HttpResponse =
        mutationRequest(HttpMethod.Post, path, body)

    suspend fun patch(path: String, body: String): HttpResponse =
        mutationRequest(HttpMethod.Patch, path, body)

    suspend fun delete(path: String): HttpResponse = mutationRequest(HttpMethod.Delete, path)

    private suspend fun mutationRequest(
        method: HttpMethod,
        path: String,
        body: String? = null
    ): HttpResponse = try {
        httpClient.request(resolveApiUrl(apiBaseUrl, path)) {
            this.method = method
            credential.useSecret { token -> header(HttpHeaders.Authorization, "Bearer $token") }
            body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
    } catch (failure: IOException) {
        throw SplClientException.ServerUnreachable(failure)
    }
}
