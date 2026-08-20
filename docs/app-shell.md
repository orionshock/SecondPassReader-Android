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
                  Shelves   -> personal, shared, and group shelf browsing
                  BookDetail -> shared app destination with typed return context
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

Library chrome exposes that shared filter through a compact semantic Tag control. It opens a modal sheet anchored to the right on tablets, avoiding competition with the shell's left navigation drawer. The sheet renders All tags plus the current scope's name-ordered vocabulary and visible book counts, including independent loading, empty, and retry states. Index results remain visible behind tag-vocabulary failures; no tag rail or tag search is currently present.

Book Detail is one shared app destination rather than a Library child. The serializable `BookDetailRoute` carries a Book ID and typed return target for Library or Shelf Detail. The shell owns route entry, back-stack interpretation, and metadata handoff; `BookDetailController` owns only detail loading/retry and has no Library or Shelves controller dependency. The originating feature entry remains below Book Detail, preserving its live paging, filters, layout, and scroll state when either system or visible back pops the route. Author, Series, and Tag actions emit neutral Book Detail navigation intents that the shell translates into typed Library routes. The SDK supplies validated metadata through `library.books.getBook`; endpoint and bearer details remain internal. Reader and sessions actions remain deferred.

`BookShelfPickerController` is a focused Book Detail child for the cross-feature Add to Shelf workflow. It reads all personal Shelf and Book-membership pages through `authenticatedClient.shelves`, admits only editable user-owned targets, and appends without a requested position. A duplicate response becomes canonical Added state. Because add has no idempotency key, ambiguous failures trigger a membership read rather than another POST. Manage Shelves is a neutral intent interpreted by the app shell; the picker does not depend on Shelves controllers.

The Books, Authors, and Series axes are explicit parent state and all three have concrete state owners and rendered results surfaces. Selected Author/Series contexts reuse the Books child for filtered results.

Shelves now has app-owned parent/child state beneath its still-placeholder destination:

```text
ShelvesController
  |- PersonalShelvesController
  |- SharedShelvesController
  |- GroupShelvesController
  |- ShelfDetailController
  `- ShelfContentsEditorController
```

The parent owns Personal/Shared/Group/Detail navigation and restores the originating collection after detail. All three collection siblings independently own page-50 append state, typed ordering, retries, three-cover preview requests, and retained scroll state. Group Shelves use `scope=group` and remain read-only under Reader bearer authority. Shelf Detail independently owns Shelf metadata and normal visible-item paging, so either request may fail without destroying the other; item positions remain server values and are never normalized. This transient state is not stored in the Home Room projection.

Shelves renders permanent read-only My Shelves, Shared by Others, and Group Shelves collections plus full-screen Shelf Detail. Cards preserve user/group ownership, listed/private visibility, editability, counts, and absent/empty/populated preview semantics without item follow-up calls. Shelf Detail reuses the app-wide compact Book list/grid presentation from `design.book`; cover bytes remain Coil-owned public assets. Selecting a Shelf Book enters the shared Book Detail route and returns to the still-live Shelf Detail and originating collection. The Sessions screen remains a placeholder. Reader-mode navigation, deep links, nested feature graphs, and logout remain deferred.

Personal Shelf creation is owned by a focused child beneath `ShelvesController`. It retains and validates the dialog draft, performs the SDK mutation, and preserves the draft on failure. The parent alone opens/closes the surface and refreshes only My Shelves after success; Shared by Others and Group Shelves remain independent. Duplicate names remain server-valid, and `listed` is presented distinctly from public-group ownership.

Personal Shelf container management is split into focused edit and delete state owners. Edit sends only changed mutable metadata and applies the authoritative response to the live Shelf Detail and My Shelves entry. Delete requires explicit non-idempotent confirmation, removes only the successful personal-Shelf result, and returns through the parent to the preserved My Shelves collection. Both retain Detail on failure; Shared and Group state remains read-only and untouched.

Personal Shelf contents management has a separate editor owner and consumes only the SDK editor projection, never the normal visible-item list. Available entries retain their Book metadata; unavailable retained entries expose only safe item identity and position. Relative moves remain server-aware across page boundaries. Exact positioning is presented one-based but converted to the server's zero-based value only at mutation time, and is disabled whenever unavailable entries exist. Successful moves, positioning, and removals reload canonical editor order, then reconcile normal Shelf Detail and the matching My Shelves summary without touching Shared or Group collections.

Marginalia has app-owned state beneath the still-placeholder Sessions destination:

```text
MarginaliaController
  |- ReadingSessionsController
  `- ReadingSessionDetailController
```

The parent owns explicit global versus Book-scoped history context, Session selection, return context, and typed future Book Detail/Reader navigation intents. The list child owns explicit-commit search, active/closed filtering, and page-50 seamless history while preserving server order; global and Book-scoped requests remain distinct SDK operations. The detail child loads Session metadata and Book context independently without coupling future annotation loading to metadata availability. Authentication rejection is surfaced to connection ownership rather than clearing credentials inside Marginalia. Entering and returning from detail leaves the live list/filter/paging state intact. No Marginalia UI is wired in this state-only slice.

All feature-facing icons use the semantic layer documented in [Icon vocabulary](icon-vocabulary.md).
