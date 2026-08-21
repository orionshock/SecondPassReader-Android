# Repository maintenance audit

Date: 2026-08-20

Scope: read-only architecture, topology, naming, and responsibility review of `:app` and
`:spl-client`. Production behavior was not changed. The approximate 300-line threshold is used
only as an inspection trigger.

## 1. Executive assessment

The repository has a sound architectural spine. `:app` depends on `:spl-client`, never the
reverse. App code does not know Ktor, endpoint paths, bearer headers, or wire DTOs. The SDK's
public authenticated hierarchy (`library`, `shelves`, `marginalia`) is already understandable to
a consumer without endpoint knowledge. App features usually follow the intended parent/child
shape, ViewModels are mostly thin facades, Compose is presentation-only, and server-authoritative
mutation reconciliation is explicit. Home's Room projection is particularly well bounded: it is
a real local/remote arbitration owner, not a catalog repository in disguise.

Entropy is appearing at the seams around those owners rather than inside their domain logic:

- the single app navigation stack destroys top-level entries when switching destinations;
- authenticated connection identity is reconstructed as delimiter-joined strings throughout the
  app;
- Library, Shelves, and Marginalia have grown into flat packages whose state, presentation, and
  child-owner files are harder to distinguish in search results;
- the SDK keeps internal Ktor clients and mappers in the same root package as its public surface;
- a few formerly useful intermediate names (`LibraryEntity`, `AuthenticatedRead`,
  `LibraryWireDecoder`) no longer describe the concepts they contain.

The highest risk before Reader work is navigation/state lifetime. Library and Shelf state survives
when Book Detail is pushed above the live origin, but drawer navigation calls
`AppNavigator.select`, clears the stack, and removes that feature entry. A Reader will add longer
lived state, resources, and deeper return paths; its ownership cannot safely depend on an
accidental route-entry lifetime.

The next risk is reset-policy drift. At least fifteen controllers independently construct an
authenticated identity using `apiBaseUrl`, `clientSessionId`, and `\u0000`; Home and Library add
different extra fields. This currently works, but it makes every new state owner responsible for
knowing which connection changes invalidate it.

## 2. Current topology assessment

### App shell

`SecondPassApp` observes `ConnectionViewModel` and chooses linked versus unlinked content.
`AuthenticatedAppShell` owns the drawer, scaffold, and one Navigation 3 back stack.
`AuthenticatedDestinations` is the common coordinator that translates feature-neutral intents
into app routes. `AppNavigator` performs stack mutations. Shared Book Detail and Book-scoped
Marginalia are pushed above their origin, so back can reveal the still-live origin entry.

Healthy:

- Cross-feature routing is centralized in `app.shell`; Book Detail does not reach into Library,
  Shelves, or Marginalia controllers.
- `BookDetailRoute` and its return targets are typed.
- Activity and Application classes remain minimal.

Entropy:

- `SecondPassApp.kt` also contains the complete connection presentation: entry, verification,
  waiting, recovery, errors, and common connection layout. The root application file therefore
  has two owners.
- `AppNavigator.select`, `openLibrarySearch`, and `replaceWith` clear the only back stack. Top-level
  state preservation is therefore conditional on a route remaining below a pushed detail route,
  not an explicit shell policy.
- `AuthenticatedAppShell` decides app-bar ownership with route-type checks. Three exceptions are
  manageable now, but Reader will make this an expanding conditional unless chrome ownership is
  represented by route metadata/policy.
- `AppDestination.kt` is a complete route catalog, not merely the `AppDestination` enum.

### Connection

The control flow is `SecondPassApp -> ConnectionViewModel -> ConnectionCoordinator -> SDK +
ConnectionProfileStore + BearerCredentialStore`. `ConnectionCoordinator` owns discovery,
pairing/polling, exactly-once consume handling, durable secret/profile ordering, restore, and
authenticated verification. Its sealed `ConnectionUiState` makes the workflow explicit.

Healthy:

- Credential and profile persistence are separate boundaries.
- Keystore/DataStore implementations are already localized under `connection.storage`.
- Exactly-once token ambiguity and durable-storage recovery are represented rather than hidden.
- The ViewModel delegates instead of absorbing the state machine.

Entropy:

- Connection UI lives in `app/SecondPassApp.kt`, not beside the state it renders.
- `ConnectionModule.kt` contains both `ConnectionStorageModule` and `SplClientModule`; the filename
  does not identify both DI responsibilities.
- Authenticated feature reset identity is not a connection-owned value, causing repeated string
  construction outside this package.

### Home

The read path is:

