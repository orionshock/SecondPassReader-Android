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
                  Home      -> library search, reading history, shelf previews
                  Library   -> Books browse and broad-search presentation
                  Shelves   -> intentional placeholder
                  Sessions  -> intentional placeholder
                  Settings  -> connection/account diagnostics
```

The modal drawer stays hidden until the app-bar menu button or edge gesture opens it. `NavDisplay` receives the full content area, without a tablet-only width cap or a separate compact-window navigation model. Destination content owns its own scrolling and future adaptive layout.

Home owns independent recent-reading and shelf-preview state through `HomeController`, with one loading/error/empty/loaded state and retry path per section. It resolves a credential-bound SDK client through the connection boundary, requests 10 active recent sessions and the first six shelves with three previews, and never exposes the bearer credential. Changing the closed-session toggle reloads only recent reading; server order and the `active`/`closed` vocabulary remain unchanged.

The Home presentation is cover-led: Reading History is a horizontal session rail and Shelves is a width-adaptive preview grid. Small Home-owned presentation models format status, ownership, and counts without changing SDK data or ordering. Search remains a routing intent into Library; View all and Open shelves route to their established top-level destinations.

Public cover references are passed unchanged to Coil `3.5.0`. Coil's singleton loader owns ordinary memory and disk caches and its OkHttp network component fetches public absolute HTTP(S) references without SPL authorization headers. Missing, loading, and failed images stay presentation concerns and use the semantic Library icon as fallback; `:spl-client` remains unaware of image loading and caching.

Room `3.0.1` now provides a bounded Home resilience projection. It stores account-scoped, query-specific Reading History and shelf-preview snapshots, including successful empty results and server order. This is storage foundation only: Home does not consume it yet, and the tables are not a passive local replica or shared catalog model. Atomic replacement keeps recent variants and the shelf projection independent. Coil continues to own cover bytes; Room stores only validated public references. Future true offline Library support should center on explicitly downloaded immutable books, not incidental browsing snapshots.

`HomeProjectionRepository` owns Home's local/remote arbitration boundary: `HomeController -> HomeProjectionRepository -> Room + authenticated :spl-client`. Home publishes last-known-good Room content before refreshing from the canonical server, replaces only the successfully refreshed account/query projection, and represents content independently from refresh state. Cached cards remain visible under restrained refresh progress, offline, or retry feedback. Reading History variants and Shelves refresh independently. Authentication rejection returns through the shell to connection policy without letting Home clear credentials. The repository exists for this bounded arbitration; it is not a general offline catalog mirror.

Home search emits a `LibrarySearch` intent. The shell converts it to a typed `LibrarySearchRoute`; Library initializes explicit broad-search state from that committed query and calls the distinct SDK broad-search capability. Normal Library entry initializes Books-browse state instead. Home does not call either Library API.

Library uses explicit parent/child ownership:

```text
LibraryController
  |- LibraryBooksController
  |- LibraryAuthorsController
  `- LibrarySeriesController
```

`LibraryController` owns route entry, current axis, Library scope, group-selector loading, and sibling coordination. Home broad-search entry is interpreted here before being delegated to Books. Author and Series selections enter through the parent, which retains the active axis and delegates detail loading to the owning child; a later transition into filtered Books will also be coordinated here. Children never reach sideways.

`LibraryBooksController` owns transient online Books browsing state. It translates the server's page-number contract into append-style results with an explicit page size of 50, prevents duplicate next-page requests, and discards responses made stale by mode, query, scope, or ordering changes. Initial loads, refreshes, and next-page failures remain distinct; next-page and refresh failures retain accumulated results. Library pages are deliberately not stored in Room: this is browsing state, not an offline catalog projection. `LibraryViewModel` retains the parent and its children for as long as the current navigation owner survives.

`LibraryAuthorsController` and `LibrarySeriesController` independently own their committed axis query, typed ordering, page-50 append state, retries, and selected entity detail. Both request three preview Books and preserve server order. A shared internal paging mechanism enforces the same duplicate-request and stale-response rules without coupling the children. Scope changes invalidate both axes and clear potentially invalid detail, while only the active child performs an immediate reload. Switching axes preserves valid loaded child state. Their Compose result surfaces remain intentionally deferred.

Library presents that state as a permanent Books surface with explicit-commit search, mode-specific typed sorting, compact list and adaptive cover-grid layouts, and scroll-proximity paging. Replacement loads retain the previously rendered books under restrained progress instead of flashing an empty surface. Coil loads public covers without credentials and the semantic Library icon owns missing-cover presentation. A dedicated DataStore preference owns only the device-local List/Grid choice; it does not alter requests or persist catalog pages. Saveable list/grid state retains each layout's scroll position while its navigation owner remains available.

Library scope is capability-gated exclusively by authenticated server info. When advanced library groups are enabled, the parent loads the complete name-ordered Reader-safe group summary list through `:spl-client` and presents All Library, public, and private/custom scopes with semantic icons. Scope remains parent-owned across query, ordering, axis, and Home broad-search route changes for the same connection. All Library keeps the distinct Books and global-search endpoints; a selected group delegates the group identity to Books, whose group-scoped query has broad metadata semantics. Scope changes reset transient paging but do not create a catalog cache.

The Books, Authors, and Series axes are explicit parent state. All three have concrete state owners; Books has the only rendered results surface, while Authors and Series remain intentional presentation placeholders until their dedicated Compose slice.

Shelves and Sessions still prove top-level routing only. Authors, Series, Groups, Catalog Tags, Book Detail, reader-mode navigation, deep links, nested feature graphs, and logout remain deferred.

All feature-facing icons use the semantic layer documented in [Icon vocabulary](icon-vocabulary.md).
