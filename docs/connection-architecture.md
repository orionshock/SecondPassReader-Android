# SPL connection architecture

This document records the first production client workflow built under the repository doctrine. Raw endpoint details remain server-contract concerns; this describes Android ownership and failure semantics.

## Ownership

`:spl-client` owns URL normalization, public discovery, pairing requests, status polling calls, one-time consumption, bearer attachment, transport DTOs, response validation, and authenticated account/server-context mapping. It remains a pure Kotlin/JVM module. Ktor Client 3.5.2 uses its OkHttp engine, and kotlinx.serialization 1.11.0 decodes internal wire models. Neither transport type is exposed to `:app`.

`:app` owns Android lifecycle sequencing, user-facing state, encrypted credential persistence, non-secret connection persistence, and display mapping. Compose receives no bearer credential and performs no network or persistence work.

## Discovery and URL normalization

`ServerOrigin` trims user input, adds HTTPS when a scheme is omitted, accepts only HTTP or HTTPS, rejects embedded credentials, and discards path/query/fragment data. Discovery always probes `/.well-known/secondpass` at that origin.

Public well-known discovery verifies SPL identity. Client-API discovery then provides the authoritative API base, server base, login-request URL, token type, and endpoint templates. Pairing uses the concrete absolute URLs returned by the server; Android does not synthesize authorization, poll, or consume routes.

Public discovery and authenticated `/server/info/` are deliberately different models. The former establishes where and what the server is before trust; the latter describes account-visible server configuration after bearer verification.

## Pairing lifecycle

The application state sequence is:

```text
server entry -> verified server -> client naming -> pairing request
             -> waiting/polling -> approved -> one consume attempt
             -> encrypted credential -> connection profile -> authenticated verification
             -> linked development status
```

The fixed client type is `second-pass-android-client`. The default client name uses a cleaned Android model name, contains no device identifier, and remains editable.

`ConnectionCoordinator` owns the polling job. It waits at least the server-provided interval, runs one loop, and retries unreachable or throttled checks with backoff capped at 60 seconds or the server minimum, whichever is higher. A successful check resets the interval; returning the app to the foreground replaces the existing loop rather than duplicating it. Polling stops on cancellation, a terminal status, an invalid protocol response, pairing abandonment, or ViewModel clearance. Recomposition does not start work.

## Exactly-once consumption

An approved request is POSTed once. A successful token-bearing response creates an opaque `BearerCredential`; a later `consumed` response without a token is terminal and unrecoverable. Any transport failure, non-success response, or malformed success response during consumption is classified as ambiguous because the server may already have atomically consumed the credential. The coordinator never retries that POST automatically.

After consumption, ordering is strict:

```text
consume -> encrypt and durably store credential -> persist profile -> verify /me and server info -> publish linked state
```

If secure storage fails, the UI reports that a one-time credential was issued but not safely retained. If profile storage fails after the credential is safe, retry resumes at profile persistence and never consumes again.

## Local persistence

`KeystoreBearerCredentialStore` generates a non-exportable AES-256-GCM key in Android Keystore. The token is encrypted with a random IV and associated data before ciphertext is committed to private preferences. The project does not use deprecated `EncryptedSharedPreferences`, plaintext preferences, DataStore, saved state, logs, or UI state for the token.

The encrypted credential envelope temporarily includes a recovery copy of the non-secret profile. This is a transaction journal: it allows restart recovery if DataStore fails after one-time consumption. Once the Preferences DataStore profile commits, the envelope is rewritten without the recovery profile. DataStore separately owns the normal non-secret server/client-session connection record.

On startup, matching profile and credential state is verified through `/accounts/me/` and `/server/info/`. A rejected/revoked credential preserves the known account and enters the re-link flow; a transient server/network failure preserves both stores and exposes retry. An interrupted profile commit is repaired from the encrypted transaction journal.

## Authenticated context

The SDK models sparse current-user fields and preserves absence for optional true-only flags. `reading_client_base_url` remains nullable. Successful token consumption alone never establishes linked state; both authenticated context requests must succeed.

The authenticated Home surface retains useful verified library, account, group, and client-session context inside the permanent application shell. It does not imply that future product Home capabilities have been implemented.

## Authenticated read surface

`KtorSecondPassClient` creates a credential-bound `AuthenticatedSecondPassClient`. The scope retains the validated API base and opaque bearer credential so feature callers do not repeatedly handle either value. Library, Shelves, and Marginalia use cohesive capability families rather than duplicate flat methods:

