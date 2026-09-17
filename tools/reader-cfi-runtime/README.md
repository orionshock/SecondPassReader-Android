# Reader CFI runtime

TypeScript is the authoritative source for the browser runtime injected by the Android Reader.
Android ships a readable development asset from `app/src/main/assets` and a minified,
same-path override from `app/src/release/assets`. Runtime builds do not require Node.

Requires Node 24 LTS and npm 11.

```text
npm ci
npm run typecheck
npm run build
npm run check
```

`protocol.json` owns only the Kotlin/JavaScript wire contract: the existing runtime version,
global, methods and positional arguments, result fields/discriminators, error codes, and shared
text limits. Calls remain positional JavaScript calls; there is no request envelope. Kotlin
provides named arguments and the bridge orders them using the generated method declaration.
The response remains `{ ok: true, value }` or `{ ok: false, error: { code } }`.
Evaluation/install deadlines and envelope-size enforcement are Kotlin bridge policy, not protocol
error codes. CFI algorithms remain in TypeScript; Readium lifecycle stays in the Android adapter.

`build` regenerates committed Kotlin/TypeScript declarations, checks the entrypoint signatures
and TypeScript result shapes, then produces the readable dev and minified release IIFE assets.
`check` rejects stale declarations, typechecks, compares both assets byte-for-byte, and checks
their installed method surface/version and error envelope in Node.

The root Gradle `readerCfiRuntimeCheck` and `releaseReaderCfiRuntimeCheck` run before Android
`preBuild`. They independently derive the expected declarations from the manifest, check
entrypoint names/argument order and embedded versions, and retain the source-digest checks.
They never invoke Node or generate runtime code. Edit the manifest and adapters together, then
run `npm run build` and `npm run check`; hand-edited or stale declarations fail Gradle.
`gradlew.bat -p buildSrc test` exercises the Node-free checker with deliberate protocol drift.

Protocol declarations stay inside the Readium CFI adapter/runtime; Reader domain and UI do not
consume them. The two small declaration renderers (Node regeneration and JVM verification) are
intentionally independent: disagreement fails the checks rather than changing shipped output.
