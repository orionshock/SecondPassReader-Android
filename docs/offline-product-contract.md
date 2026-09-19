# Offline and cached product contract

This document records cross-client product behavior for Android and the Web Reader. It describes
server authority and user-visible semantics; platform storage, background execution, and lifecycle
mechanisms may differ.

## Product boundary

Offline support exists for downloaded-publication reading, Reader progress, Reader-authored
marginalia, and personal reading continuity. It is not a general offline administration layer.
Offline behavior can follow Library unavailability or the account's explicit **Work offline** choice;
Android network availability alone never establishes Library reachability. The choice survives
restart and suppresses automatic recovery until the reader explicitly reconnects. Home refresh
may ask Connection to check the Library, but does not own that check. Home, Library, Reader, and
sync consume installation
availability; they do not know or select endpoint URLs. A Library may later have multiple known
locations without changing this product rule.
Shelf mutations, library metadata changes, group/ACL operations, and librarian actions remain
online-only unless a later product decision explicitly expands the boundary.

Cached server facts and pending client intent are distinct. The server remains authoritative for
Reading Session identity and status, the current active Session, closed-Session immutability,
authoritative annotation collections, and confirmed progress. Reconciliation refreshes that
authority before delivering pending Reader intent. A closed Session never reopens or receives a
new write.

“Make available offline” downloads and checksum-verifies the immutable EPUB without opening Reader
or creating a Reading Session. It retains any existing per-Book Sessions, progress, and annotations
through read-only server calls; an empty Session list remains empty. Offline availability means a
completed, checksum-valid account-local asset, not a cached flag. “Remove download” removes only
that asset, its retained cover, and its metadata, leaving Reader-authored state and pending sync
intact. A Book made available offline retains its EPUB, offline listing metadata, and cover image
when available. The cover is local downloaded content; Reader-authored data remains separately
owned. A cover-fetch failure does not discard an otherwise verified EPUB.

## Reader mutation contracts

- Annotation `client_id` is an opaque string of 1-255 characters; whitespace-only values are
  rejected, but the server does not validate UUID/GUID syntax. Database uniqueness is scoped to
  `(Session, client_id)`, so the same value may legally exist in different Sessions. Android should
  generate a random UUID v4 as a client convention for reliable uniqueness.
- `client_id` is distinct from the annotation's server-assigned database UUID. It is client-owned
  and remains stable across retries. Android preserves it when moving never-confirmed authored
  state; forwarding an edit of a confirmed annotation uses a deterministic replacement ID.
- Annotation upserts are last-write-wins for that Session-scoped `client_id`.
- Annotation deletion is idempotent. Deleting an existing annotation creates a tombstone; deleting
  an already-deleted or unknown `client_id` is a successful no-op and does not change an existing
  tombstone timestamp. A later upsert of the same `client_id` restores the annotation.
- A successful annotation synchronization response is the complete authoritative annotation
  collection, not a delta.
- Progress replacement is last-write-wins. The server provides no revision or timestamp conflict
  protection, so clients must coalesce and serialize their own pending progress to prevent an older
  request from arriving after a newer request.
- Canonical EPUB CFI remains opaque. Clients do not compare CFIs to infer reading order.

### Durable EPUB CFI profile

The server stores EPUB CFIs opaquely; clients own their generation and resolution. Second Pass
clients emit compact structural CFIs made from package/content paths and character offsets. Text
location assertions are outside the supported durable interoperability profile. Highlight quote
and repair context is carried separately as exact text, prefix, and suffix.

An imported EPUB's bytes are immutable for the lifetime of its Book identity, so mutation-repair
assertions are unnecessary for the canonical baseline. A materially changed EPUB is a different
Book and defines a different CFI address space.

### Write failures and retry

Progress replacement and annotation batch writes return HTTP `409 Conflict` with
`error.code = "SESSION_CLOSED"` for a structurally valid write to a closed Session. Request-body
validation runs first, so malformed input returns `400` even when the target Session is closed.
`SESSION_CLOSED` is permanent for that Session and means the rejected annotation batch committed
nothing: batches are atomic, so there is no partial result to disentangle. It requires authority
refresh and continuation, not a retry against the same Session.

