# Account application shell

The account UI is owned by `app.shell`. `AccountAppShell` may render cached Home while authority is restoring or rejected; only verified authority supplies authenticated feature hosts. Pairing, credentials, and HTTP ownership remain outside navigation.

The shell uses AndroidX Navigation 3. `AppNavigationState` owns one saveable `NavBackStack` for each closed, serializable `AppDestination`; `AppNavigator` is its typed command facade. Drawer selection switches the active stack without clearing inactive stacks. Every stack remains decorated with Navigation 3's saveable-state holder and Lifecycle's ViewModel-store decorator, so an inactive entry retains saveable composition state and its entry-scoped feature ViewModel, while a popped entry clears both. Account origin plus profile ID keys the whole navigation lifetime: authority changes for the same account retain it, and account replacement discards it. Process recreation restores serializable stacks and saveable entry state, but recreates ViewModels and non-persisted controller data.

```text
SecondPassApp
  unlinked -> connection flow
  cached or verified account -> AccountAppShell
                TopAppBar
                ModalNavigationDrawer
                active retained NavDisplay
                  Home      -> library search, reading history, shelf previews
                  Library   -> Books browse and broad-search presentation
                  Shelves   -> personal, shared, and group shelf browsing
                  BookDetail -> shared app destination with typed return context
                  Marginalia -> Reading Session history browser
                  Settings  -> connection/account diagnostics
```

The modal drawer stays hidden until the app-bar menu button or edge gesture opens it. `NavDisplay` receives only the active stack's already-decorated entries; all five stacks remain decorated while inactive so Navigation 3 does not dispose their entry owners. Destination content owns its own scrolling and future adaptive layout. Verified-only destinations show the shell's connection-required entry until real authority arrives, so their feature ViewModels are not constructed from stored local identity alone.

Home owns independent recent-reading and shelf-preview state through `HomeController`, with one loading/error/empty/loaded state and retry path per section. It resolves a credential-bound SDK client through the connection boundary, requests 10 active recent sessions and the first six shelves with three previews, and never exposes the bearer credential. Changing the closed-session toggle reloads only recent reading; server order and the `active`/`closed` vocabulary remain unchanged.

The Home presentation is cover-led: Reading History is a horizontal session rail and Shelves is a width-adaptive preview grid. Small Home-owned presentation models format status, ownership, and counts without changing SDK data or ordering. Search remains a routing intent into Library; View all and Open shelves route to their established top-level destinations.

Public cover references are passed unchanged to Coil 3. Coil's singleton loader owns ordinary memory and disk caches and its OkHttp network component fetches public absolute HTTP(S) references without SPL authorization headers. Missing, loading, and failed images stay presentation concerns and use the semantic Library icon as fallback; `:spl-client` remains unaware of image loading and caching.

Room 3 provides a bounded Home resilience projection. It stores account-scoped, query-specific Reading History and shelf-preview snapshots, including successful empty results and server order. Home consumes this projection cache-first, then refreshes each section independently from the canonical server. The tables are not a passive local replica or shared catalog model. Atomic replacement keeps recent variants and the shelf projection independent. Coil continues to own cover bytes; Room stores only validated public references.

`HomeProjectionRepository` owns Home's local/remote arbitration boundary: `HomeController -> HomeProjectionRepository -> Room + authenticated :spl-client`. Home publishes last-known-good Room content before refreshing from the canonical server, replaces only the successfully refreshed account/query projection, and represents content independently from refresh state. Cached cards remain visible under restrained refresh progress, offline, or retry feedback. Reading History variants and Shelves refresh independently. Authentication rejection returns through the shell to connection policy without letting Home clear credentials. The repository exists for this bounded arbitration; it is not a general offline catalog mirror.

Home search emits a `LibrarySearch` intent. The shell converts it to a typed `LibrarySearchRoute`; Library initializes explicit broad-search state from that committed query and calls the distinct SDK broad-search capability. Normal Library entry initializes Books-browse state instead. Home does not call either Library API.

Library uses explicit parent/child ownership:

```text
LibraryController
  |- books/LibraryBooksController
  `- axis/
       `- PagedLibraryAxisController
            |- typed Author request configuration
            `- typed Series request configuration
