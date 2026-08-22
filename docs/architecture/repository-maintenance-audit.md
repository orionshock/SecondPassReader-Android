# Repository maintenance audit

Date: 2026-08-21

This is the current-state maintenance record. Completed maintenance evaluations have been
removed; Git history records those changes. The approximate 300-line threshold remains an
inspection trigger, not a splitting rule.

## Executive assessment

The repository's main ownership spine remains healthy:

- `:app` depends on `:spl-client`; the SDK has no app dependency.
- HTTP paths, bearer handling, wire DTOs, and transport errors remain inside `:spl-client`.
- Library, Shelves, and Marginalia now expose shallow parent/child package topology.
- authenticated reset identity is represented by app-owned `AuthenticatedConnectionIdentity`.
- Home's Room data remains a bounded resilience projection rather than a catalog replica.
- shared Book Detail and typed return contexts remain app-shell owned.

The highest remaining architectural risk before Reader work is top-level destination state
lifetime. `AppNavigator.select`, `openLibrarySearch`, and `replaceWith` clear the only back stack.
Feature state survives a pushed detail round trip only while its origin entry remains below that
route; drawer navigation discards it.

The strongest remaining topology problem is in `:spl-client`: public interfaces/models and
internal Ktor clients/mappers still share `com.secondpasslibrary.client`. The strongest remaining
app responsibility problem is the experimental aggregate `StateFlow` implementation duplicated
by Library and Shelves.

## Current topology and responsibility findings

### App shell

Healthy:

- Cross-feature routes and typed return targets are shell-owned.
- Book Detail does not depend on Library or Shelves controllers.
- Book-scoped Marginalia preserves the Book Detail origin.

Remaining:

- Top-level selection clears the Navigation 3 back stack and therefore owns destructive state
  lifetime implicitly.
- App-bar behavior is selected through route-type checks. This remains manageable, but Reader
  routes should not expand this into an unbounded conditional.
- `AppDestination.kt` contains the complete route catalog; `AppRoutes.kt` would be more precise.

### Connection and application root

`ConnectionCoordinator` remains one cohesive pairing/restore/verification state machine. Its
exactly-once consume and durable-storage ordering are reasons to keep it intact.

`SecondPassApp.kt` owns lifecycle notification and linked/unlinked root selection. Unlinked
presentation and its state-down/intents-up action contract live in `ConnectionScreen.kt` and
`ConnectionScreenActions.kt`. Connection storage and SPL client bindings live in module files
matching their declarations.

### Home

The `HomeProjectionRepository -> Room + authenticated SDK` boundary remains sound. No repository
or cache generalization is warranted.

Remaining low-priority items:

- `AuthenticatedHome` is a stale permanent-screen name; all Home content is authenticated.
- historical unused Room shelf-owner columns should be removed only through a deliberate schema
  migration.

### Library

Current ownership is:

```text
LibraryController
|- LibraryFilterVocabularyController
|- LibraryBooksController
|- LibraryAuthorsController -> PagedLibraryAxisController
`- LibrarySeriesController  -> PagedLibraryAxisController
```

The package structure and shared presentation dependencies now match that tree. Vocabulary jobs,
bounded paging, retries, authentication events, and stale scope/connection rejection are owned by
`LibraryFilterVocabularyController`. Scope, selected tag, route entry, pending tag intent, and
cross-child transitions remain parent-owned.

The child controllers are private implementation details. `LibraryViewModel` sends commands
through explicit `LibraryController` methods, matching the existing aggregate-state ownership.

Remaining findings are detailed in **Library re-read after Maintenance 6** below.

### Book Detail

Shared Book Detail remains correctly app-level. The shelf picker is a focused child workflow but
its five `BookShelfPicker*` files still sit at the Book Detail root. A shallow
`bookdetail.shelfpicker` move would improve locality without creating another controller.

### Shelves

The collection/detail/management/editor topology is truthful. Root state is aggregate navigation
state; mutation reconciliation remains parent-owned. `ShelfContentsEditorController` is large but
cohesive around editor projection, unavailable items, mutations, and canonical reconciliation.

Remaining:

- `ShelvesStateFlow` manually implements experimental `StateFlow` inheritance, matching the
  Library issue described below.
- `ShelvesViewModel` is verbose delegation, not a hidden behavioral owner. Do not split it merely
  by method count.

### Marginalia

History and detail topology is clear. Annotation, metadata, and close owners remain children of
Reading Session Detail.

`BookScopedReadingSessionHistoryLoader` owns the initial scoped-history resolution workflow:
normal history, or the bounded non-mutating active-session lookup that proves a visible Book has
no history. The controller retains filters, paging, stale-response rejection, and state.

Authoritative metadata and close results leave Reading Session Detail through the
constructor-injected `ReadingSessionAuthoritativeUpdateSink`. `MarginaliaController` wires that
boundary to history reconciliation; Detail has no direct history-controller dependency or mutable
callback lifecycle.

### Design and shared presentation

`design.icons`, `design.book`, `design.marginalia`, and `design.components` are small, semantic,
and correctly shared. Library and Shelves consume the design-owned compact Book presentation.
Do not reorganize these packages for symmetry.

### `:spl-client`

The public hierarchy remains workflow-shaped:

```text
AuthenticatedSecondPassClient
|- library -> books/authors/series/groups/tags
|- shelves
`- marginalia -> books/sessions
```