The server does not emit an error code named `RESOURCE_NOT_FOUND`; that name is an Android
normalization of HTTP `404`. For Reader operations, `404` means only that the requested resource is
unavailable to the credential. It deliberately does not reveal whether the resource never existed,
was deleted, belongs to someone else, or is no longer visible. Clients must refresh identity and
authority rather than blindly retry or infer deletion.

| Operation | Deliberately ambiguous `404` meaning |
|---|---|
| Open Book, active Session lookup, or start-over | Book is absent or not currently visible |
| Get or close Session | Session is absent or not owned by the current user |
| Get or replace progress | Session is absent or not owned by the current user |
| Get or write annotations | Session is absent or not owned by the current user |
| Book detail or download | Book is absent or not currently visible |

Distinct authority failures are:

- `403 PERMISSION_DENIED` when the caller owns the Session but current Book access was lost;
- `409 SESSION_CLOSED` when the caller owns the Session but it is closed; and
- `409` or `503 BOOK_FILE_UNAVAILABLE` when a visible Book has no available EPUB.

Retry classification is:

| Result | Client behavior |
|---|---|
| `400` validation | Permanent for unchanged input; correct the payload |
| `401` | Reauthenticate or pair again; do not blindly retry |
| `403 PERMISSION_DENIED` | Retry only after authority or Book visibility changes |
| `404` | Refresh identity/authority first; do not blindly retry |
| `409 SESSION_CLOSED` | Never retry against that Session; reconcile continuation |
| Reused idempotency key with different input | Permanent for that key |
| Same idempotent request still processing | Retry later with the same key and identical request |
| `429` | Retry after the server-indicated delay |
| `5xx` or network interruption | Potentially retryable under the operation's idempotency rules |

Progress `PUT`, annotation batches, and identical Session-close requests are safe to repeat.
Start-over is safe to repeat only with the same idempotency key and identical request. The remaining
product question is whether a permanently rejected authored annotation should be retained for user
repair/export; the backend contract does not decide that UX policy.

Android background Reader delivery maps some permanent validation and permission failures to a
generic unavailable/pending result. This Android limitation does not change the cross-client retry
contract above.

## Session establishment and start-over

The active-Session lookup is a non-mutating authority check. Normal open is the convergent lazy
create-or-resume operation: it returns the current active Session when one exists and creates one
otherwise. A client must accept the exact server-returned Session identity.
Android first looks up the active Session and calls normal open only when none exists, accepting
the server-returned identity in either case. This lookup-then-open convergence is deliberate.

Start-over is a separate, explicitly user-invoked recovery action. It must never be substituted for
normal open or invoked automatically during reconnect. Start-over is the lifecycle operation with
an idempotency key. The key is scoped per authenticated user across all of that user's idempotent
requests and is bound to the HTTP method, complete path, and normalized validated body. An identical
request with the same key returns the stored success; changing the Book, path, method, or body with
that key returns `409 INVALID_REQUEST`. Different users may reuse the same value independently.

The replay window is exactly 24 hours. A still-processing request returns `409 INVALID_REQUEST` and
must be retried later with the same key and identical input. Failed transactions do not retain a
completed idempotency result. Physical database-row deletion is not guaranteed at the exact expiry
instant; expiry controls whether the key may be reused.

## Remote closure and continuation

If Android staged Reader work against Session A and discovers that A closed elsewhere, it keeps A
as immutable history and resolves a writable continuation Session. Its established forwarding
rules are:

- forward the latest pending progress;
- move annotations that were created locally and never server-confirmed;
- duplicate an offline edit of a server-confirmed annotation into the writable Session while
  using a deterministic replacement `client_id` derived from the source local Session ID,
  continuation local Session ID, and original client ID; the historical original remains unchanged
  and retries reuse the replacement identity; and
- drop an offline delete of a server-confirmed annotation, preserving the historical annotation and
  recording the outcome for a non-blocking user notice.

Repeated reconciliation must reuse the same continuation intent and transformations.

### Annotation recovery after `SESSION_CLOSED`

For an annotation batch rejected with `409 SESSION_CLOSED`, Android must:

1. retain every locally authored, unacknowledged annotation upsert;
2. resolve writable authority for the Book through active lookup, then normal `open` if needed,
   not once per annotation;
3. accept the exact active Session returned by that resolution;
4. replay the unresolved upserts as one bounded annotation batch against that Session; and
5. retain the planned client IDs throughout retries: original IDs for never-confirmed annotations,
   deterministic replacement IDs for edits of confirmed annotations.

