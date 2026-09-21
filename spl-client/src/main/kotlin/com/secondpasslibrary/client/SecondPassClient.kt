package com.secondpasslibrary.client

interface SecondPassClient {
    suspend fun discoverServer(userInput: String): DiscoveredServer

    /** Reads public well-known identity without contacting an authenticated endpoint. */
    suspend fun identifyServer(libraryBaseUrl: String): String =
        discoverServer(libraryBaseUrl).serverId

    suspend fun beginPairing(
        server: DiscoveredServer,
        clientName: String,
        clientType: String = SplClient.ANDROID_CLIENT_TYPE
    ): PairingRequest

    suspend fun checkPairing(request: PairingRequest): PairingStatus

    suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption

    suspend fun loadAuthenticatedContext(
        libraryBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedContext
}
