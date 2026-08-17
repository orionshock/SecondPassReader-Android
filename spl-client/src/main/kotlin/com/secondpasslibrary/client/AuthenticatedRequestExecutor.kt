package com.secondpasslibrary.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
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
}
