# Data model notes

This package reflects server-owned reading/library contracts while providing an app-friendly API.

## Library file invariant

Server convention: **1 book === 1 backing file**.

App/UI code should not treat "file availability" as an optional feature flag. If a book exists, open/download should be allowed; failures are handled as errors.

## Marginalia progress

- `cfi` is the meaningful restore anchor (persist + resume).
- `locationLabel` is opaque display metadata and maps to live API `location_label`.
- `updatedAt` is server-assigned.

## Annotations

- Live annotations use the Marginalia API projected types.
- App code should use `spl.marginalia.sessions.batchAnnotations()`.

Bookmarks contain only their identity, kind, location, and timestamps. They never contain a body. Highlights contain body text and optional quote context, color, and note fields.

### Server limits (reader-relevant)

- annotation batch: 1-100 operations
- `Idempotency-Key` header max length: 128 chars

Upserts, updates, restores, and retry-safe deletes use annotation batch operations keyed by `client_id`.
