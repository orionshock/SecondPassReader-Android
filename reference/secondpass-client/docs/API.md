# API

`@secondpass/client` exposes an initialized, workflow-shaped client:

```ts
import { createSecondPassClient } from "@secondpass/client";

const spl = createSecondPassClient({ apiBaseUrl, accessToken, tokenType });
```

## Auth model

- Server discovery and linking methods are allowed without an access token.
- `spl.server.info()` and all non-server namespaces require an access token and throw `ApiError(kind="unauthorized")` if missing/invalid.

## server

- `spl.server.discover(serverBaseUrl)`
- `spl.server.info()`
- `spl.server.createLoginRequest(discovery, input?)`
- `spl.server.pollLoginRequest(pollUrl)`
- `spl.server.consumeLoginRequest(consumeUrl)`

## account

- `spl.account.getCurrentUser()`

## library

- `spl.library.search(params?)`
  - Uses `/api/v1/library/search` with no trailing slash.
  - Supports broad `q`, ordering, exclusions, and pagination.

### library.books

- `spl.library.books.list(params?)`
- `spl.library.books.get(bookId)`
- `spl.library.books.download(bookIdOrBook)`
  - Downloads the backing file bytes as a `Blob` (format-neutral).
  - Hides the server `download_url` field from normal app flows.
- `spl.library.books.getDownloadUrl(bookIdOrBook)`
  - Returns a server-provided URL for deliberate URL workflows only.

### library.series

- `spl.library.series.list(params?)`
- `spl.library.series.get(seriesId, { includePreviewBooks?, previewLimit? }?)`

### library.authors

- `spl.library.authors.list(params?)`
- `spl.library.authors.get(authorId, { includePreviewBooks?, previewLimit? }?)`

Author- and series-filtered books use `spl.library.books.list({ author })` and
`spl.library.books.list({ series })` directly.

### library.tags

- `spl.library.tags.list(params?)`
- `spl.library.tags.get(tagId)`
- Tags do not accept preview parameters.

### library.groups

- `spl.library.groups.list(params?)`
- `spl.library.groups.get(groupId, { includePreviewBooks?, previewLimit? }?)`
- `spl.library.groups.books(groupId, params?)`
- `spl.library.groups.authors(groupId, params?)`
- `spl.library.groups.series(groupId, params?)`
- `spl.library.groups.tags(groupId, params?)`

## shelves

- `spl.shelves.list(params?)`
- `spl.shelves.get(shelfId)`
- `spl.shelves.items(shelfId, params?)`

## marginalia

### Books and sessions

- `spl.marginalia.books.list(params?)`
- `spl.marginalia.books.get(bookId)`
- `spl.marginalia.books.sessions(bookId, params?)`
- `spl.marginalia.books.open(bookId, { name?, notes? }?)`
- `spl.marginalia.books.getActiveSession(bookId)`
- `spl.marginalia.books.startOver(bookId, finalState, { idempotencyKey })`
- `spl.marginalia.sessions.list(params?)`
- `spl.marginalia.sessions.recent(params?)`
- `spl.marginalia.sessions.get(sessionId)`
- `spl.marginalia.sessions.update(sessionId, { name?, notes? })`
- `spl.marginalia.sessions.close(sessionId, finalState?)`

`startOver` requires a 1-128 character `idempotencyKey`. The client sends it as `Idempotency-Key`.

### Progress and annotations

- `spl.marginalia.sessions.getProgress(sessionId)`
- `spl.marginalia.sessions.replaceProgress(sessionId, { cfi, locationLabel? })`
- `spl.marginalia.sessions.getAnnotations(sessionId)`
- `spl.marginalia.sessions.batchAnnotations(sessionId, operations)`

Progress replacement uses `PUT`; `locationLabel` maps to `location_label`. Annotation batches use `clientId`/`client_id` for retry-safe upserts and deletes. Bookmark upserts have no body. Highlight upserts require body text.

Bearer clients do not expose archive import/export methods.

## Errors

The client throws normal JavaScript errors. For server failures, the package exports:

- `ApiError`
- `ApiErrorKind`

Callers should catch errors and render user-friendly messages. Never log or persist bearer tokens.

## Server limits (reader-relevant)

- annotation batch: 1-100 operations
- `location_label`: opaque display metadata; the client does not parse or normalize returned values
- `Idempotency-Key` header max length: 128 chars
