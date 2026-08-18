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

`LibraryController` owns route entry, current axis, Library scope, group/tag vocabulary loading, selected Catalog Tag, and sibling coordination. Home broad-search entry is interpreted here before being delegated to Books. Author and Series selections enter through the parent, which retains the active axis, delegates detail loading to the owning child, and configures filtered Books through the Books child. Children never reach sideways; they receive only the effective tag slug.

`LibraryBooksController` owns transient online Books browsing state. It translates the server's page-number contract into append-style results with an explicit page size of 50, prevents duplicate next-page requests, and discards responses made stale by mode, query, scope, or ordering changes. Initial loads, refreshes, and next-page failures remain distinct; next-page and refresh failures retain accumulated results. Library pages are deliberately not stored in Room: this is browsing state, not an offline catalog projection. `LibraryViewModel` retains the parent and its children for as long as the current navigation owner survives.

`LibraryAuthorsController` and `LibrarySeriesController` independently own their committed axis query, typed ordering, page-50 append state, retries, and selected entity detail. Both request three preview Books and preserve server order. A shared internal paging mechanism enforces the same duplicate-request and stale-response rules without coupling the children. Scope changes invalidate both axes and clear potentially invalid detail, while only the active child performs an immediate reload. Switching axes preserves valid loaded child state.

Authors and Series render permanent dense card lists with identity, book count, and up to three public preview covers. Selecting one keeps its axis active while the Library parent coordinates its detail child and the existing Books child, filtered by the selected identity and current scope. Detail loading or failure is independent of filtered Books loading. Index result sets are list-only; selected-entity Books use the normal List/Grid presentation through the parent-owned `LibraryResultKind`.

Search in a selected Author or Series context stays within its filtered Books result. Returning to the axis index clears only the selection context and preserves the existing index paging state. Scope changes clear the selection before loading the new scoped index, preventing stale cross-scope detail or Books results.

Library presents that state as a permanent Books surface with explicit-commit search, mode-specific typed sorting, compact list and adaptive cover-grid layouts, and scroll-proximity paging. Replacement loads retain the previously rendered books under restrained progress instead of flashing an empty surface. Coil loads public covers without credentials and the semantic Library icon owns missing-cover presentation. A dedicated DataStore preference owns only the device-local List/Grid choice; it does not alter requests or persist catalog pages. Saveable list/grid state retains each layout's scroll position while its navigation owner remains available.

Library scope is capability-gated exclusively by authenticated server info. When advanced library groups are enabled, the parent loads the complete name-ordered Reader-safe group summary list through `:spl-client` and presents All Library, public, and private/custom scopes with semantic icons. The SDK-owned scope is resolved first, then identical Books-browse, broad-search, Authors, and Series semantics apply; endpoint topology stays inside `:spl-client`. Scope remains parent-owned across query, ordering, axis, and Home broad-search route changes. Scope changes reset transient paging but do not create a catalog cache.

The parent also loads the complete scoped Catalog Tag vocabulary as a bounded, non-persistent filter dataset. A selected tag composes with the current axis query and selected Author/Series Books context without changing search semantics. Tag-list failure leaves current results usable; tag changes reset child paging, and scope changes clear selection before loading the new vocabulary.

The Books, Authors, and Series axes are explicit parent state and all three have concrete state owners and rendered results surfaces. Selected Author/Series contexts reuse the Books child for filtered results.

Shelves and Sessions still prove top-level routing only. Catalog Tag filter UI, Book Detail, reader-mode navigation, deep links, nested feature graphs, and logout remain deferred.

All feature-facing icons use the semantic layer documented in [Icon vocabulary](icon-vocabulary.md).