```

`LibraryController` owns route entry, current axis, Library scope, group/tag vocabulary loading, selected Catalog Tag, and sibling coordination. Home broad-search entry is interpreted here before being delegated to Books. Author and Series selections enter through the parent, which retains the active axis, delegates detail loading to the owning child, and configures filtered Books through the Books child. Children never reach sideways; they receive only the effective tag slug.

`LibraryBooksController` owns transient online Books browsing state. It translates the server's page-number contract into append-style results with an explicit page size of 50, prevents duplicate next-page requests, and discards responses made stale by mode, query, scope, or ordering changes. Initial loads, refreshes, and next-page failures remain distinct; next-page and refresh failures retain accumulated results. Library pages are deliberately not stored in Room. Offline Library instead presents the account-scoped subset with a confirmed completed EPUB, using the best cached Home/asset metadata available and title-only local search. It does not pretend that previously viewed catalog pages form a complete offline Library. `LibraryViewModel` retains the parent and its children for as long as the current navigation owner survives.

Two typed `PagedLibraryAxisController` instances independently own the Author and Series committed query, typed ordering, page-50 append state, retries, and selected entity detail. Their typed construction configuration owns the corresponding SDK endpoint and bounded preview options without adding forwarding controller interfaces. Scope changes invalidate both axes and clear potentially invalid detail, while only the active child performs an immediate reload. Switching axes preserves valid loaded child state. Shared Library controls and vocabulary presentation live in the shallow `library.chrome` cluster; root Library files remain reserved for parent coordination, aggregate state, navigation, and state hosting.

Authors and Series render permanent dense card lists with identity, book count, and up to three public preview covers. Selecting one keeps its axis active while the Library parent coordinates its detail child and the existing Books child, filtered by the selected identity and current scope. Detail loading or failure is independent of filtered Books loading. Index result sets are list-only; selected-entity Books use the normal List/Grid presentation encoded by the parent-owned `LibraryResultState`.

Search in a selected Author or Series context stays within its filtered Books result. Returning to the axis index clears only the selection context and preserves the existing index paging state. Scope changes clear the selection before loading the new scoped index, preventing stale cross-scope detail or Books results.

Library presents that state as a permanent Books surface with explicit-commit search, mode-specific typed sorting, compact list and adaptive cover-grid layouts, and scroll-proximity paging. Replacement loads retain the previously rendered books under restrained progress instead of flashing an empty surface. Coil loads public covers without credentials and the semantic Library icon owns missing-cover presentation. A dedicated DataStore preference owns only the device-local List/Grid choice; it does not alter requests or persist catalog pages. Saveable list/grid state retains each layout's scroll position while its navigation owner remains available.

Library scope is capability-gated exclusively by authenticated server info. When advanced library groups are enabled, the parent loads the complete name-ordered Reader-safe group summary list through `:spl-client` and presents All Library, public, and private/custom scopes with semantic icons. The SDK-owned scope is resolved first, then identical Books-browse, broad-search, Authors, and Series semantics apply; endpoint topology stays inside `:spl-client`. Scope remains parent-owned across query, ordering, axis, and Home broad-search route changes. Scope changes reset transient paging but do not create a catalog cache.

The parent also loads the complete scoped Catalog Tag vocabulary as a bounded, non-persistent filter dataset. Library and Group `/tags/` responses are unqualified universes for their respective scope. Active Books, Authors, and Series result responses separately own contextual Catalog Tag aggregates for the exact axis, scope, search query, selected tag, and other result qualifiers that produced them. Page changes replace those server-provided full-population aggregates; the app never merges or derives counts from loaded page items. A selected tag composes with the current axis query and selected Author/Series Books context without changing search semantics. Tag-list failure leaves current results usable; tag changes reset child paging, and scope changes clear selection before loading the new vocabulary.

Library chrome exposes that shared filter through a compact semantic Tag control. It opens a modal sheet anchored to the right on tablets, avoiding competition with the shell's left navigation drawer. The sheet renders All tags plus the current scope's name-ordered vocabulary and visible book counts, including independent loading, empty, and retry states. Index results remain visible behind tag-vocabulary failures; the UI omits a tag rail and tag search.

Book Detail is one shared app destination rather than a Library child. The serializable `BookDetailRoute` carries a Book ID and typed return target for Library or Shelf Detail. The shell owns route entry, back-stack interpretation, and metadata handoff; `BookDetailController` owns only detail loading/retry and has no Library or Shelves controller dependency. The originating feature entry remains below Book Detail, preserving its live paging, filters, layout, and scroll state when either system or visible back pops the route. Author, Series, Tag, and Manage Shelves intents are explicit top-level transfers: the source Book Detail overlay is removed before the target stack is selected/replaced, so it cannot remain hidden on an inactive stack. Reading Sessions is different: the shell adds typed Book-scoped Marginalia above the complete originating Book Detail route on that same stack, so back restores Book Detail and its Library or Shelf origin without reconstruction. Marginalia receives only its Book history context and never creates a Reading Session while browsing history. The SDK supplies validated metadata through `library.books.getBook`; endpoint and bearer details remain internal. Read Book launches the separate Reader activity with Book and optional Session identity.

`BookShelfPickerController` is a focused Book Detail child for the cross-feature Add to Shelf workflow. It reads all personal Shelf and Book-membership pages through `authenticatedClient.shelves`, admits only editable user-owned targets, and appends without a requested position. A duplicate response becomes canonical Added state. Because add has no idempotency key, ambiguous failures trigger a membership read rather than another POST. Manage Shelves is a neutral intent interpreted by the app shell; the picker does not depend on Shelves controllers.

The Books, Authors, and Series axes are explicit parent state and all three have concrete state owners and rendered results surfaces. Selected Author/Series contexts reuse the Books child for filtered results.

Shelves has app-owned parent/child state beneath its app destination:

```text
ShelvesController
  |- collection/ (Personal, Shared, and Group collection owners)
  |- detail/ShelfDetailController
  |- management/ (create, edit, and delete owners)
  `- editor/ShelfContentsEditorController
```

