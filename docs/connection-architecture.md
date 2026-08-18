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

On startup, matching profile and credential state is verified through `/accounts/me/` and `/server/info/`. A rejected/revoked credential clears local connection state. A transient server/network failure preserves both stores and exposes retry. An interrupted profile commit is repaired from the encrypted transaction journal.

## Authenticated context

The SDK models sparse current-user fields and preserves absence for optional true-only flags. `reading_client_base_url` remains nullable. Successful token consumption alone never establishes linked state; both authenticated context requests must succeed.

The authenticated Home surface retains useful verified library, account, group, and client-session context inside the permanent application shell. It does not imply that future product Home capabilities have been implemented.

## Authenticated read surface

`KtorSecondPassClient` can create a credential-bound `AuthenticatedSecondPassClient`. The scope retains the validated API base and opaque bearer credential so feature callers do not repeatedly handle either value. It exposes recent reading, paginated shelf summaries with optional preview books, normal Books browsing, and broad Library search; it is not a screen-specific aggregate.

Recent-reading results preserve server order. Session status remains `active` or `closed`, and progress retains its CFI plus the server's opaque location label without deriving percentages. Shelf list inputs use a closed ordering vocabulary and can request a bounded number of preview books. Omitted `preview_books` remains distinguishable from a returned empty list, and missing item counts are rejected as invalid protocol rather than coerced to zero.

Books browsing and broad Library search are separate SDK operations because their server-side query semantics differ. Both return compact Books through an SDK-owned page model without exposing raw next/previous URLs. Ordering is typed, page size is bounded to the server's 1-200 contract, author/tag/series ordering is preserved, and `SeriesIndex` retains the exact two-decimal wire representation rather than converting it to a floating-point number.

`PublicBookCoverReference` wraps a server-provided absolute HTTP(S) URL. Covers are public assets, require no bearer credential, may use an external host, and are limited by contract to JPEG, PNG, or WebP. The SDK neither reconstructs cover paths nor fetches image bytes; a future image-loading layer may consume the public reference directly.

## Logout and deferred work

Remote session listing/revocation is not implemented because the audited summary does not define their response bodies and the reference SDK does not yet expose them. The UI therefore does not claim to log out. Error recovery offers an explicitly local “Forget locally” action where appropriate; it does not pretend to revoke the server session.

Also deferred: mDNS discovery, known-server presets, remaining Library capabilities and UI, general offline Library behavior, nested feature navigation, and all reader features.

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