Deletes from the closed Session are not replayed into the new Session because they identify
historical annotations in the old Session. The closed originals remain unchanged. Normal `open`,
not start-over, owns recovery: start-over could close an unrelated active Session.

If `open` fails because the Book is unavailable or no longer visible, unresolved annotation
upserts remain durable and unsynchronized. They are not discarded. Suitable user-facing copy is:

> Not synced. The reading session was closed, and a new session could not be opened.

Progress recovery remains a separate desired-state operation even when it ultimately uses the same
writable Session.

The server permits the same client ID in different Sessions. Android nevertheless gives a forwarded
edit a deterministic replacement ID so retry remains idempotent and the confirmed historical
annotation remains unchanged. Continuation uses lookup-then-open convergence and never substitutes
start-over.

### Writes racing with close

The server contract serializes a write racing with Session close:

- if the write commits first, it succeeds and close observes that state;
- if close commits first, the write is rejected with `409 SESSION_CLOSED`;
- no progress or annotation write may commit after the Session is closed; and
- an annotation batch is atomic and rolls back completely on failure.

The backend enforces this with transactional active-state checks, including an active-Session
compare-and-set before annotation changes.

## Offline Session close

Offline Session close is a settled product contract but is not implemented by Android yet. When it
is implemented, reconnect ordering is:

```text
refresh Session authority
-> synchronize pending progress and annotations
-> close the writable Session
```

If the intended Session is already closed server-side, the client first resolves a new writable
Session and applies the normal continuation rules there, then synchronizes the forwarded work. It
must not write into the closed Session. In particular, a delete targeting a server-confirmed
annotation in the closed Session remains a dropped delete; it is not converted into a delete in the
continuation. The original close intent is already satisfied by the source Session being closed and
is not transferred to the continuation Session; the continuation remains the normal current
writable Session.

Users may organize unwanted Sessions later through server-side Session deletion and selective
export. Clients should not guess which historical Sessions the user considers redundant.

## Catalog Tag contexts

Scope-level `/tags/` responses and contextual Catalog Tag aggregates answer different questions:

- Library `/tags/` is the unqualified tag universe for the Library scope.
- Group `/tags/` is the unqualified tag universe for that Group scope.
- Contextual `catalogTags` belongs to the exact result query that produced it, including result axis,
  Library or Group scope, search query, selected tag, and other request qualifiers.

A contextual aggregate must be cached and replaced atomically with that exact result context. It
must not be stored as a global tag-count list, carried across context changes, merged across pages,
or recomputed from loaded page items. Page responses may repeat the same full-population aggregate;
the latest response for the same context replaces the prior value.

## Publication identity and caching

An EPUB asset is identified by its immutable file hash. A materially different EPUB or edition is a
different Book because it defines a different CFI address space; Books are never merged across
editions. Metadata edits do not invalidate an EPUB. A changed file hash would violate the normal
immutability invariant and is the only meaningful asset-identity change.

Authenticated Book detail exposes the exact stored EPUB checksum as lowercase SHA-256 hexadecimal.
The checksum covers the accepted byte stream; clients can hash downloaded bytes and compare them. The
download response has no `Digest` or checksum header, so validation uses the Book-detail field. A
blank checksum means that the file cannot be validated for offline admission. Marginalia
archives use the distinct textual form `sha256:<hex>` and must not be compared without removing that
prefix deliberately. The Android SDK preserves the Book-detail checksum. Android hashes downloaded
and retained EPUB bytes before treating them as complete or available offline.

The approximately two-minute catalog cache is server-internal Django caching of the set of Book IDs
visible to a user for ordinary browse/list projections. It does not authorize Android, Web, browser,
or intermediary response caching and does not imply `Cache-Control: max-age=120`. Authorization-
sensitive Book detail, mutation, and EPUB download recalculate visibility; the live download route
is explicitly uncached.

## Logout, revocation, and forgetting an account

The product rule is:

- when online, attempt to synchronize pending Reader work before clearing the account;
- when offline or synchronization cannot complete, warn explicitly that continuing will discard
  pending offline-authored work; and
- clear only the exact account scope after confirmation.

Android does not provide this pending-work preflight and warning. This platform limitation does not
change the product rule. Credentials remain separate from content storage, and credential refresh
for the same stable account identity must not purge local data.