The parent owns Personal/Shared/Group/Detail navigation and restores the originating collection after detail. All three collection siblings independently own page-50 append state, typed ordering, retries, 24-Book embedded preview requests, and retained scroll state; presentation chooses how many returned previews fit. Group Shelves use `scope=group` and remain read-only under Reader bearer authority. Shelf Detail independently owns Shelf metadata and normal visible-item paging, so either request may fail without destroying the other; item positions remain server values and are never normalized. This transient state is not stored in the Home Room projection.

Shelves renders permanent My Shelves, Shared by Others, and Group Shelves collections plus full-screen Shelf Detail. Cards preserve user/group ownership, listed/private visibility, editability, counts, and absent/empty/populated preview semantics without item follow-up calls. Shelf Detail reuses the app-wide compact Book list/grid presentation from `design.book`; cover bytes remain Coil-owned public assets. Selecting a Shelf Book enters the shared Book Detail route and returns to the still-live Shelf Detail and originating collection.

Personal Shelf creation is owned by a focused child beneath `ShelvesController`. It retains and validates the dialog draft, performs the SDK mutation, and preserves the draft on failure. The parent alone opens/closes the surface and refreshes only My Shelves after success; Shared by Others and Group Shelves remain independent. Duplicate names remain server-valid, and `listed` is presented distinctly from public-group ownership.

Personal Shelf container management is split into focused edit and delete state owners. Edit sends only changed mutable metadata and applies the authoritative response to the live Shelf Detail and My Shelves entry. Delete requires explicit non-idempotent confirmation, removes only the successful personal-Shelf result, and returns through the parent to the preserved My Shelves collection. Both retain Detail on failure; Shared and Group state remains read-only and untouched.

Personal Shelf contents management has a separate editor owner and consumes only the SDK editor projection, never the normal visible-item list. Available entries retain their Book metadata; unavailable retained entries expose only safe item identity and position. Relative moves remain server-aware across page boundaries. Exact positioning is presented one-based but converted to the server's zero-based value only at mutation time, and is disabled whenever unavailable entries exist. Successful moves, positioning, and removals reload canonical editor order, then reconcile normal Shelf Detail and the matching My Shelves summary without touching Shared or Group collections.

Marginalia is the permanent top-level name and owns Reading Session browsing:

