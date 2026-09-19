# Second Pass Reader Android Agent Doctrine

## Purpose

This file defines repository-specific architecture, naming, domain, product, and Android rules for **SecondPassReader-AndroidClient**. Base engineering-agent policy still applies; this file controls when it is more specific.

Build a production first-party client that obeys the same public boundaries expected of a credible third-party Second Pass Library (SPL) client.

## Project identity

- Product: **Second Pass Reader**
- Namespace/application ID: `com.secondpasslibrary.reader`
- Platform: native Android/Kotlin
- Current priority: tablets; avoid gratuitously breaking phones when adaptive layouts can support them.

SPL is a self-hosted library centered on durable books, reading sessions, and reader-owned marginalia.

## Core rule: responsibilities stay in their lane

Responsibility boundaries are requirements, not preferences. Do not bypass them for shorter code, merge independently meaningful owners to reduce boilerplate, or let framework convenience decide domain ownership. Coordinate cross-boundary workflows through the nearest legitimate common owner and narrow explicit contracts. A shortcut that violates ownership is a defect even when it works.

## First-party client, third-party contract

The app is first-party but not privileged. It must use client-facing SPL capabilities available to an independent client; shared maintainers, repository proximity, and knowledge of server internals grant no special access.

If required functionality is missing from the Android SDK contract, decide whether that contract should expose it. Never let app code bypass the SDK. The app must be both a production reader and evidence that the SPL client boundary supports third-party development.

## Gradle modules and dependency direction

The project uses:

```text
:app -> :spl-client
```

Never allow `:spl-client -> :app`. The SDK must not depend on app UI, screens, app orchestration, or the EPUB renderer. Add modules only for demonstrated boundaries, not symmetry or speculative scale.

## `:spl-client` ownership

`:spl-client` owns the SPL protocol boundary and should fill the role of the web reader's packaged `@secondpass/client` SDK. It owns:

- discovery and pairing HTTP, server-provided URL handling, bearer authentication, transport, requests, and response decoding;
- schema validation, wire DTOs, transport-error interpretation, and mapping to client-facing models;
- behavior required by protocol semantics, including protocol-level retry and idempotency;
- EPUB asset downloads; and
- reader-authorized account, library, session, marginalia, and shelf capabilities.

Application features may own product workflow sequencing, retry lifecycle, backoff, cancellation, and coordination. Connection owns pairing creation-to-consumption sequencing and the handoff into verification; `:spl-client` exclusively performs the pairing HTTP and protocol decoding.

Expose client capabilities and domain-shaped types, not server implementation. Unless an explicit public SPL contract requires otherwise, hide Retrofit/OkHttp, headers, endpoint paths, JSON/wire naming, response envelopes, transport-internal retry plumbing, authentication internals, and server-framework concepts.

App code must not construct SPL URLs, attach authorization headers, deserialize wire DTOs, or call Retrofit/OkHttp from ViewModels.

### Publication test

Design the SDK API as if it could later be extracted, versioned, documented, and published, although publication is not currently required. An API is suspect if an external developer must know app packages, Compose state, app ViewModels/entities, internal server models, or concrete EPUB renderer classes.

## Application architecture

Use native Android patterns rather than mechanically porting React:

- Kotlin, Jetpack Compose, coroutines, and Flow/StateFlow;
- Hilt for dependency injection;
- DataStore for small preferences/settings; and
- Room only when structured durable local data warrants it.

Do not add Room or another architecture framework by convention. Prefer one obvious ownership path for state.

Organize `:app` by capability and responsibility. Expected areas may include `app`, `connection`, `home`, `library`, `shelves`, `sessions`, `reader`, `storage`, and `design`. Reader concerns may include `activity`, `annotations`, `domain`, `engine`, `session`, `settings`, and `shell`. These are ownership signals, not directory quotas; evolve topology from evidence.

## Repository organization and role vocabulary

- Package and folder names describe ownership or a domain concept. Prefer feature-local grouping over global technical buckets.
- One-file folders require a durable ownership, runtime, or dependency boundary. Do not create folders for hypothetical growth.
- A Kotlin file's primary type normally matches its filename. Grouped model and UI files use a precise concept name.
- Tests mirror the production concept where practical; shared support stays near the behavioral family that uses it.
- Avoid vague `Utils`, `Helpers`, `Manager`, `Common`, `Misc`, `Stuff`, and `Thing` names.

Role suffixes have stable meanings:

- **Controller:** owns one bounded feature or subsystem's stateful operation surface.
- **Coordinator:** orders or synchronizes independent owners.
- **Orchestrator:** runs a multi-step workflow across capabilities without absorbing them.
- **Policy:** applies pure rules, validation, classification, eligibility, or selection.
- **Presenter:** creates display-ready state, text, or formatting.
- **Mapper:** performs pure representation conversion.
- **Adapter:** translates an external library, service, or schema contract.
- **Bridge:** exposes a narrow boundary across runtimes, renderers, languages, or systems.
- **Repository:** gives the domain asynchronous record access while hiding storage or transport details.
- **Store:** owns app-local durable state or preferences.
- **Dao:** owns Room access and atomic persistence mechanics.
- **Factory:** constructs configured owners without retaining their lifecycle.
- **Lifecycle:** owns setup, teardown, or subscription timing.
- **State:** defines observable state and closely related state invariants.
- **Intent:** defines typed commands into an owner.
- **Module:** declares dependency-injection bindings.
- **TestSupport** and **Fixtures:** provide shared test-only setup, fakes, builders, or representative data.

