package com.secondpasslibrary.reader.connection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.live.LiveServerTest
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.readium.cfi.RealSplCfiInteropEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val REAL_SPL_CONNECTION_ARGUMENT = "connection.realSplLifecycle"

/** Opt-in proof that mutates only this debug client's server session. */
@LiveServerTest
@RunWith(AndroidJUnit4::class)
class RealSplConnectionLifecycleTest {
    @Test
    fun notFoundIsDistinctAndExactRemoteRevocationLeavesLocalData() = runBlocking {
        assumeTrue("Real SPL connection proof was not requested.", isRealProofEnabled())
        val owners = InstrumentationRegistry.getInstrumentation().targetContext.connectionOwners()
        val profile = owners.connectionProfileStore().read()
        val account = owners.persistedAccountContextStore().read()
        val credential = owners.bearerCredentialStore().read()
        assumeNotNull(profile, account, credential)
        requireNotNull(profile)
        requireNotNull(account)
        requireNotNull(credential)
        assumeTrue(account.matches(profile))
        val accountScope = ReaderAccountScope(profile.serverOrigin, account.profileId)
        val assetsBefore = owners.readerBookAssetStore().completedBooks(accountScope)
        assumeTrue("The proof requires a downloaded EPUB.", assetsBefore.isNotEmpty())

        val missing = runCatching {
            owners.clientSessionRevocationClient().revokeCurrentClientSession(
                profile.libraryBaseUrl,
                credential.credential,
                UUID.randomUUID().toString()
            )
        }
        assertTrue(missing.exceptionOrNull() is SplClientException.ClientSessionNotFound)

        owners.clientSessionRevocationClient().revokeCurrentClientSession(
            profile.libraryBaseUrl,
            credential.credential,
            profile.clientSessionId
        )

        assertTrue(owners.readerBookAssetStore().completedBooks(accountScope).isNotEmpty())
        assertTrue(owners.persistedAccountContextStore().read() == account)
    }
}

private fun isRealProofEnabled(): Boolean =
    InstrumentationRegistry.getArguments().getString(REAL_SPL_CONNECTION_ARGUMENT).toBoolean()

private fun Context.connectionOwners(): RealSplCfiInteropEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, RealSplCfiInteropEntryPoint::class.java)
