package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.State
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.reader.ReaderStateHost

internal fun EntryProviderScope<NavKey>.registerReaderEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<ReaderRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            val current = environment.value
            ReaderStateHost(
                profile = bindings.profile,
                profileId = current.session.profileId,
                bookId = route.bookId,
                onBack = bindings.navigator::goBack,
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
}
