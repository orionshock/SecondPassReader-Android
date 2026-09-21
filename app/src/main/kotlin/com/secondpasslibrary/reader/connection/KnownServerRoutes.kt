package com.secondpasslibrary.reader.connection

/** Operator order is canonical; the active URL is only the current transport location. */
internal data class KnownServerRoutes(
    val serverId: String,
    val serverUrls: List<String>,
    val activeLibraryBaseUrl: String
) {
    init {
        require(
            serverId.isNotBlank() && serverUrls.isNotEmpty() && activeLibraryBaseUrl.isNotBlank()
        )
    }

    companion object {
        fun initial(profile: ConnectionProfile) = KnownServerRoutes(
            profile.serverId,
            listOf(profile.libraryBaseUrl),
            profile.libraryBaseUrl
        )
    }
}

internal interface KnownServerRoutesStore {
    suspend fun read(serverId: String): KnownServerRoutes?

    suspend fun write(routes: KnownServerRoutes)

    suspend fun clear()
}

internal class KnownServerRoutesStorageException(cause: Throwable) :
    Exception("Known server routes could not be stored.", cause)
