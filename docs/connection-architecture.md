# SPL connection architecture

This document defines Android connection ownership and failure semantics. Raw endpoints remain server-contract concerns.

## Ownership

The SPL server owns pairing semantics. `:spl-client` owns transport, schema validation, typed
protocol results, and following server-provided URLs. The Android Connection feature owns
create -> wait -> poll -> consume sequencing, verification, repair/logout/forget workflow,
account replacement, and authenticated publication. `ConnectionPersistence` owns durable commit,
interrupted-commit recovery, and clear across the profile, secure credential, and account descriptor
stores. `AccountLocalDataLifecycle` separately owns destructive cross-feature account-data cleanup.
UI owns presentation only; pairing does not publish a verified account by itself.

`:spl-client` owns URL normalization, public discovery, pairing request HTTP, status polling HTTP, consumption HTTP, bearer attachment, transport DTOs, response validation, and authenticated account/server-context mapping. It remains a pure Kotlin/JVM module. Ktor Client 3 uses its OkHttp engine, and kotlinx.serialization decodes internal wire models. Exact dependency versions come from `gradle/libs.versions.toml`. Neither transport type is exposed to `:app`.

`KtorSecondPassClient` is the public transport owner and implements `AutoCloseable`. Its public
constructor creates an internally owned Ktor/OkHttp client; callers close the root client when they
are finished. Authenticated clients and their capabilities share that root transport and do not
have independent disposal. Android binds one root client for the application process lifetime, so
features and ViewModels do not perform transport shutdown.

`:app` owns Android lifecycle sequencing, user-facing state, encrypted credential persistence, non-secret connection persistence, and display mapping. Compose receives no bearer credential and performs no network or persistence work.

## Discovery and URL normalization

`ServerOrigin` trims user input, adds HTTPS when a scheme is omitted, accepts only HTTP or HTTPS, rejects embedded credentials, and discards path/query/fragment data. Discovery always probes `/.well-known/secondpass` at that origin.

Public well-known discovery verifies SPL identity. Its public, non-secret `installationId` identifies the SPL installation and is distinct from the requested endpoint URL, which identifies its location. Client-API discovery then provides the authoritative API base, server base, login-request URL, token type, and endpoint templates. Pairing uses the concrete absolute URLs returned by the server; Android does not synthesize authorization, poll, or consume routes.

Public discovery and authenticated `/server/info/` are deliberately different models. The former establishes where and what the server is before trust; the latter describes account-visible server configuration after bearer verification.

Android may browse `_secondpass._tcp` over native DNS-SD while the server-entry surface is active. The only Second Pass-specific TXT attribute is `url`, whose value is an externally reachable Library URL. mDNS supplies location only: Android never reconstructs a URL from the service name, SRV host or port, `.local` hostname, or IP address. Each candidate still passes through the normal SDK well-known discovery, which supplies installation identity and presentation metadata. Validated installations appear as compact suggestions that only prefill the existing address field; manual entry remains available and the user must still run the normal confirmation and pairing flow.

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

The encrypted credential envelope temporarily includes a recovery copy of the non-secret profile. `ConnectionPersistence` coordinates this transaction journal: it writes the secure credential first, commits the Preferences DataStore profile second, then rewrites the envelope without the recovery profile. Restart recovery repairs a missing profile from the journal. Clear attempts the profile, credential, and account descriptor stores even when one removal fails, allowing a later retry to converge. DataStore separately owns the normal non-secret server/client-session connection record.

On startup, matching profile and credential state is verified through `/accounts/me/` and `/server/info/`. A rejected/revoked credential preserves the known account and enters the re-link flow; a transient server/network failure preserves both stores and exposes retry. An interrupted profile commit is repaired from the encrypted transaction journal.

After verification, Connection owns installation reachability separately from authentication. An authenticated transport failure from any SDK capability is a hint; Connection confirms it through the authenticated connection target before publishing offline availability. Android default-network changes only prompt a check or retry. HTTP errors, invalid protocol responses, and rejected credentials do not mean transport loss. An unreachable installation retains the verified account shell and local data; retry and network restoration use the same verification path to heal in place. Today the target uses the saved API endpoint. Its interface can later try known endpoints for the same installation without changing Home, Library, Reader, or sync availability models. URL is location, not installation identity; no endpoint-list schema is assumed here.

Connection also owns account-scoped, persistent **Work offline** intent. It withholds online
authority even if the Library is reachable and ignores automatic recovery hints while that choice
is active. Settings reconnect and Home refresh request the same installation-level check; an
explicit reconnect clears the choice, then verifies before restoring online authority. Reader
sync work remains pending without contacting the Library while the choice is active.