## ViewModels and Compose

ViewModels hold UI state and coordinate screens. They may accept intent, invoke capabilities, and manage screen loading/errors. They must not own SPL protocol behavior, EPUB details, CFI algorithms, annotation engines, session rules, formatting libraries, persistence implementation, or unrelated workflows. Extract independently meaningful behavior to its actual owner.

Compose owns presentation and interaction: **state down, intent up**. Composables must not perform SPL calls, handle tokens/wire DTOs, parse EPUBs, own durable sessions, or persist data as a rendering side effect. Lifecycle primitives may express Android lifecycle behavior, but recomposition is not domain identity.

## Visual and adaptive product direction

Preserve the web reader's identity through native tablet interaction: **same library, identity, and information hierarchy; native Android expression**.

The initial reference is a dense dark theme with very dark neutral backgrounds, slightly elevated surfaces, restrained blue accents, thin low-contrast borders, high-contrast primary text, subdued metadata, touch-appropriate compact controls, cover-driven color, and selective monospace metadata. Use semantic tokens; do not produce a generic spacious Material sample. Maintain usable touch targets without discarding intentional information density.

Pixel Tablet/large-tablet UX has priority. Prefer width-based adaptive layouts over device-name checks and tablet-only hardcoding. Compact layouts may collapse secondary rails, panes, drawers, panels, and grid columns, but do not degrade tablet UX to force identical phone layouts.

## Web relationship and feature scope

The web reader is a behavioral/architectural reference, not code to port mechanically. Target **ability parity**, not implementation parity. Classify behavior as:

1. SPL domain/server requirement;
2. SDK contract;
3. product behavior;
4. web-platform detail; or
5. renderer-specific workaround.

Port 1–3 deliberately; do not automatically port 4–5. Use Android-native patterns.

Reader-facing scope includes pairing, authenticated identity, library browsing/search/filter/sort, Book detail, Shelves and authorized personal Shelf management, Recent History, Reading Sessions and metadata, EPUB reading, durable CFI progress, highlights/bookmarks/notes, prior-session Marginalia layers, Reader settings, in-book search, table of contents, completion affordances, and Marginalia management.

Third-party marginalia import/translation is explicitly not required. Do not port that web subsystem for feature-count parity; this does not change SPL's broader portability philosophy.

## Reading sessions and book identity

A session is one reader's pass through one immutable book. For each reader/book relationship:

- at most one session is open;
- resume an existing open session;
- create a session lazily, immediately before genuine EPUB/CFI-capable reading needs it; and
- browsing metadata never creates a session.

Sessions should normally remain background mechanics. Do not require management ceremony before reading. Closing is optional: a reader may validly keep one session open indefinitely for their lifetime experience with a book. Never prevent, nag against, or label that misuse. Offer completion where context helps, such as near book end or in session-management surfaces.

An imported EPUB is an immutable, stable book asset because marginalia CFIs address its exact structure. Catalog metadata may be corrected, but a corrected/different edition or changed EPUB is a new imported book. Equal titles or metadata do not imply interchangeable EPUB structure.

## CFI, EPUB engine, and marginalia

EPUB CFI is the durable spatial language for locations and marginalia. SPL does not render EPUB or own renderer internals; Android owns EPUB interpretation.

```text
UI -> screen/activity orchestration -> reader/session domain
                                      /                 \
                              spl-client             reader engine
                                  |                       |
                              SPL server          EPUB implementation
```

The SDK/server and engine branches must not reach into each other's implementation. Coordinate them at the reader/session boundary.

Readium is the concrete renderer behind app-owned engine contracts for loading, rendering, CFI resolution, visible location/range, navigation, selection, highlights, search, table of contents, rendition settings, reflow, and lifecycle. Readium types and workarounds stay inside the engine/adaptation layer.

Marginalia—highlights, bookmarks, notes, progress, and session metadata—is first-class reader-owned data. The active session is mutable; prior sessions may appear as read-only contextual layers. Never flatten prior marginalia into the active session or overwrite historical passes when revisiting a location.

## Persistence, authentication, and errors

Server and device state have different owners. Never make a cache canonical when SPL owns the record. Local persistence may hold connection profiles, app/reader settings, device-local preferences, and explicitly justified caches/offline data.

Any offline behavior must define canonical ownership, synchronization, conflict handling, expiration, and failure behavior. Server-owned records remain authoritative. Reader-authored state may be local-first only where the offline product contract defines it; this does not make cached server metadata canonical or extend local-first ownership to librarian, admin, or catalog workflows.

