package com.secondpasslibrary.client

interface SecondPassClient {
    suspend fun discoverServer(userInput: String): DiscoveredServer

    suspend fun beginPairing(
        server: DiscoveredServer,
        clientName: String,
        clientType: String = SplClient.ANDROID_CLIENT_TYPE
    ): PairingRequest

    suspend fun checkPairing(request: PairingRequest): PairingStatus

    suspend fun consumeApprovedPairing(request: PairingRequest): PairingConsumption

    suspend fun loadAuthenticatedContext(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedContext
}