```text
AuthenticatedHome -> HomeViewModel -> HomeController
                  -> HomeProjectionRepository
                  -> HomeProjectionStore (Room) + AuthenticatedClientProvider
                  -> :spl-client marginalia.sessions.recent / shelves.list
```

The repository emits cached content, then refreshing state, then current or failed refresh state.
Reading History and Shelves use independent jobs and projection variants. Server responses live in
controller memory for the ViewModel lifetime and in bounded Room snapshots across process death;
cover bytes remain Coil-owned.

Healthy:

- `HomeProjectionRepository` earns the Repository suffix through source arbitration and atomic
  last-known-good replacement.
- `HomeProjectionState` separates content availability from refresh condition and represents a
  successful empty cache.
- `home.projection` is a useful, shallow subpackage with clear Room ownership.
- Cache scope excludes bearer/client-session identity, allowing the same server/account projection
  to survive re-pairing.

Entropy:

- `AuthenticatedHome` is now a permanent Home screen; authentication is no longer a useful name
  discriminator.
- `HomeProjectionEntities` retains `ownerFirstName`, `ownerLastName`, and `ownerOtherType`, while
  current mapping/presentation no longer uses those values. They are schema residue, not an urgent
  deletion because a Room migration would be required.

### Library

The parent/child execution path is real, not diagram-only:

```text
LibraryStateHost -> LibraryViewModel -> LibraryController
                                      |- LibraryBooksController
                                      |- LibraryAuthorsController
                                      `- LibrarySeriesController
```

The parent owns axis, scope, shared tag selection, bounded group/tag vocabulary loading, route
entry, and Author/Series-to-Books handoff. Books owns mode, filters, paging, ordering, layout
preference, stale-response generations, and restoration of the prior unfiltered result. Authors
and Series are semantic facades over one generic index/detail state machine. No child reaches
sideways.

Healthy:

- The parent coordinates siblings and selected-entity Books correctly.
- Scope-first SDK semantics remain hidden from Compose.
- All three paging owners suppress duplicate requests and reject stale responses.
- Presentation availability (`LibraryResultKind`) is parent-owned instead of scattered.

Entropy:

- Twenty-nine production files are in one `library` package. Parent coordination, Books, the
  Author/Series index family, chrome, feedback, and presentation all appear together in `rg`
  results.
- `LibraryController` also runs two independent vocabulary loaders. This is parent-authorized work,
  but the jobs and paging-all loops now form a separable filter-vocabulary child responsibility.
- The `LibraryEntity*` family hides that it exists specifically for Author/Series index and detail
  behavior. “Entity” is both vague and search-noisy.
- `LibraryBookPresentation.kt` combines a duplicate compact-Book presentation model, ordering-menu
  policy, and the seamless-paging trigger. `LibraryBookCards` immediately adapts that duplicate
  model back into `design.book.CompactBookPresentation`.
- `LibraryScreen`/`LibraryContent` pass a large callback list through multiple layers. Compose is
  not making domain decisions, but the callback chain obscures which intents are parent versus
  child owned.

### Book Detail

Shared Book Detail has one controller/screen and is correctly app-level. Its controller owns only
detail loading. The shelf picker is a focused sibling owned by the Book Detail ViewModel; it loads
all editable personal targets, reconciles ambiguous non-idempotent adds by rereading membership,
and emits no Shelves-controller dependency.

Healthy:

- One shared destination serves Library and Shelf origins.
- Metadata navigation is emitted as neutral intents and interpreted by the shell.
- Add-to-Shelf ambiguity handling is correctly isolated in `BookShelfPickerController`.

Entropy:

- Five `BookShelfPicker*` files now form a clear child cluster inside an otherwise cohesive flat
  package. A single `bookdetail.shelfpicker` subpackage would improve locality without fragmenting
  Book Detail itself.
- `BookDetailViewModel` is the de facto small parent for detail and shelf picker. This is acceptable
  screen coordination; introducing a ceremonial `BookDetailParentController` now would not help.

### Shelves

The execution path is:

```text
ShelvesStateHost -> ShelvesViewModel -> ShelvesController
                                     |- personal/shared/group collection owners
                                     |- ShelfDetailController
                                     |- create/edit/delete owners
                                     `- ShelfContentsEditorController
```

The parent owns navigation and mutation reconciliation. Collections page independently. Detail
loads metadata and visible items independently. The editor consumes the safe editor projection and
reconciles authoritative detail/My Shelves state through a narrow callback.

Healthy:

