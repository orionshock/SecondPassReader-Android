package com.secondpasslibrary.client

object SplClient {
    val identity =
        ClientIdentity(
            name = "Second Pass Library Client",
            version = "0.1.0-dev"
        )
}

data class ClientIdentity(val name: String, val version: String) {
    init {
        require(name.isNotBlank()) { "Client name must not be blank." }
        require(version.isNotBlank()) { "Client version must not be blank." }
    }
}
