package com.secondpasslibrary.reader.app

import com.secondpasslibrary.client.SplClient
import javax.inject.Inject

class ApplicationFoundationMetadata
@Inject
constructor() {
    val snapshot =
        FoundationSnapshot(
            status = "Android client foundation is ready.",
            clientName = SplClient.identity.name,
            clientVersion = SplClient.identity.version
        )
}

data class FoundationSnapshot(val status: String, val clientName: String, val clientVersion: String)