Treat bearer credentials as secrets: never log them, include them in diagnostics/exceptions, expose them unnecessarily through UI state, or store them in plaintext debug files. Use protected Android storage when persistent credentials are implemented; keep their lifecycle behind connection/SDK boundaries.

Translate failures at boundaries:

```text
transport failure -> SDK/client error -> app interpretation -> UI presentation
```

Preserve internal diagnostics without leaking secrets or meaningless protocol terminology.

## Cross-feature collaboration and file shape

Features must not reach laterally into one another's internals: `library` does not manipulate private `sessions`, `reader` does not reach into private `shelves`, and `sessions` does not invoke renderer internals. Find the nearest common owner, define the smallest contract, route intent through it, and keep implementations private. Share stable dependency-neutral values when useful; do not disguise behavioral coupling as utilities.

Apply the delete test to small abstractions: mentally inline the implementation into its callers without removing behavior. Delete the abstraction when this makes the code simpler with only trivial repetition; keep it when inlining would duplicate knowledge, scatter a domain concept, expose implementation details, or spread future changes across callers. Call-site count alone proves nothing: a one-use abstraction may define a valuable seam, while a widely used trivial wrapper may still be shallow. Extract when a stable concept or ownership seam is evident, not merely on the second occurrence.

Favor understandable seams over multifunction files without mechanically creating one file per class. File size is evidence, not a rule:

- ~100 lines: comfortable focused unit;
- ~150–220: commonly reasonable;
- ~250: inspect for separable responsibility;
- ~300: make an explicit keep/split decision; and
- substantially above 300: require a concrete cohesion reason.

Scrutinize large ViewModels, repositories, engine adapters, and orchestrators.

## Testing and static verification

Test at the narrowest meaningful boundary. Pure Kotlin should not require a device unless Android behavior is under test. Prioritize policies, mappers, presenters, session transitions, app-owned CFI/location translation, SDK wire/model and error mapping, retry/idempotency, persistence rules, and orchestration. Use instrumented tests for platform integration; do not move domain logic into Compose because UI tests exist.

Runtime tests protect runtime code, not copy text unless that copy text is actually making deterministic runtime code changes. Apply this guidance when test files are touched.

Validation is CLI/Gradle-owned through the committed wrapper. Once scaffolded, the baseline is Android Lint, Kotlin compilation, unit tests, detekt, and ktlint. Editor diagnostics are advisory. Do not add overlapping formatters or redundant analyzers without a concrete reason.

The canonical commands and device/live-test split are documented in [Repository tooling](docs/repository-tooling.md).

## Dependencies and open decisions

Treat dependencies affecting networking, serialization, storage, navigation, DI, EPUB rendering, cryptography, background work, or synchronization as architectural decisions. Check repository doctrine and the current ecosystem first. Familiarity alone is not justification; do not rebuild commodity infrastructure when a mature supported library fits.

Open decisions include additional Gradle feature modules, broader offline administration, release distribution, and a final light theme. Established platform levels, Readium ownership, Navigation 3, and the bounded offline Reader contract are recorded in the architecture docs and build configuration.

Do not settle them during unrelated work. Investigate deliberately and report tradeoffs when one becomes necessary.

## Documentation and Git

Documentation is architectural. When requested or clearly required by a coherent change, record stable boundaries, owners, invariants, public contracts, lifecycle expectations, non-obvious failures, important library limitations, and deferred decisions. Explain why; do not narrate implementation line by line or silently weaken doctrine for convenience.

The user owns commit decisions. Unless explicitly instructed, never commit, amend, push, rewrite history, or choose commit boundaries. Inspecting status, diffs, and history is allowed when relevant.

## Agent workflow and reporting

Before a non-trivial change:

1. inspect repository evidence;
2. identify the owning layer and crossed contracts;
3. check whether doctrine answers the design question; and
4. make the smallest complete, coherent change.

Prompt convenience does not permit weaker architecture. Report conflicts before implementing workarounds.

For substantive work, report what changed, its owner and rationale, affected boundaries/contracts, tests/static/runtime checks, anything unverified, architectural concerns, necessary deferred questions, and every intentional deviation from this file. Never claim completion when required validation failed or was skipped.

## Decision heuristics

When placing behavior, ask: **Who has enough authority to own this decision without learning anything outside its responsibility?**

When independent areas collaborate, ask: **What is their nearest legitimate common coordinator, and what smallest contract connects them?**

Before bypassing `:spl-client`, ask: **Could an unrelated third-party Android client do this through the public SPL contract?** If not, determine whether the SDK contract is incomplete before granting first-party access.

## Final principle

Build explicit, cooperating responsibilities. First-party status grants no privilege; the SDK is not bypassable transport plumbing; the EPUB engine is not the domain; Compose is not the application architecture; ViewModels are not universal owners; sessions are durable records rather than mandatory ceremony; and CFIs—not renderer state—describe durable reader space.

Prefer clear ownership, narrow contracts, native Android expression, and architecture that remains understandable after the immediate task is forgotten.
