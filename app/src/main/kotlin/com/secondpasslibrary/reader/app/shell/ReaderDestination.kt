package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.State
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.reader.ReaderStateHost

internal fun EntryProviderScope<NavKey>.registerReaderEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<ReaderRoute> { route ->
        val current = environment.value
        ReaderStateHost(
            profile = current.session.profile,
            profileId = current.session.profileId,
            bookId = route.bookId,
            existingSessionId = route.existingSessionId,
            titleHint = route.titleHint,
            availability = current.session.availability,
            onBack = current.navigator::goBack,
            onAuthenticationRejected = current.onAuthenticationRejected
        )
    }
}
