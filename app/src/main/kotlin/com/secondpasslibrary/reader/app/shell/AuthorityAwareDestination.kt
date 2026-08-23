package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.secondpasslibrary.reader.app.authenticatedFeatureContext

@Composable
internal fun AuthenticatedDestination(
    environment: State<AccountDestinationEnvironment>,
    content: @Composable (AuthenticatedDestinationBindings) -> Unit
) {
    val current = environment.value
    val context = current.session.authenticatedFeatureContext
    if (context == null) {
        ConnectionRequiredDestination(
            current.session.authority,
            current.onRetryConnection,
            current.onRelinkAccount,
            current.onForgetAccount,
            current.onOpenDrawer
        )
    } else {
        content(
            AuthenticatedDestinationBindings(
                current.session.profile,
                context,
                current.navigator,
                current.onAuthenticationRejected,
                current.onOpenDrawer
            )
        )
    }
}