```text
MarginaliaController
  |- books/MarginaliaBooksController
  |- history/ReadingSessionsController
  `- detail/ReadingSessionDetailController
       |- annotations/ReadingSessionAnnotationsController
       |- metadata/ReadingSessionMetadataEditorController
       `- close/ReadingSessionCloseController
```

The parent owns Books versus Sessions browsing, global versus Book-scoped history, Session selection, return context, and typed Book Detail/Reader navigation events. `MarginaliaViewModel` exposes only the parent state, intents, navigation events, and connection events; it does not know the child graph. History paging/filter/search state is colocated under `marginalia.history`; Book browsing lives under `marginalia.books`; detail resource state and its annotation, metadata-edit, and close-finalization children live under `marginalia.detail`. The Session list child owns explicit-commit search, active/closed filtering, and page-50 history while preserving server order; global and Book-scoped requests remain distinct SDK operations. A Book-scoped history 404 is not globally reclassified: `BookScopedReadingSessionHistoryLoader` performs the non-mutating active-session lookup, and only a successful visible-Book response with no active Session becomes valid empty history. Append paging stays in the list controller and never uses this fallback. Browsing never opens a Session.

Reading Session Detail renders Book-led metadata and progress labels without exposing raw CFIs. Its annotation child independently loads the authoritative server-ordered collection, so metadata remains usable when annotations fail. Presentation models discard protocol identities and CFI/context fields before Compose. Bookmark and Highlight variants remain discriminated; durable color tokens map through a focused design palette into thin accents and restrained tinted quote surfaces. Detail is read-only, including closed Sessions, and returning leaves the live list/filter/paging and Compose scroll state intact. Authentication rejection is surfaced to connection ownership rather than clearing credentials inside Marginalia.

Active Session metadata editing and close finalization are focused children of Reading Session Detail. Both apply the authoritative SDK response to Detail, then notify the Marginalia parent to reconcile any loaded history row; an Active-filtered row is removed after closure without reloading unrelated history. Closed Sessions expose neither mutation. Close sends no progress from this non-Reader surface, and an unreachable response locks the exact submitted name/notes for a retry-safe repeat rather than inventing idempotency or altered finalization. Authentication rejection remains connection-owned.

All feature-facing icons use the semantic layer documented in [Icon vocabulary](icon-vocabulary.md).

## Reader ownership

Reader runs in its own activity so rendering lifecycle and reading gestures do not depend on shell navigation. `ReaderViewModel` owns entry initialization, launch/progress state, navigation coordination, reconciliation, and the Android coroutine lifetime. `ReaderMarginaliaPresentationController` owns the screen-scoped annotation graph: current annotations, prior Reading Session layers, layer visibility, decorations, bookmarks, selection, mutations, highlight activation, Session metadata, authentication events, and child teardown. Its collectors are created once for that owner; changing Reader entry selects new data without adding another lifetime collector.

Readium stays behind the app-owned Reader engine contracts. The Reader domain exchanges CFIs and outcomes, while the adapter owns Readium types, WebView behavior, and the Kotlin/JavaScript CFI bridge. The machine-checked protocol and dev/release assets are documented in [Reader CFI runtime](../tools/reader-cfi-runtime/README.md).

`ReaderClosedSessionContinuationPolicy` decides which pending progress and annotations move to a writable continuation. `LocalReaderDao` reads the locked persistence snapshot and atomically applies that plan across Sessions, progress, Marginalia, outbox records, and durable continuation outcomes. Keeping this transaction deep prevents callers from learning table order or rollback mechanics; product decisions remain visible in the policy. The authoritative continuation rules live in [Offline and cached product contract](offline-product-contract.md).

## Account-local storage

`app.storage.AccountLocalDataRepository` is the common boundary for the single retained account footprint. It normalizes the account scope, supplies Library with the checksum-valid downloaded-Book catalog, and coordinates destructive cleanup across Home projections, Reader Room state and outbox, continuation outcomes, downloaded EPUBs and checksum metadata, Marginalia visibility preferences, and account-scoped Reader sync work. Feature stores stay private behind that boundary.

Repair for the same verified identity preserves this footprint. Logout, Forget, and verified replacement with a different account route through the same purge operation; device-global preferences remain. The app does not retain dormant per-account caches.
