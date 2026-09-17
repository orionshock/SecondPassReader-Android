# SPL connection architecture

This document records the first production client workflow built under the repository doctrine. Raw endpoint details remain server-contract concerns; this describes Android ownership and failure semantics.

## Ownership

The SPL server owns pairing semantics. `:spl-client` owns transport, schema validation, typed
protocol results, and following server-provided URLs. The Android Connection feature owns
create → wait → poll → consume sequencing, polling cadence, retry/backoff, and cancellation.
UI owns presentation only. App shell/storage owns workflow gating, the single active connection,
and secure persistence; pairing does not publish a verified account by itself.

`:spl-client` owns URL normalization, public discovery, pairing request HTTP, status polling HTTP, consumption HTTP, bearer attachment, transport DTOs, response validation, and authenticated account/server-context mapping. It remains a pure Kotlin/JVM module. Ktor Client 3 uses its OkHttp engine, and kotlinx.serialization decodes internal wire models. Exact dependency versions come from `gradle/libs.versions.toml`. Neither transport type is exposed to `:app`.

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

`ConnectionPairingController` owns the pending SDK request and polling job within Connection.
It waits at least the server-provided interval, runs one loop, and retries unreachable or throttled
checks with backoff capped at 60 seconds or the server minimum, whichever is higher. A successful
check resets the interval; foregrounding replaces the waiting loop rather than duplicating it.
Polling stops on cancellation, terminal status, invalid response, abandonment, or ViewModel clearance.
The request is retired before consumption, so foregrounding cannot start another consume attempt.
Cancellation is checked after asynchronous results before state publication or credential handoff.
`ConnectionCoordinator` receives issued credentials for persistence and separate verification.
Recomposition does not start work.

Waiting UI state contains only the Library/device names, approval code, authorization URL, expiry,
and display status. Poll/consume URLs, polling interval, SDK request identity, and retry state remain
inside the pairing owner. The SDK protocol models and HTTP operations are unchanged.

## Exactly-once consumption

An approved request is POSTed once. A successful token-bearing response creates an opaque `BearerCredential`; a later `consumed` response without a token is terminal and unrecoverable. Any transport failure, non-success response, or malformed success response during consumption is classified as ambiguous because the server may already have atomically consumed the credential. The pairing controller never retries that POST automatically.

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

Progress is Session metadata with last-write-wins whole-value replacement and no revision or timestamp conflict protection; clients must coalesce and serialize pending writes so an older request cannot arrive last. CFI and location labels remain opaque. Annotations form a complete authoritative child collection synchronized through explicit Bookmark/Highlight upserts and client-ID deletes. Upserts are last-write-wins for a Session-scoped client ID, and every successful synchronization response contains the complete authoritative collection. The server accepts an opaque nonblank 1-255-character value and does not enforce UUID syntax; Android conventionally generates random UUID v4 values. This client-owned identity is separate from the server annotation UUID and remains stable across retries. Android preserves it across Session continuation for never-confirmed annotations; a confirmed edit receives a deterministic replacement client ID. Delete is idempotent, including unknown and already-deleted client IDs; a later upsert restores the tombstoned identity. Synchronization is deliberately capped at the server's 100-operation atomic batch rather than implying cross-request atomicity through automatic chunking.

Structurally valid progress or annotation writes to a closed Session return HTTP `409` with `SESSION_CLOSED`; malformed request bodies fail validation first with `400`. HTTP `404` intentionally reveals only that the resource is unavailable to the credential and must not be interpreted as proof of deletion or nonexistence. Owned Sessions with lost Book visibility return `403 PERMISSION_DENIED`. The complete retry classification and remote-close behavior are recorded in the offline product contract.

After an annotation batch receives `SESSION_CLOSED`, Android retains unresolved upserts, performs
active-Session lookup for the Book, and calls normal `open` only when needed. Android accepts the
exact returned active Session. Never-confirmed annotations retain their client IDs; confirmed edits
become authored copies with deterministic replacement IDs derived from the source local Session,
continuation local Session, and original client ID. Those planned IDs remain stable across retries,
and historical originals remain unchanged. Deletes are not forwarded. If writable authority cannot
be resolved, the upserts remain locally unsynchronized. Start-over is never used for this recovery.

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

Android retains one active account context. A verified same-account Repair keeps that account's local
state; a verified replacement purges the previous account before publishing the replacement. There
is no dormant offline account store or account-switching lifecycle.

Reader logout attempts to revoke the exact bearer-owned client session with
`DELETE /accounts/me/client-sessions/{client_session_id}/`, then performs the standard local-account
reset regardless of remote availability. A not-found response is convergent remote success. Other
remote failures do not block local logout but are not described as confirmed remote revocation.
Explicit Forget skips remote cooperation and invokes the same destructive local reset immediately.
The reset cancels account sync work and removes cached projections, local Reader state and outbox,
continuation outcomes, and downloaded EPUBs with their checksum metadata. Device-level Reader
appearance remains intact.

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