## Authenticated context

The SDK models sparse current-user fields and preserves absence for optional true-only flags. `reading_client_base_url` remains nullable. Successful token consumption alone never establishes linked state; both authenticated context requests must succeed.

The authenticated shell receives verified Library, account, group, and client-session context. Feature UI does not interpret pairing transport state as account authority.

## Authenticated transport

`AuthenticatedRequestExecutor` owns bearer attachment, same-origin checks, HTTP execution, common response-body extraction, shared JSON decoding, authentication classification, and transport/I/O normalization. Capability clients supply paths and request models, interpret endpoint-specific statuses, and map wire models to SDK models. Raw Ktor responses and credentials do not cross the SDK transport boundary, and errors do not include tokens or unbounded response bodies.

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

Marginalia reads preserve server ordering and historical Book context after visibility is lost; `canOpen` remains advisory. Active lookup is non-mutating, open/resume is the convergent lazy create-or-resume operation, and start-over is an explicit idempotent recovery action. The SDK keeps CFI values opaque. Retry, continuation, annotation identity, and closed-Session rules are canonical in [Offline and cached product contract](offline-product-contract.md).

Shelf reads preserve discriminated user/group ownership, private/listed visibility, item identity separately from Book identity, non-contiguous stored positions, matched Book items, and omitted versus empty previews. Normal item pages expose only visible Compact Books; the editor projection represents unavailable retained items without inventing Book metadata.

The SDK exposes Reader-bearer capabilities independently of Android UI sequencing.

Shelf mutations are limited by the server to the caller's owned personal Shelves; Group and shared Shelves remain bearer-read-only. Create/add/delete operations have no client-generated idempotency key. Relative item movement is distinct from direct zero-based positioning because retained unavailable items can make direct positioning unavailable. Structured mutation failures identify fields and bounded rejection reasons without exposing server prose.

Library reads resolve an explicit SDK-owned scope (`Global` or `Group`) before applying shared axis semantics. Books browsing remains distinct from broad Library search: scoped browse `q` is title-only, while scoped broad search uses the wider metadata surface. Both return compact Books through an SDK-owned page model without exposing endpoint topology or raw next/previous URLs. Ordering is typed, page size is bounded to 1-200, and `SeriesIndex` retains the exact two-decimal wire representation.

Reader-safe Authors and Series use the same scoped list operation for Global and Group reads; their detail endpoints remain globally owned. They reuse the Library page envelope and public cover reference. Preview limits are bounded to 0-24; omitted preview books remain distinct from a returned empty preview list.

Catalog Tags are a shared scoped Library filter. Library and Group `/tags/` responses are unqualified tag universes for their exact scope. Contextual Catalog Tag aggregates belong to the complete result query that produced them, including axis, scope, search, selected tag, and other qualifiers; they are never globalized, merged across pages, or derived from page results. Their slug composes with Books browse/broad search and Authors/Series axis search, while the app retains tag identity and display data separately. Changing scope invalidates the available tag vocabulary rather than carrying a coincidentally equal slug across scopes.

## Offline and publication contracts

Cross-client authority, continuation, offline-close ordering, Catalog Tag cache identity, publication identity, and account cleanup are defined in [Offline and cached product contract](offline-product-contract.md). Connection does not reinterpret those rules.

`PublicBookCoverReference` wraps a server-provided absolute HTTP(S) URL. Covers are public assets, require no bearer credential, may use an external host, and are limited by contract to JPEG, PNG, or WebP. The SDK neither reconstructs cover paths nor fetches image bytes; Coil consumes the reference in `:app`.

## Repair, logout, and forget

Android retains one active account context. A verified same-account Repair keeps that account's local
state; a verified replacement purges the previous account before publishing the replacement. There
is no dormant offline account store or account-switching lifecycle.

Logout attempts to revoke the exact bearer-owned client session with
`DELETE /accounts/me/client-sessions/{client_session_id}/`, then performs the standard local-account
reset regardless of remote availability. A not-found response is convergent remote success. Other
remote failures do not block local logout but are not described as confirmed remote revocation.
Explicit Forget skips remote cooperation and invokes the same destructive local reset immediately.
Connection expresses destructive intent through `AccountLocalDataLifecycle`; `app.storage` owns the
cross-feature purge. The purge cancels account sync work and removes Home projections, local Reader
state and outbox, continuation outcomes, downloaded EPUBs and checksum metadata, and per-account
Marginalia visibility. Device-global preferences remain intact.

## Verification

Run focused `:spl-client:test` and `:app:testDebugUnitTest` selections while changing connection behavior. The normal gate and live connection proof are documented in [Repository tooling](repository-tooling.md).
