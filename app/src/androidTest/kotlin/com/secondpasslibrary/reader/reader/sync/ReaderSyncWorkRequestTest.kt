package com.secondpasslibrary.reader.reader.sync

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSyncWorkRequestTest {
    @Test
    fun requestIsAccountScopedConnectedAndExponentiallyBackedOff() {
        val account = LocalReaderAccountKey.from("https://library.example", "profile-1")

        val request = readerSyncWorkRequest(account)

        assertEquals(account.value, request.workSpec.input.getString(INPUT_ACCOUNT_KEY))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(
            TimeUnit.SECONDS.toMillis(READER_SYNC_BACKOFF_SECONDS),
            request.workSpec.backoffDelayDuration
        )
        assertTrue(request.tags.contains(workName(account)))
    }
}