Remaining:

- Move internal Ktor clients, wire models, mappers, and response interpretation beneath internal
  subsystem packages without changing the public API.
- Rename `LibraryWireDecoder.decodeLibrary`; it decodes Library, Shelves, and Marginalia bodies.
- Rename Recent Reading-only `AuthenticatedRead*` files to their actual subject.
- `LibraryModels.kt` is structurally dense at 298 lines. A same-package declaration split may
  improve discovery, but its public domain model remains cohesive.
- Normalize receiver-qualified SDK method names only as one deliberate source migration before
  publication. Do not retain duplicate old/new methods without a compatibility requirement.

## Ranked remaining findings

### High — Explicit top-level state lifetime

- Files: `AppNavigator.kt`, `AuthenticatedAppShell.kt`, feature `StateHost` owners.
- Evidence: `select`, `openLibrarySearch`, and `replaceWith` call `backStack.clear()`.
- Direction: establish route-entry lifetime tests, then choose stable top-level entries/back stacks
  or shell-scoped feature state before Reader resources are introduced.
- Risk: high/product-visible. This is not a behavior-preserving refactor.

### Medium — Experimental aggregate `StateFlow` implementations

- Files: `LibraryCombinedState.kt`, `ShelvesCombinedState.kt`.
- Evidence: both opt into `ExperimentalForInheritanceCoroutinesApi`, manually implement `value`
  and `collect`, and construct those snapshots through separate paths.
- Direction: replace them with ordinary coroutine composition owned by each parent scope. Do not
  create a universal feature-state framework.
- Risk: medium because synchronous initial `value`, cancellation, and eager collection semantics
  must be frozen in tests.

### Medium — SDK implementation and public API share one package

- Files: `Ktor*Client`, `*Mapping`, `*Response`, `AuthenticatedRequestExecutor`, public models and
  interfaces.
- Direction: internal-only subsystem topology first; public packages remain a separate decision.
- Risk: low for internal moves, provided public signatures remain unchanged.

### Completed — Marginalia authoritative-update callback seam

- Completed: `ReadingSessionAuthoritativeUpdateSink` is construction-injected into Detail and
  wired by `MarginaliaController`; history reconciliation remains parent-coordinated.

### Low — Remaining naming/locality cleanup

- `AuthenticatedHome` -> permanent Home naming.
- `AppDestination.kt` -> `AppRoutes.kt`.
- `LibraryWireDecoder` -> protocol-body naming.
- `AuthenticatedRead*` -> Recent Reading naming.
- move `BookShelfPicker*` into `bookdetail.shelfpicker`.

## Approximate 300-line production file review

| File | Lines | Assessment |
| --- | ---: | --- |
| `connection/ConnectionCoordinator.kt` | 413 | Cohesive state machine; keep intact. |
| `library/books/LibraryBooksController.kt` | 362 | Cohesive Books state machine; no responsibility split justified. |
| `shelves/editor/ShelfContentsEditorController.kt` | 337 | Cohesive editor transaction workflow; keep intact. |
| `library/LibraryController.kt` | 351 | Large parent facade; current cross-axis behavior remains legitimate parent coordination. |
| `marginalia/history/ReadingSessionsController.kt` | 299 | Cohesive history state/paging owner after initial scoped resolution moved to its loader. |
| `spl-client/LibraryModels.kt` | 298 | Structurally dense public catalog; declaration-only split is optional. |
| `library/axis/PagedLibraryAxisController.kt` | 294 | Cohesive shared Author/Series state machine; keep intact. |

Large test files matter independently of production cohesion:

- The 19 Books-controller tests are grouped by initialization, paging, context, and layout, with
  Books-owned fixture support; that verification surface now matches behavioral ownership.
- `LibraryControllerAxisTest.kt` is 523 lines but follows one parent coordination surface; inspect
  again only if another parent responsibility is extracted.

## Library re-read after Maintenance 6

### Static-analysis baseline

