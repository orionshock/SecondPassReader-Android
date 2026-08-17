# Authenticated application shell

The authenticated UI is owned by `app.shell`. A verified `ConnectionUiState.Linked` supplies immutable connection profile and authenticated context to `AuthenticatedAppShell`; it does not transfer pairing, credential, or HTTP ownership into navigation.

The shell uses stable AndroidX Navigation 3 `1.1.6`, the current Compose-first navigation API. `AppDestination` is the closed, serializable set of top-level destinations and `AppNavigator` alone mutates the app-owned back stack. Selecting a drawer destination replaces the current top-level entry rather than accumulating duplicate roots.

```text
SecondPassApp
  unlinked -> connection flow
  linked   -> AuthenticatedAppShell
                TopAppBar
                ModalNavigationDrawer
                NavDisplay
                  Home      -> authenticated status content
                  Library   -> intentional placeholder
                  Shelves   -> intentional placeholder
                  Sessions  -> intentional placeholder
                  Settings  -> intentional placeholder
```

The modal drawer stays hidden until the app-bar menu button or edge gesture opens it. `NavDisplay` receives the full content area, without a tablet-only width cap or a separate compact-window navigation model. Destination content owns its own scrolling and future adaptive layout.

Home is now the permanent owner of the useful linked server, account, and device status. The other roots prove routing and ownership only; they must be replaced by real feature surfaces when those responsibilities exist. Reader-mode navigation, deep links, nested feature graphs, logout, and destination-specific ViewModel scoping remain deferred.

All feature-facing icons use the semantic layer documented in [Icon vocabulary](icon-vocabulary.md).