```text
AuthenticatedSecondPassClient.library
  |- books  -> scoped list and scoped broad search
  |- authors -> scoped list and global detail
  |- series -> scoped list and global detail
  |- tags -> scoped list and global detail
  `- groups -> selector summaries

AuthenticatedSecondPassClient.shelves
  |- list and detail
  |- normal visible-item pages
  |- personal-shelf editor pages with unavailable-item placeholders
  `- owned-personal Shelf and item mutations

AuthenticatedSecondPassClient.marginalia
  |- books -> summaries/history plus active lookup, open/resume, and start-over
  `- sessions -> history/detail, lifecycle, progress, and annotation synchronization
```

Pagination, compact/preview Books, cover references, scope, and shared Library value types remain Library-level artifacts. Catalog Tags expose Reader-safe identity, slug, and scoped book counts; endpoint topology remains private to the SDK.

Marginalia reads preserve server ordering and historical Book context after current visibility is lost; `canOpen` remains advisory. Session status stays `active` or `closed`, while counts and activity timestamps remain server-owned. Active lookup/open/start-over return one bootstrap boundary containing Book context, the nullable active Session, current annotations, and the first closed-history page. Ordinary Session detail does not load annotations.

Active lookup is a non-mutating authority check. Open/resume is the normal convergent lazy create-or-resume operation under the server's one-active-Session rule; clients accept the exact server-returned identity. Close can atomically finalize metadata/progress. Start-over is strictly an explicit user recovery action, never an automatic reconnect behavior. It requires a caller-owned stable `MarginaliaIdempotencyKey`; the SDK validates and reuses it for transport execution but does not persist it. The key is scoped per authenticated user across that user's idempotent requests, bound to method, complete path, and normalized body, and replayable with identical input for exactly 24 hours. App orchestration must retain the same key and exact request across logical-operation retries.

Progress is Session metadata with last-write-wins whole-value replacement and no revision or timestamp conflict protection; clients must coalesce and serialize pending writes so an older request cannot arrive last. CFI and location labels remain opaque. Annotations form a complete authoritative child collection synchronized through explicit Bookmark/Highlight upserts and client-ID deletes. Upserts are last-write-wins for a Session-scoped client ID, and every successful synchronization response contains the complete authoritative collection. The server accepts an opaque nonblank 1-255-character value and does not enforce UUID syntax; Android conventionally generates random UUID v4 values. This client-owned identity is separate from the server annotation UUID and remains stable across retries and cross-Session continuation. Delete is idempotent, including unknown and already-deleted client IDs; a later upsert restores the tombstoned identity. Synchronization is deliberately capped at the server's 100-operation atomic batch rather than implying cross-request atomicity through automatic chunking.

Structurally valid progress or annotation writes to a closed Session return HTTP `409` with `SESSION_CLOSED`; malformed request bodies fail validation first with `400`. HTTP `404` intentionally reveals only that the resource is unavailable to the credential and must not be interpreted as proof of deletion or nonexistence. Owned Sessions with lost Book visibility return `403 PERMISSION_DENIED`. The complete retry classification and remote-close behavior are recorded in the offline product contract.

After an annotation batch receives `SESSION_CLOSED`, clients retain unresolved upserts, call normal
Book `open` once, and replay them against the returned active Session with their original
Session-scoped client IDs. This makes an edit from the closed Session a distinct authored copy in
the writable Session without changing identity across retries. Deletes are not forwarded. If the
Book cannot be opened, the upserts remain locally unsynchronized. Start-over is never used for this
recovery.

Shelf reads preserve discriminated user/group ownership, private/listed visibility, item identity separately from Book identity, non-contiguous stored positions, matched Book items, and omitted versus empty previews. Normal item pages expose only visible Compact Books; the editor projection represents unavailable retained items without inventing Book metadata.

The Android SDK aims to expose each SPL subsystem's complete Reader-bearer capability even when the first-party Android UI adopts that capability incrementally. UI sequencing does not define or restrict the publishable SDK boundary.

Shelves now cover the complete current Reader-bearer boundary. Mutations are limited by the server to the caller's owned personal Shelves; Group and shared Shelves remain bearer-read-only. Create/add/delete operations have no client-generated idempotency key. Relative item movement is distinct from direct zero-based positioning because retained unavailable items can make direct positioning unavailable. Structured mutation failures identify fields and bounded rejection reasons without exposing server prose.

Library reads resolve an explicit SDK-owned scope (`Global` or `Group`) before applying shared axis semantics. Books browsing remains distinct from broad Library search: scoped browse `q` is title-only, while scoped broad search uses the wider metadata surface. Both return compact Books through an SDK-owned page model without exposing endpoint topology or raw next/previous URLs. Ordering is typed, page size is bounded to 1-200, and `SeriesIndex` retains the exact two-decimal wire representation.

Reader-safe Authors and Series use the same scoped list operation for Global and Group reads; their detail endpoints remain globally owned. They reuse the Library page envelope and public cover reference. Preview limits are bounded to 0-24; omitted preview books remain distinct from a returned empty preview list.

Catalog Tags are a shared scoped Library filter. Library and Group `/tags/` responses are unqualified tag universes for their exact scope. Contextual Catalog Tag aggregates belong to the complete result query that produced them, including axis, scope, search, selected tag, and other qualifiers; they are never globalized, merged across pages, or derived from page results. Their slug composes with Books browse/broad search and Authors/Series axis search, while the app retains tag identity and display data separately. Changing scope invalidates the available tag vocabulary rather than carrying a coincidentally equal slug across scopes.

## Offline product contract

Cross-client offline authority, continuation, future offline-close ordering, Catalog Tag cache identity,
publication identity, and account-cleanup behavior are recorded in
[Offline and cached product contract](offline-product-contract.md).

The important future close ordering is pending Reader changes first, close second. If the intended
Session is already closed server-side, the client resolves a writable continuation and applies the
normal forwarding policy rather than writing into history. Android does not currently queue Session
close, so this paragraph records settled product behavior rather than current app capability.

An EPUB is identified by its immutable file hash. A changed EPUB or different edition is a distinct
Book with a distinct CFI address space; metadata edits do not invalidate the publication asset.
Authenticated Book detail exposes the accepted byte stream's lowercase SHA-256 checksum, while the
download response has no checksum header. The SDK preserves this optional field. Android hashes the
downloaded file before promotion and rechecks retained EPUBs before reuse or offline admission.

`PublicBookCoverReference` wraps a server-provided absolute HTTP(S) URL. Covers are public assets, require no bearer credential, may use an external host, and are limited by contract to JPEG, PNG, or WebP. The SDK neither reconstructs cover paths nor fetches image bytes; a future image-loading layer may consume the public reference directly.

## Logout and deferred work

Reader logout revokes the exact bearer-owned client session with `DELETE /accounts/me/client-sessions/{client_session_id}/`. Confirmed revocation, or authentication rejection proving the bearer is already unusable, is followed by the standard local-account reset. Ambiguous remote failure retains local state for retry. Explicit Forget skips remote cooperation and invokes that same destructive local reset immediately.

The settled product contract additionally requires an online attempt to synchronize pending Reader
work before logout/forget cleanup. If pending offline-authored work cannot be synchronized, the user
must be warned that continuing will discard it. Android does not yet implement that preflight and
warning; the current immediate Forget behavior is a known gap, not cross-client precedent.

Also deferred: mDNS discovery, known-server presets, explicit offline Session close, general cache/download management, terminal failed-outbox repair UI, and broader offline Library administration.

## Reference SDK differences

The web SDK was used as behavioral evidence, not copied. Notable deliberate differences are:

- Android fixes `client_type` to `second-pass-android-client`; the reference defaults to `reader`.
- Android retains nullable/optional authenticated fields instead of coercing all missing values to empty strings or false.
- Android consumes the audited client-discovery `server_base_url` and authoritative absolute URLs.
- Android exposes a specific ambiguous-consumption error and durable handoff state; browser storage behavior is not reused.
- The copied reference advertises a distinct `/consume/` template, while the audited current server returns the same `/poll/` URL for poll and consume. Android trusts each concrete `poll_url` and `consume_url` from pairing creation, so neither assumption leaks into route construction.
- The reference has no client-session listing/revocation surface, so Android did not invent one without audited wire shapes.
- The reference shelf list exposes `include_preview_books` but not `preview_limit`. The current server contract supplies `preview_limit`; Android sends both parameters when a preview limit is requested.

## Verification

The focused checks are:

```powershell
.\gradlew.bat :spl-client:test
.\gradlew.bat :app:testDebugUnitTest
```

Repository-wide validation remains `test`, `detekt`, `ktlintCheck`, `lint`, `staticHygiene`, `check`, and `assembleDebug` through the committed wrapper.
