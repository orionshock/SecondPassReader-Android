package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential

interface ConnectionProfileStore {
    suspend fun read(): ConnectionProfile?

    suspend fun write(profile: ConnectionProfile)

    suspend fun clear()
}

data class StoredCredential(
    val credential: BearerCredential,
    val recoveryProfile: ConnectionProfile?
)

interface BearerCredentialStore {
    suspend fun read(): StoredCredential?

    suspend fun write(credential: BearerCredential, recoveryProfile: ConnectionProfile)

    suspend fun markProfileCommitted()

    suspend fun clear()
}

class CredentialStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class ConnectionProfileStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
