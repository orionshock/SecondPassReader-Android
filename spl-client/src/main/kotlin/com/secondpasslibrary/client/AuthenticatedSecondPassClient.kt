package com.secondpasslibrary.client

interface AuthenticatedSecondPassClient {
    suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem>

    suspend fun listShelves(options: ShelfListOptions = ShelfListOptions()): ShelfPage
}

interface AuthenticatedSecondPassClientFactory {
    fun authenticated(
        apiBaseUrl: String,
        credential: BearerCredential
    ): AuthenticatedSecondPassClient
}
