package com.secondpasslibrary.reader.connection

import javax.inject.Inject
import kotlinx.coroutines.delay

fun interface PairingPollDelay {
    suspend fun wait(intervalSeconds: Long)
}

class CoroutinePairingPollDelay
@Inject
constructor() : PairingPollDelay {
    override suspend fun wait(intervalSeconds: Long) {
        delay(intervalSeconds * MILLIS_PER_SECOND)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