- Personal, Shared, and Group are independent state owners, not one conditional controller.
- Mutation owners are focused and server-authoritative responses flow through the parent.
- Editor placeholders preserve the unavailable-item privacy boundary.
- Shared and Group collections are not mutated as a side effect of personal changes.

Entropy:

- This is the largest flat feature package: thirty-one files spanning collections, detail,
  management dialogs/controllers, contents editing, presentation, and parent navigation.
- `ShelvesState.kt` is a smaller but more important topology problem than several large files: it
  contains collection, detail, editor, create, error, navigation, and feature aggregate state.
  `ShelfManagementState.kt` separately holds edit/delete state, leaving create state in the grab
  bag.
- `ShelvesViewModel` exposes many child methods as one flat facade. It remains delegation-only, but
  its method list makes search results less useful and will keep growing with management features.

### Marginalia

`MarginaliaController` owns global versus Book history, Session selection, and return context.
`ReadingSessionsController` owns filters/search/paging. `ReadingSessionDetailController` owns
metadata detail and contains independent annotation, metadata-edit, and close children. The
Book-history loader narrowly handles the server's unlinked-visible-Book 404 by performing a
non-mutating active-session lookup.

Healthy:

- History browsing cannot create a Reading Session.
- Metadata, annotations, edit, and close states fail independently.
- Active/closed vocabulary and server order are preserved.
- The special empty-history fallback is narrow and contract-specific.

Entropy:

- `MarginaliaState.kt` contains nearly every state, intent, failure, navigation, and constant for
  history and detail children. Search finds the package rather than the owner.
- `ReadingSessionsController` includes generic paging plus the distinct Book-scoped fallback
  workflow. The fallback is sufficiently contractual and independently tested to merit a named
  loader/policy extraction.
- `ReadingSessionDetailController.onAuthoritativeUpdate` is a publicly mutable callback installed
  by the parent. The parent is coordinating correctly, but a typed child event or constructor sink
  would make the ownership and lifetime explicit.

### Design and shared presentation

`design.icons`, `design.book`, `design.marginalia`, and `design.components` are appropriately small
and semantic. The icon vocabulary keeps raw Material symbols out of features. `design.book` is a
genuine shared presentation seam used by Library and Shelves.

The main exception is Library's parallel `LibraryBookPresentation`/`LibraryBookCards` adapter,
which duplicates `design.book` before converting back to it. Home's cover/card models should remain
Home-owned because their overlay/status semantics differ from generic compact Book cards.

### `:spl-client`

Public control flow is coherent:

```text
AuthenticatedSecondPassClient
  |- library -> books/authors/series/groups/tags
  |- shelves
  `- marginalia -> books/sessions
