package com.secondpasslibrary.reader.connection.storage

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.StoredCredential
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object CredentialEnvelopeCodec {
    private const val VERSION = "2"
    private const val BASE_FIELD_COUNT = 4

    fun encode(credential: BearerCredential, recoveryProfile: ConnectionProfile?): ByteArray {
        val fields =
            buildList {
                add(VERSION)
                credential.useSecret { add(it) }
                add(credential.tokenType)
                add(if (recoveryProfile == null) "0" else "1")
                recoveryProfile?.let { profile ->
                    add(profile.serverId)
                    add(profile.serverOrigin)
                    add(profile.libraryBaseUrl)
                    add(profile.serverName)
                    add(profile.serverDescription)
                    add(profile.serverVersion)
                    add(profile.serverReleaseDate)
                    add(profile.clientSessionId)
                    add(profile.clientName)
                    add(profile.clientType)
                }
            }
        return fields.joinToString(
            "\n",
            transform = ::encodeField
        ).toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(payload: ByteArray): StoredCredential {
        val fields = payload.toString(
            StandardCharsets.UTF_8
        ).lineSequence().map(::decodeField).toList()
        require(fields.size >= BASE_FIELD_COUNT && fields[0] == VERSION) {
            "Unsupported credential envelope."
        }
        val credential = BearerCredential.restore(fields[1], fields[2])
        val profile =
            when (fields[3]) {
                "0" -> null

                "1" -> {
                    require(fields.size == PROFILE_ENVELOPE_FIELD_COUNT) {
                        "Incomplete credential recovery profile."
                    }
                    ConnectionProfile(
                        serverId = fields[4],
                        serverOrigin = fields[5],
                        libraryBaseUrl = fields[6],
                        serverName = fields[7],
                        serverDescription = fields[8],
                        serverVersion = fields[9],
                        serverReleaseDate = fields[10],
                        clientSessionId = fields[11],
                        clientName = fields[12],
                        clientType = fields[13]
                    )
                }

                else -> error("Invalid credential recovery marker.")
            }
        return StoredCredential(credential, profile)
    }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            value.toByteArray(StandardCharsets.UTF_8)
        )

    private fun decodeField(value: String): String =
        Base64.getUrlDecoder().decode(value).toString(StandardCharsets.UTF_8)

    private const val PROFILE_ENVELOPE_FIELD_COUNT = 14
}