The current app detekt report contains zero findings. Cognitive complexity is explicitly enabled
with an allowed complexity of 15; cyclomatic complexity remains enabled through detekt's default
configuration. No Library method violates either configured threshold.

That clean result is meaningful but not dispositive:

- `LibraryController`, `LibraryViewModel`, `LibraryBooksController`,
  `PagedLibraryAxisController`, and the two thin Author/Series facades suppress
  `TooManyFunctions` with bounded-intent rationales.
- `LibraryScreen.LibraryBrowseContent` suppresses `LongParameterList` for its callback boundary.
- `LibraryStateFlow` requires an experimental inheritance opt-in.
- Production size is concentrated in explicit state machines; test size is concentrated in one
  Books controller suite.

The cross-axis methods are short and explicit. `commitSearch`, `loadNextPage`, and `retry` dispatch
by result kind/axis. `selectScope` and `selectTag` fan state into the three children.
`selectAuthor`/`selectSeries`, `navigateTo`, and `clearSelectedAuthorSeries` own sibling handoff.
This is the parent controller's stated responsibility. Extracting it would require another owner
to mutate parent chrome state while knowing all three children, reproducing rather than reducing
coupling. There is no complexity evidence for that extraction today.

### Candidate 1 — Replace custom aggregate StateFlow inheritance

**Files and methods**

- `LibraryCombinedState.kt`: `LibraryChromeState.toState`, `LibraryStateFlow.value`, and
  `LibraryStateFlow.collect`.
- `LibraryController.kt`: aggregate state construction.
- `ShelvesCombinedState.kt`: same implementation pattern and therefore the natural paired scope.

**Problem**

The aggregate manually implements an experimental interface. Synchronous `value` reads and
collected emissions use two construction paths. The implementation is small, but it carries more
lifecycle and consistency risk than its size suggests.

**Evidence**

- Explicit `ExperimentalForInheritanceCoroutinesApi` opt-in.
- The same pattern exists in Shelves, so another feature would likely copy it.
- Detekt reports no complexity violation; the concern is framework-contract ownership and
  duplicated composition, not branching.

**Safest boundary**

Freeze immediate initial-state, connection reset, and child-emission behavior in tests. Then use
ordinary `combine(...).stateIn(...)` owned by each parent scope, or avoid an aggregate only if all
callers can consume explicit states without presentation churn. Change Library and Shelves in one
focused slice; do not create a generic state framework.

**Risk and timing**

- Expected behavioral risk: **medium**, especially around eager collection and synchronous
  `value` expectations.
- Recommendation: **defer until after Candidates 1 and 2**, then handle Library and Shelves
  together.

### Lower-priority Library observations

- `LibraryScreen` passes a large callback set through two presentation layers and suppresses
  `LongParameterList`. Typed chrome/result action groups could improve locality, but introducing a
  lambda grab bag or unstable Compose object would be worse. Defer until the intent surface grows
  again.
- `LibraryBooksController` is 362 lines and suppresses `TooManyFunctions`, but mode, scope/filter,
  paging, stale-response rejection, layout preference, and unfiltered Author/Series restoration
  still form one state machine. Do not split it now.
- `PagedLibraryAxisController` is 294 lines and cohesive. Do not create separate Author and Series
  implementations.
- `LibraryFilterVocabularyController` is 178 lines and has one bounded responsibility. Its
  `TooManyFunctions` suppression reflects explicit lifecycle/retry intents, not mixed ownership.

## Remaining maintenance sequence

1. Replace Library/Shelves custom aggregate `StateFlow` inheritance.
2. Move `:spl-client` implementation under internal subsystem packages and fix stale internal
   names.
3. Move the Book Detail shelf-picker child cluster and perform remaining low-risk naming cleanup.
4. Establish top-level navigation state-lifetime tests and then make the deliberate retention
   change before Reader work.
5. Decide public SDK package/method naming before publication or another large SDK expansion.

## Do not change without new evidence

- Do not split `ConnectionCoordinator` merely by line count.
- Do not split `LibraryController` cross-axis routing into another coordinator today.
- Do not split `LibraryBooksController` or `PagedLibraryAxisController` merely by line count or
  `TooManyFunctions` suppression.
- Do not split `ShelfContentsEditorController`; its unavailable-item and reconciliation invariants
  belong together.
- Do not generalize Home's projection repository or Room schema into an offline catalog.
- Do not merge independent Shelf collection controllers.
- Do not create separate Author and Series package hierarchies around thin facades.
- Do not create a universal paging or aggregate-state framework.
- Do not move endpoint topology, bearer handling, or DTO decoding into `:app`.
- Do not make annotations a top-level feature outside Reading Session/Reader ownership.
- Do not duplicate Book Detail by origin.