```

Ktor clients build endpoint-specific requests, `AuthenticatedRequestExecutor` owns URL resolution
and bearer attachment, internal wire DTOs decode with strict mappers, and public models contain no
Ktor or Android types. App code was searched for Ktor, headers, and endpoint literals; none leaked.

Healthy:

- The public hierarchy is workflow-shaped and publishable in substance.
- Wire DTOs are internal and discriminated public models preserve Shelf ownership/editor entries
  and annotation kinds.
- URL/credential types prevent casual secret and endpoint leakage.
- Library scope selects endpoint topology internally.

Entropy:

- Most internal Ktor clients, mapping files, response interpreters, and public interfaces/models
  share `com.secondpasslibrary.client`. Only wire DTOs are under `internal`. Package browsing does
  not distinguish SDK surface from implementation.
- Public child method naming is inconsistent: `books.getBook`, `authors.getAuthor`,
  `series.getSeries`, and `groups.listGroups` coexist with receiver-qualified `get`/`list` on Tags,
  Marginalia, and Shelves.
- `LibraryWireDecoder.decodeLibrary` decodes Library, Shelves, Marginalia, and lifecycle responses;
  its name is stale.
- `AuthenticatedReadMapping` and `AuthenticatedReadWireModels` now mean Recent Reading only, after
  Recent Reading moved under Marginalia.
- `LibraryModels.kt` is a useful public catalog but now spans queries, ordering, paging, Books,
  Author/Series previews, detail, files, groups, and exact value types. Splitting it by public domain
  concept within the same package would improve discovery without inventing new abstractions.

## 3. Concrete organization proposal

These are targeted clusters, not a universal package template.

| Area | Current problem | Proposed clustering and renames | Deliberately remain flat / benefit |
| --- | --- | --- | --- |
| App root/connection | `SecondPassApp.kt` owns root selection and all connection UI | Keep `SecondPassApp.kt` as the root; move presentation to `connection/ConnectionScreen.kt`, `ConnectionEntryContent.kt`, `ConnectionPairingContent.kt`, and `ConnectionRecoveryContent.kt` only where each extraction is cohesive | Keep `ConnectionCoordinator` and `ConnectionUiState` together in `connection`; this makes `rg Connection` find the complete vertical slice |
| App shell | One file named for one enum contains all routes | Rename `AppDestination.kt` to `AppRoutes.kt`; add an explicit route chrome policy before Reader instead of more `is Route` checks | Keep four shell files in `app.shell`; another navigation package level would add no value |
| Home | One stale screen name; projection topology is already clear | `AuthenticatedHome.kt` -> `HomeScreen.kt`; leave `home.projection` unchanged | Keep Home's non-Room files flat; twenty focused files split cleanly at the existing projection boundary |
| Library | Twenty-nine unrelated-search-result files in one package | Keep parent files in `library`; add shallow `library.books`, `library.axis`, and `library.chrome`. Rename `LibraryEntityController` -> `PagedLibraryAxisController`; rename UI files to `LibraryAuthorSeriesCards/Results/Presentation`. Extract `LibraryFilterVocabularyController` only if done with behavior tests | Do not create separate `authors` and `series` packages around two thin facades; their shared axis implementation is the meaningful locality |
| Book Detail | Shelf picker is a five-file child cluster | Move `BookShelfPicker*` to `bookdetail.shelfpicker`; keep controller/screen/hero/presentation/navigation at `bookdetail` root | Do not create a parent controller solely for symmetry |
| Shelves | Thirty-one flat files and a state grab bag | Keep `ShelvesController`, navigation/state host, and root screen in `shelves`; add `shelves.collection`, `shelves.detail`, `shelves.management`, and `shelves.editor`. Move each state/error beside its controller. Put create/edit/delete state consistently in `management` | Keep the three collection subclasses together with their shared collection controller; separate per-scope packages would make navigation worse |
| Marginalia | History, detail, annotations, and mutations are flat; one state file contains all | Keep parent/nav/state host in `marginalia`; add `marginalia.history` and `marginalia.detail`. Keep annotation/edit/close children in `detail` until Reader creates a real reusable annotation boundary. Extract `BookScopedHistoryLoader` from the list controller | Do not create a top-level annotations feature; annotations are Session children |
| Design | Library duplicates shared compact Book presentation | Remove the Library-only compact Book adapter and use `design.book` directly. Split ordering labels and paging trigger into `LibraryOrderingPresentation.kt` and `LibraryPagingTriggerPolicy.kt` | Keep semantic icons, public covers, compact cards, and annotation colors in their current shallow design packages |
| SDK public | One root package contains all public models plus internal implementation | Keep `SplClient`, factories, root authenticated client, and `SplClientException` at the root. Before publication, consider public `connection`, `library`, `shelves`, and `marginalia` packages if import migration is acceptable | Do not introduce a generic global page model; Library, Shelves, and Marginalia have meaningfully different envelopes |
| SDK internal | Ktor clients and mappers sit beside public API | Move implementation to `client.internal.transport`, `.library`, `.shelves`, and `.marginalia`; keep wire DTOs with their subsystem. Rename `decodeLibrary` -> `decodeProtocolBody`, `AuthenticatedReadMapping` -> `RecentReadingMapping`, and its wire file likewise | This is an internal-only move and gives `rg Ktor`/package browsing a clear implementation boundary |

## 4. Responsibility findings

### High

#### H1. Top-level navigation state lifetime is destructive, not explicit

- Files/classes: `AppNavigator`, `AuthenticatedAppShell`, `AuthenticatedDestinations`, all
  feature `StateHost`/ViewModel pairs.
- Evidence: `select`, `openLibrarySearch`, and `replaceWith` call `backStack.clear()`. Feature
  controllers are constructed by ViewModels inside destination entries. State is preserved during
  Book Detail round trips only because the origin entry remains below the pushed route.
- Problem: drawer switching can discard paging, query, filters, scroll ownership, and future Reader
  resources. The behavior differs depending on how a destination was left.
- Direction: define top-level state-retention policy before Reader—stable top-level entries/back
  stacks or shell-scoped feature state—and test route-entry lifetime. Continue pushing shared
  detail routes above a live origin.
- Behavior-preserving: no; this intentionally improves state retention and needs focused runtime
  verification.

#### H2. Authenticated identity/reset policy is duplicated as raw strings

- Files/classes: Home, Library, Shelves, Marginalia, Book Detail controllers and mutation children;
  at least fifteen `connectionIdentity`/entry-key constructions.
- Evidence: repeated `"${profile.apiBaseUrl}\u0000${profile.clientSessionId}"`; Home also adds
  profile ID, Library adds capability/entry fields.
- Problem: a controller can accidentally omit a credential/session change or conflate connection
  identity with request identity. New Reader children would copy the pattern again.
- Direction: add a typed app-owned `AuthenticatedConnectionIdentity` derived once from
  `ConnectionProfile`; keep feature request keys (`mode`, query, scope, tag) separate and typed or
  feature-local.
- Behavior-preserving: yes, and narrow tests can freeze current invalidation behavior.

### Medium

#### M1. `SecondPassApp` owns both application root and connection presentation

- Files/classes: `SecondPassApp.kt`, `ConnectionUiState`, `ConnectionViewModel`.
- Evidence: the root file dispatches every connection state and implements entry, waiting,
  recovery, problem, and busy Composables.
- Problem: app-shell searches return connection screen details, and connection changes require
  editing the root application owner.
- Direction: move the state host/screen and focused content into `connection`; leave root linked
  versus unlinked selection in `SecondPassApp`.
- Behavior-preserving: yes.

#### M2. Feature packages no longer expose their parent/child topology

- Files/classes: all files under `library`, `shelves`, and `marginalia`, especially
  `ShelvesState.kt` and `MarginaliaState.kt`.
- Evidence: 29 Library, 31 Shelves, and 19 Marginalia production files are flat; aggregate state
  files mix independently owned child states and errors.
- Problem: filenames remain individually reasonable, but directory and `rg` output cannot show
  collection/detail/management or history/detail boundaries.
- Direction: apply only the shallow clusters in section 3, moving tests with owners.
- Behavior-preserving: yes if performed package-by-package without simultaneous redesign.

#### M3. Library parent has acquired an independent vocabulary-loading state machine

- Files/classes: `LibraryController`, `LibraryGroupSelectorState`, `LibraryTagSelectorState`,
  SDK group/tag capabilities.
- Evidence: parent owns `groupsJob`, `tagsJob`, load-all loops, stale-scope checks, retry, errors,
  and pending tag-route resolution in addition to cross-child coordination.
- Problem: all work is authorized at the parent, but bounded vocabulary loading obscures axis and
  scope coordination in a 390-line controller.
- Direction: extract a `LibraryFilterVocabularyController` that loads scoped group/tag vocabulary
  and emits state; parent retains scope, selected tag, route resolution, and child invalidation.
- Behavior-preserving: likely, with existing axis/controller tests split around the new boundary.

#### M4. Library duplicates the shared compact Book presentation seam

- Files/classes: `LibraryBookPresentation.kt`, `LibraryBookCards.kt`, `LibraryBookCover.kt`,
  `design.book.CompactBookPresentation/Cards/PublicBookCover`.
- Evidence: Library maps `CompactBook` to `LibraryBookPresentation`, then immediately maps it to
  `CompactBookPresentation`; Shelves already uses the shared presentation directly. The same file
  also owns ordering options and paging-trigger policy.
- Problem: one feature-local representation has no distinct presentation behavior and the filename
  conceals ordering/paging decisions.
- Direction: consume `design.book` directly; retain only genuinely Library-specific ordering and
  paging policy in precisely named files.
- Behavior-preserving: yes.

#### M5. Combined state is implemented through duplicated experimental `StateFlow` inheritance

- Files/classes: `LibraryStateFlow`, `ShelvesStateFlow`.
- Evidence: both manually implement `StateFlow`, opt into `ExperimentalForInheritanceCoroutinesApi`,
  expose a computed `value`, and separately combine flows for collectors.
- Problem: the `value` snapshot and collected snapshot have different construction paths, and the
  pattern must be recopied for the next parent with several children.
- Direction: use ordinary `combine(...).stateIn(...)` owned by the parent scope, or expose parent
  plus child flows separately if an aggregate is not required. Do not create a universal feature
  state framework.
- Behavior-preserving: likely; verify initial synchronous `value` expectations in tests.

#### M6. SDK implementation and public surface share one package

- Files/classes: `Ktor*Client`, `*Mapping`, `*Response`, `AuthenticatedRequestExecutor`, public
  interfaces/models.
- Evidence: only wire DTOs are currently under `internal`; package browsing at the root interleaves
  transport adapters with the publishable API.
- Problem: publication review and `rg` results require callers to mentally filter implementation.
- Direction: move internal implementation by subsystem as proposed in section 3; make a separate
  decision before changing public model packages.
- Behavior-preserving: yes for internal moves.

#### M7. Session-detail reconciliation uses a mutable callback slot

- Files/classes: `MarginaliaController`, `ReadingSessionDetailController`,
  `ReadingSessionsController.authoritativeDetailSink`.
- Evidence: the parent assigns `detail.onAuthoritativeUpdate` during initialization and nulls it on
  close; successful edit/close invokes the callback to reconcile the list.
- Problem: the common owner is correct, but wiring and lifecycle are mutable and implicit.
- Direction: inject a narrow authoritative-update sink when constructing the detail child, or emit
  a typed child event the parent collects. Keep reconciliation in the parent/list owner.
- Behavior-preserving: yes.

### Low

#### L1. Several names are stale or overly generic

- Files/classes: `AuthenticatedHome`, `AppDestination.kt`, `LibraryEntity*`,
  `LibraryWireDecoder`, `AuthenticatedRead*`, `ConnectionModule.kt`.
- Evidence: each name describes an old implementation stage or only one of several contents.
- Direction: use the concrete renames in sections 3 and 6.
- Behavior-preserving: yes.

#### L2. Room Home shelf columns contain unused historical owner fields

- Files/classes: `HomeShelfEntity`, `HomeProjectionMapping`.
- Evidence: three persisted owner fields are written/read without contributing to the current
  public `ShelfSummary`/presentation.
- Direction: remove only as a deliberate Room migration, not as incidental cleanup.
- Behavior-preserving: functionally yes, structurally requires schema migration testing.

#### L3. Read-failure mapping is repeated but not yet worth a generic framework

- Files/classes: `LibraryFailureMapping`, `ShelvesFailureMapping`,
  `MarginaliaFailureMapping`, Home/Book Detail mappings.
- Evidence: the same transport/auth/protocol categories are translated repeatedly, with
  feature-specific result enums and mutation exceptions layered separately.
- Direction: first colocate mappings with the reorganized owners. Extract a small shared
  classifier only if Reader supplies another concrete consumer and semantics remain identical.
- Behavior-preserving: yes, but defer the abstraction.

## 5. Approximate 300-line file review

| File | Lines | Class | Assessment |
| --- | ---: | :---: | --- |
| `connection/ConnectionCoordinator.kt` | 413 | A | One explicit connection/pairing/restore state machine. Splitting its transitions would make exactly-once and persistence ordering harder to audit. Keep intact. |
| `library/LibraryController.kt` | 390 | B | Parent coordination is cohesive, but group/tag vocabulary loading is now one small extractable child state machine. |
| `library/LibraryBooksController.kt` | 348 | A | One Books state machine covering mode/filter/scope/paging/stale responses and layout persistence. `unfilteredState` is part of Author/Series return semantics, not a second owner. Keep intact for now. |
| `shelves/editor/ShelfContentsEditorController.kt` | 329 | A | One canonical editor workflow: editor paging, move/position/remove mutation, ambiguity/reconciliation, and dialog state. Extraction would scatter invariants around unavailable items and server order. Keep intact. |
| `app/SecondPassApp.kt` | 303 | C | Root app selection and complete connection UI are separate responsibilities. Extract connection presentation by ownership, not line target. |
| `spl-client/LibraryModels.kt` | 298 | B | Public Library model catalog is coherent at subsystem level but structurally dense. Split into query/order, paging, compact/detail Book, and axis model files in the same package. |
| `library/LibraryEntityController.kt` | 287 | A | One reusable Author/Series index-plus-detail state machine with sound stale-response handling. Keep behavior together; rename it to expose its actual axis role. |
| `marginalia/ReadingSessionsController.kt` | 278 | B | Paging/filter/search is cohesive; the contract-specific unlinked-Book fallback is an independent, named load policy worth extracting. |

Important smaller files with stronger responsibility problems than their size suggests:

- `LibraryBookPresentation.kt` (115): duplicate presentation plus ordering and paging policy.
- `ShelvesState.kt` (192): states/errors from multiple child owners.
- `MarginaliaState.kt` (150): history, detail, mutation, navigation, and error state in one file.
- `LibraryCombinedState.kt` and `ShelvesCombinedState.kt` (71/69): duplicated experimental
  `StateFlow` implementation.
- `ConnectionModule.kt` (51): two DI modules under one misleading filename.

## 6. Naming findings

Recommended current -> proposed names:

| Current | Proposed | Reason |
| --- | --- | --- |
| `AuthenticatedHome.kt` / `AuthenticatedHome` | `HomeScreen.kt` / `HomeStateHost` plus `HomeScreen` if split | All product Home is authenticated; the adjective no longer distinguishes responsibility. |
| `AppDestination.kt` | `AppRoutes.kt` | File contains top-level destinations, typed routes, return targets, origins, and route mapping. |
| `LibraryEntityController` | `PagedLibraryAxisController` | It owns Author/Series axis index/detail behavior; “Entity” hides domain and role. |
| `LibraryEntityState` | `PagedLibraryAxisState` | Same reason; improves `rg Axis` results. |
| `LibraryEntityResults/Cards/Presentation` | `LibraryAuthorSeriesResults/Cards/Presentation` | These files render the concrete axes, not arbitrary entities. |
| `LibraryEntityDetailOptions` | `LibraryPreviewOptions` or axis-specific detail options | The only behavior is preview limit; “Entity” says nothing. Choose based on whether Author and Series remain symmetric. |
| `LibraryWireDecoder.decodeLibrary` | `ProtocolBodyDecoder.decodeProtocolBody` | It is used across Library, Shelves, and Marginalia. |
| `AuthenticatedReadMapping/WireModels` | `RecentReadingMapping/WireModels` | The files now contain only the Recent Reading contract. |
| `ConnectionModule.kt` | split `ConnectionStorageModule.kt` and `SplClientModule.kt` | Two DI responsibilities are currently hidden by one filename. |
| SDK `getBook/getAuthor/getSeries/listGroups` | receiver-qualified `get`/`list` | `client.library.books.get(id)` is consistent with Tags, Shelves, and Marginalia and avoids redundant subject names. This is a source change and should occur before publication or in one coordinated API migration. |
| `AppIcon.Sessions` | `AppIcon.ReadingHistory` | User-facing destination is Marginalia; “Sessions” is otherwise a domain collection. The new name states the icon's semantic use rather than a former destination label. |

The strongest naming correction is the `LibraryEntity*` family. It currently dominates `rg Entity`
without telling the reader that the implementation is the shared Author/Series axis owner. Rename
the state-machine type to `PagedLibraryAxisController` and presentation files to explicit
Author/Series names rather than propagating “Entity” into new work.

## 7. SDK findings

1. **Public hierarchy: healthy.** `AuthenticatedSecondPassClient` exposes capability families and
   app consumers do not construct paths. Shelves and Marginalia are complete bearer-shaped
   boundaries rather than app-screen facades.
2. **Public/wire separation: healthy.** DTOs are internal, required fields are checked during
   mapping, discriminated public types prevent invalid Shelf owner/editor and annotation states,
   and exact `SeriesIndex`/opaque cover references preserve contract semantics.
3. **Implementation locality: needs work.** Ktor clients, mutation response interpretation, and
   mappers should move under internal subsystem packages. This is the strongest SDK topology
   improvement and can be done without changing the public API.
4. **Error taxonomy: bounded but centralized.** `SplClientException` gives callers useful auth,
   protocol, Shelf mutation, lifecycle, and unlinked-history distinctions. Keeping the sealed root
   is useful. As Reader work adds EPUB download and synchronization failures, avoid turning this
   into one giant enum; add subsystem rejection values only when callers need distinct recovery.
5. **Request execution: healthy.** `AuthenticatedRequestExecutor` alone resolves API paths and
   attaches bearer credentials. It does not log secrets or expose Ktor responses publicly.
6. **Mapping locality: mixed.** Subject-specific mappers are mostly separate, but generic helpers
   have Library names and the internal wire package is flat. Move mapper beside wire/capability
   implementation, leaving shared protocol checks in `internal.transport`.
7. **Consumer comprehension: good with naming blemishes.** Receiver-qualified `get`/`list` names
   would make the hierarchy more uniform. Do not preserve duplicate old and new methods merely for
   compatibility; the SDK is not yet published.

## 8. Test-topology findings

Healthy:

- App tests mostly mirror production feature packages and exercise controllers at their narrow
  boundaries.
- Room projection tests are correctly instrumented and colocated with `home.projection`.
- SDK tests are contract-oriented by capability and verify request/mapping behavior without app
  dependencies.
- Connection, paging, stale-response, mutation reconciliation, exactly-once, and special
  Book-history behavior have focused tests.

Improvements:

- Move tests with any production package reorganization; do not leave all feature tests at the old
  root merely to avoid import edits.
- `FakeAuthenticatedLibraryClient`, `FakeAuthenticatedMarginaliaClient`, and
  `FakeAuthenticatedShelvesClient` sit in the app test root and are primarily unsupported-default
  capability stubs. Move them to `reader.testsupport` and rename them
  `UnsupportedAuthenticated*Capability` (or provide narrowly configurable fakes). “Fake” implies
  useful behavior that these objects intentionally reject.
- `LibraryAxisControllerFixtures`, `ShelvesControllerFixtures`, and
  `MarginaliaControllerFixtures` are large but feature-owned. Keep them separate; do not merge them
  into a universal fixture dump.
- Split the 646-line `LibraryBooksControllerTest` by responsibility—initial/search/order,
  paging/stale requests, scope/filter restoration, and layout persistence—when its production
  package is reorganized. `ConnectionCoordinatorTest` may remain one suite because it follows one
  state machine.
- App-shell tests cover `AppNavigator` mutations but not route-entry/ViewModel lifetime. Add a
  focused navigation/state-retention test before changing the top-level stack strategy. That gap is
  more consequential than additional Compose coordinate tests.
- If the SDK implementation moves under internal subsystem packages, keep public contract tests at
  the public capability level and colocate mapper/response-normalization tests with internal owners.

## 9. Proposed maintenance sequence

Each slice should be independently buildable and behavior-focused.

1. **Typed authenticated identity.** Introduce `AuthenticatedConnectionIdentity`, separate it from
   feature entry/request keys, replace all delimiter-built identities, and freeze reset behavior in
   controller tests. This is the smallest high-leverage first slice.
2. **Connection presentation extraction.** Reduce `SecondPassApp` to root selection and move
   unlinked presentation into `connection`. Split the two DI modules by filename.
3. **Shared compact Book presentation cleanup.** Remove Library's duplicate compact Book adapter;
   extract ordering and paging-trigger policy into precisely named files. This is small and easy to
   visually verify.
4. **Shelves topology pass.** Move collection/detail/management/editor files and their states/tests
   into shallow packages. No controller behavior changes.
   Completed: production and test files now mirror those four ownership clusters; Shelves root
   retains aggregate coordination and shared presentation/failure infrastructure.
5. **Library topology and vocabulary owner.** Move Books/axis/chrome clusters first, then extract
   the filter-vocabulary child in a separate commit-sized change. Rename `LibraryEntity*` during
   that move.
6. **Marginalia topology and scoped-history loader.** Cluster history/detail files, then extract the
   unlinked-Book fallback loader and replace the mutable detail-update callback with explicit parent
   coordination.
7. **State-combination cleanup.** Replace the two custom experimental `StateFlow` implementations
   with ordinary coroutine composition after package moves make their owners clear.
8. **SDK internal topology.** Move Ktor clients/wires/mappers beneath internal subsystem packages;
   rename stale decoder/recent-reading files. Keep public API unchanged in this slice.
9. **SDK public naming/package decision.** Before external publication or Reader API expansion,
   decide whether public subsystem packages are worth the import break and normalize receiver-
   qualified method names once.
10. **Top-level navigation lifetime.** With explicit tests in place, adopt stable top-level state
    retention before Reader resources and navigation are added. This is intentionally later than
    mechanical moves because it changes runtime behavior and needs device verification.

This is not a rewrite plan. Slices 1–8 are behavior-preserving maintenance; slice 10 is a deliberate
product/runtime correction.

## 10. Do not change

- Do not split `ConnectionCoordinator` merely because it is 413 lines. Its ordering and ambiguity
  semantics are easier to verify in one state machine.
- Do not split `LibraryBooksController` until a second real owner emerges. Paging, filter context,
  stale-response rejection, and unfiltered restoration are one Books responsibility.
- Do not split `ShelfContentsEditorController`; unavailable-item rules, mutation, canonical reload,
  and reconciliation form one editor transaction workflow.
- Do not replace `HomeProjectionRepository` with generic repositories or turn Room into a catalog
  mirror. Its bounded cache responsibility is clear.
- Do not merge the three Shelf collection controllers into a scope-conditional controller. Their
  independent state is intentional.
- Do not create separate Author and Series package hierarchies around thin facades; preserve their
  shared axis implementation.
- Do not create a universal paging framework from superficially similar feature states. Their
  reset, filtering, reconciliation, and error semantics differ.
- Do not move endpoint topology, bearer handling, or DTO decoding into `:app`.
- Do not make annotations a top-level app feature; they remain owned by Reading Session detail and
  future Reader/session coordination.
- Do not reorganize the small semantic design packages for symmetry. Their current names and
  boundaries are already searchable.
- Do not introduce a Book Detail duplicate per origin. The shared app-level destination and typed
  return context are sound.
